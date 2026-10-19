#!/usr/bin/env python3
"""Tomrun 纪录榜参考服务端（stdlib only，无第三方依赖）。

启动:
  python3 server/leaderboard_server.py
  python3 server/leaderboard_server.py --port 8787 --db leaderboard.db

API:
  GET    /api/v1/leaderboard/{slug}?limit=10
  POST   /api/v1/leaderboard/{slug}
         {"player","value","whenMs","detail","deviceId","gameVersion"}
  DELETE /api/v1/records            {"deviceId"}  删除这台设备上传过的全部纪录（隐私）
  DELETE /api/v1/leaderboard/{slug}?id=N   管理员下架一条（Authorization: Bearer $TOMRUN_ADMIN_TOKEN）

面向中小学：角色名过滤脏话、网址和长串数字（手机号/QQ），命中就换成默认名照样收；
数值超出游戏能达到的上限直接拒（422），客户端遇到 4xx 会丢掉这条不再重试。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import threading
import time
import unicodedata
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

CATEGORIES = {
    "run_distance": "单场距离",
    "run_score": "单场得分",
    "museum_collect": "藏品图鉴",
    "honor_count": "荣誉获得",
}

# 游戏里能达到的上限（藏品 88 件、荣誉 22×4 档；距离按最高速跑一小时留余量）
VALUE_MAX = {
    "run_distance": 100_000,
    "run_score": 2_000_000,
    "museum_collect": 88,
    "honor_count": 88,
}

DEFAULT_PLAYER = "小猫"
PLAYER_MAX_LEN = 8  # 与客户端 CHARACTER_NAME_MAX_LENGTH 一致

# 内置一份最常见的；部署时可在 db 同目录放 blocked_words.txt（一行一个）扩充
BUILTIN_BLOCKED = [
    "傻逼", "煞笔", "沙比", "操你", "草你", "艹你", "日你", "妈的", "他妈", "你妈",
    "尼玛", "去死", "滚蛋", "贱人", "婊子", "畜生", "脑残", "智障", "废物", "垃圾",
    "fuck", "shit", "bitch", "dick", "porn", "色情", "约炮", "赌博", "博彩", "代练",
    "加微", "qq群", "习近平", "共产党", "法轮",
]
_URLISH = re.compile(r"(https?|www\.|\.com|\.cn|\.net|\.top|@)", re.I)
_LONG_DIGITS = re.compile(r"\d{5,}")

# 每个 IP / 设备在窗口内最多上报几条（正常一局最多 4 条）
RATE_WINDOW_S = 600
RATE_MAX_POST = 40


def load_blocked(db_path: Path) -> list[str]:
    words = list(BUILTIN_BLOCKED)
    extra = db_path.with_name("blocked_words.txt")
    if extra.exists():
        for line in extra.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                words.append(line)
    return [w.lower() for w in words]


def clean_player(raw: str, blocked: list[str]) -> str:
    name = "".join(
        ch for ch in unicodedata.normalize("NFKC", raw)
        if unicodedata.category(ch)[0] not in ("C", "Z") or ch == " "
    ).strip()[:PLAYER_MAX_LEN]
    if not name:
        return DEFAULT_PLAYER
    if _URLISH.search(name) or _LONG_DIGITS.search(name) or has_blocked(name, blocked):
        return DEFAULT_PLAYER
    return name


def has_blocked(text: str, blocked: list[str]) -> bool:
    low = unicodedata.normalize("NFKC", text).lower()
    squashed = re.sub(r"[\s\W_]+", "", low)
    return any(w in squashed or w in low for w in blocked)


class RateLimiter:
    def __init__(self, window_s: int, max_hits: int) -> None:
        self.window_s = window_s
        self.max_hits = max_hits
        self.hits: dict[str, list[float]] = {}
        self.lock = threading.Lock()

    def allow(self, *keys: str) -> bool:
        now = time.time()
        with self.lock:
            if len(self.hits) > 50_000:
                self.hits.clear()
            ok = True
            for k in keys:
                if not k:
                    continue
                q = [t for t in self.hits.get(k, []) if now - t < self.window_s]
                self.hits[k] = q
                if len(q) >= self.max_hits:
                    ok = False
            if ok:
                for k in keys:
                    if k:
                        self.hits[k].append(now)
            return ok


def init_db(path: Path) -> None:
    conn = sqlite3.connect(path)
    try:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                player TEXT NOT NULL,
                value INTEGER NOT NULL,
                when_ms INTEGER NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                device_id TEXT NOT NULL DEFAULT '',
                game_version TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """
        )
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_entries_cat ON entries(category, value DESC, when_ms DESC)"
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS crashes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                version TEXT NOT NULL DEFAULT '',
                version_code INTEGER NOT NULL DEFAULT 0,
                sdk INTEGER NOT NULL DEFAULT 0,
                device TEXT NOT NULL DEFAULT '',
                trace TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """
        )
        conn.commit()
    finally:
        conn.close()


class LeaderboardHandler(BaseHTTPRequestHandler):
    db_path: Path = Path("leaderboard.db")
    blocked: list[str] = list(BUILTIN_BLOCKED)
    limiter = RateLimiter(RATE_WINDOW_S, RATE_MAX_POST)
    admin_token: str = ""

    def _client_ip(self) -> str:
        return self.headers.get("X-Real-IP") or self.client_address[0]

    def _read_json(self) -> dict[str, Any] | None:
        try:
            length = min(int(self.headers.get("Content-Length", "0")), 64 * 1024)
            raw = self.rfile.read(length).decode("utf-8")
            body = json.loads(raw) if raw else {}
            return body if isinstance(body, dict) else None
        except (ValueError, UnicodeDecodeError):
            return None

    def log_message(self, fmt: str, *args: Any) -> None:
        print("[%s] %s" % (self.log_date_time_string(), fmt % args))

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        prefix = "/api/v1/leaderboard/"
        if not parsed.path.startswith(prefix):
            self._json(404, {"error": "not found"})
            return
        slug = parsed.path[len(prefix) :].strip("/")
        if slug not in CATEGORIES:
            self._json(400, {"error": "unknown category"})
            return
        qs = parse_qs(parsed.query)
        try:
            limit = max(1, min(int(qs.get("limit", ["10"])[0]), 50))
        except ValueError:
            limit = 10
        entries = self._top(slug, limit)
        self._json(200, {"entries": entries, "category": slug})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") == "/api/v1/crash":
            self._post_crash()
            return
        prefix = "/api/v1/leaderboard/"
        if not parsed.path.startswith(prefix):
            self._json(404, {"error": "not found"})
            return
        slug = parsed.path[len(prefix) :].strip("/")
        if slug not in CATEGORIES:
            self._json(400, {"error": "unknown category"})
            return
        body = self._read_json()
        if body is None:
            self._json(400, {"error": "invalid json"})
            return

        device_id = str(body.get("deviceId", "")).strip()[:64]
        if not self.limiter.allow("ip:" + self._client_ip(), "dev:" + device_id):
            self._json(429, {"error": "too many submissions"})
            return
        try:
            value = int(body.get("value", 0))
            now_ms = int(time.time() * 1000)
            when_ms = int(body.get("whenMs", now_ms))
        except (TypeError, ValueError):
            self._json(400, {"error": "value must be integer"})
            return
        if value <= 0 or value > VALUE_MAX[slug]:
            self._json(422, {"error": "value out of range"})
            return
        # 未来时间按现在算，免得改系统时间就能永远压在同分纪录前面
        when_ms = min(when_ms, now_ms)
        player = clean_player(str(body.get("player", "")), self.blocked)
        detail = str(body.get("detail", "")).strip()[:120]
        if _URLISH.search(detail) or has_blocked(detail, self.blocked):
            detail = ""
        game_version = str(body.get("gameVersion", "")).strip()[:16]

        rank = self._insert(
            slug, player, value, when_ms, detail, device_id, game_version
        )
        self._json(200, {"accepted": True, "rank": rank, "category": slug})

    def _post_crash(self) -> None:
        if not self.limiter.allow("crash:" + self._client_ip()):
            self._json(429, {"error": "too many requests"})
            return
        body = self._read_json()
        trace = str((body or {}).get("trace", "")).strip()[:12000]
        if not trace:
            self._json(400, {"error": "trace required"})
            return
        try:
            version_code = int(body.get("versionCode", 0))
            sdk = int(body.get("sdk", 0))
        except (TypeError, ValueError):
            version_code, sdk = 0, 0
        self._exec(
            "INSERT INTO crashes (version, version_code, sdk, device, trace, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            (
                str(body.get("version", ""))[:16], version_code, sdk,
                str(body.get("device", ""))[:64], trace, int(time.time() * 1000),
            ),
        )
        self._json(200, {"accepted": True})

    def do_DELETE(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") == "/api/v1/records":
            body = self._read_json() or {}
            device_id = str(body.get("deviceId", "")).strip()[:64]
            if len(device_id) < 16:
                self._json(400, {"error": "deviceId required"})
                return
            if not self.limiter.allow("del:" + self._client_ip()):
                self._json(429, {"error": "too many requests"})
                return
            n = self._exec("DELETE FROM entries WHERE device_id = ?", (device_id,))
            self._json(200, {"deleted": n})
            return
        prefix = "/api/v1/leaderboard/"
        if parsed.path.startswith(prefix):
            auth = self.headers.get("Authorization", "")
            if not self.admin_token or auth != "Bearer " + self.admin_token:
                self._json(403, {"error": "forbidden"})
                return
            slug = parsed.path[len(prefix):].strip("/")
            try:
                entry_id = int(parse_qs(parsed.query).get("id", [""])[0])
            except ValueError:
                self._json(400, {"error": "id required"})
                return
            n = self._exec("DELETE FROM entries WHERE id = ? AND category = ?", (entry_id, slug))
            self._json(200, {"deleted": n})
            return
        self._json(404, {"error": "not found"})

    def _exec(self, sql: str, params: tuple[Any, ...]) -> int:
        conn = sqlite3.connect(self.db_path)
        try:
            cur = conn.execute(sql, params)
            conn.commit()
            return cur.rowcount
        finally:
            conn.close()

    def _insert(
        self,
        category: str,
        player: str,
        value: int,
        when_ms: int,
        detail: str,
        device_id: str,
        game_version: str,
    ) -> int:
        now = int(time.time() * 1000)
        conn = sqlite3.connect(self.db_path)
        try:
            conn.execute(
                """
                INSERT INTO entries
                (category, player, value, when_ms, detail, device_id, game_version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (category, player, value, when_ms, detail, device_id, game_version, now),
            )
            conn.commit()
            cur = conn.execute(
                """
                SELECT COUNT(*) + 1 FROM entries
                WHERE category = ? AND (
                    value > ? OR (value = ? AND when_ms > ?)
                )
                """,
                (category, value, value, when_ms),
            )
            return int(cur.fetchone()[0])
        finally:
            conn.close()

    def _top(self, category: str, limit: int) -> list[dict[str, Any]]:
        conn = sqlite3.connect(self.db_path)
        try:
            cur = conn.execute(
                """
                SELECT player, value, when_ms, detail, id
                FROM entries
                WHERE category = ?
                ORDER BY value DESC, when_ms DESC
                LIMIT ?
                """,
                (category, limit),
            )
            return [
                {
                    "player": row[0],
                    "value": row[1],
                    "whenMs": row[2],
                    "detail": row[3] or "",
                    "id": row[4],
                }
                for row in cur.fetchall()
            ]
        finally:
            conn.close()

    def _json(self, code: int, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    parser = argparse.ArgumentParser(description="Tomrun leaderboard API server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--db", default="leaderboard.db")
    args = parser.parse_args()

    db_path = Path(args.db)
    init_db(db_path)
    LeaderboardHandler.db_path = db_path
    LeaderboardHandler.blocked = load_blocked(db_path)
    LeaderboardHandler.admin_token = os.environ.get("TOMRUN_ADMIN_TOKEN", "")

    server = ThreadingHTTPServer((args.host, args.port), LeaderboardHandler)
    print("Tomrun leaderboard API on http://%s:%d" % (args.host, args.port))
    print("Categories:", ", ".join(CATEGORIES.keys()))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutdown")


if __name__ == "__main__":
    main()
