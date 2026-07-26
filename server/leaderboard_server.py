#!/usr/bin/env python3
"""Tomrun 纪录榜参考服务端（stdlib only，无第三方依赖）。

启动:
  python3 server/leaderboard_server.py
  python3 server/leaderboard_server.py --port 8787 --db leaderboard.db

API:
  GET  /api/v1/leaderboard/{slug}?limit=10
  POST /api/v1/leaderboard/{slug}
       {"player","value","whenMs","detail","deviceId","gameVersion"}
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import time
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
        conn.commit()
    finally:
        conn.close()


class LeaderboardHandler(BaseHTTPRequestHandler):
    db_path: Path = Path("leaderboard.db")

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
        limit = min(int(qs.get("limit", ["10"])[0]), 50)
        entries = self._top(slug, limit)
        self._json(200, {"entries": entries, "category": slug})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        prefix = "/api/v1/leaderboard/"
        if not parsed.path.startswith(prefix):
            self._json(404, {"error": "not found"})
            return
        slug = parsed.path[len(prefix) :].strip("/")
        if slug not in CATEGORIES:
            self._json(400, {"error": "unknown category"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8")
            body = json.loads(raw) if raw else {}
        except (ValueError, json.JSONDecodeError):
            self._json(400, {"error": "invalid json"})
            return

        player = str(body.get("player", "")).strip()[:32]
        value = int(body.get("value", 0))
        when_ms = int(body.get("whenMs", int(time.time() * 1000)))
        detail = str(body.get("detail", "")).strip()[:120]
        device_id = str(body.get("deviceId", "")).strip()[:64]
        game_version = str(body.get("gameVersion", "")).strip()[:16]
        if not player or value <= 0:
            self._json(400, {"error": "player and positive value required"})
            return

        rank = self._insert(
            slug, player, value, when_ms, detail, device_id, game_version
        )
        self._json(200, {"accepted": True, "rank": rank, "category": slug})

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
                SELECT player, value, when_ms, detail
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

    server = ThreadingHTTPServer((args.host, args.port), LeaderboardHandler)
    print("Tomrun leaderboard API on http://%s:%d" % (args.host, args.port))
    print("Categories:", ", ".join(CATEGORIES.keys()))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutdown")


if __name__ == "__main__":
    main()
