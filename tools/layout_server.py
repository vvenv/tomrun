#!/usr/bin/env python3
"""家页面布局编辑器的本地读写服务。

启动后浏览器打开 http://localhost:8770/ 即是编辑器；
编辑器开机自动读取、点「保存」直接写回：
    app/src/main/assets/home_layout.json
无需手动导入导出。仅监听本机回环地址。

用法：  python3 tools/layout_server.py   （或 ./tools/layout_server.py）
"""
import http.server
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent          # tools/
REPO = ROOT.parent
EDITOR = ROOT / "yard-editor.html"
LAYOUT = REPO / "app" / "src" / "main" / "assets" / "home_layout.json"
PORT = 8770


class Handler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self._send(200, EDITOR.read_bytes(), "text/html; charset=utf-8")
        elif self.path == "/api/layout":
            txt = LAYOUT.read_text("utf-8") if LAYOUT.exists() else "{}"
            self._send(200, txt)
        else:
            self._send(404, '{"error":"not found"}')

    def do_PUT(self):
        self._save()

    def do_POST(self):
        self._save()

    def _save(self):
        n = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(n)
        try:
            obj = json.loads(raw)                       # 先校验再落盘
        except Exception as e:
            self._send(400, json.dumps({"error": f"invalid json: {e}"}))
            return
        LAYOUT.parent.mkdir(parents=True, exist_ok=True)
        LAYOUT.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", "utf-8")
        print(f"  ✔ 已写入 {LAYOUT}")
        self._send(200, json.dumps({"ok": True}))

    def log_message(self, *a):
        pass                                            # 静默访问日志


if __name__ == "__main__":
    print(f"家页面布局编辑器  →  http://localhost:{PORT}/")
    print(f"读写目标：{LAYOUT}")
    try:
        http.server.HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\n已停止。")
