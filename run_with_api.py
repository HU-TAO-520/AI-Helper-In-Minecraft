# -*- coding: utf-8 -*-
"""带本地接口的启动器（新增文件，不改动本项目其它文件）。

作用：用本文件启动本项目（等于 python main.py），并在**同一个进程内**挂钩
BotEngine，开一个本机 HTTP 接口，让网站可以借用**本项目正在运行的机器人**发消息。

用法（代替 `python main.py`）：
    python run_with_api.py
    python run_with_api.py --port 8766 --token 你的口令

接口（默认 127.0.0.1:8766）：
    GET  /status                          -> {"ok":true,"running":true/false}
    POST /say  {"text":"..."}             -> 机器人发送聊天
    POST /pay  {"game_id":"xx","amount":1}-> 机器人发送 /pay xx 1
鉴权：设置了 --token 时，请求头需带 X-Token: <token>。
"""
import argparse
import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE_DIR)

from aafm_py import bot_engine as _be  # noqa: E402

# --- 挂钩 BotEngine，记录正在运行的实例 ---
_ENGINES = []
_orig_start = _be.BotEngine.start


def _patched_start(self, runtime_config):
    _orig_start(self, runtime_config)
    _ENGINES.append(self)


_be.BotEngine.start = _patched_start


def _engine():
    for e in reversed(_ENGINES):
        if e.running:
            return e
    return _ENGINES[-1] if _ENGINES else None


TOKEN = ""


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _auth(self):
        return not TOKEN or self.headers.get("X-Token") == TOKEN

    def _read(self):
        try:
            n = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            n = 0
        raw = self.rfile.read(n) if n else b"{}"
        try:
            return json.loads(raw.decode("utf-8") or "{}")
        except ValueError:
            return {}

    def do_GET(self):
        if self.path.startswith("/status"):
            e = _engine()
            self._json(200, {"ok": True, "running": bool(e and e.running)})
        else:
            self._json(404, {"ok": False, "msg": "not found"})

    def do_POST(self):
        if not self._auth():
            self._json(403, {"ok": False, "msg": "token 无效"})
            return
        data = self._read()
        e = _engine()
        if e is None or not e.running:
            self._json(503, {"ok": False, "msg": "机器人未运行（请先在界面启动机器人）"})
            return
        if self.path.startswith("/say"):
            text = str(data.get("text", "")).strip()
            ok = bool(text) and e.say(text)
            self._json(200 if ok else 400, {"ok": ok, "msg": "" if ok else "参数无效或发送失败"})
        elif self.path.startswith("/pay"):
            game_id = str(data.get("game_id", "")).strip()
            try:
                amount = int(data.get("amount", 0))
            except (TypeError, ValueError):
                amount = 0
            if not game_id or amount <= 0:
                self._json(400, {"ok": False, "msg": "game_id / amount 无效"})
                return
            ok = e.say("/pay %s %d" % (game_id, amount))
            self._json(200 if ok else 503, {"ok": ok, "msg": "" if ok else "发送失败"})
        else:
            self._json(404, {"ok": False, "msg": "not found"})

    def log_message(self, *args):
        pass


def main():
    global TOKEN
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--token", default="")
    args = parser.parse_args()
    TOKEN = args.token

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print("本地接口已启动：http://%s:%d  (token=%s)" % (args.host, args.port, TOKEN or "无"))
    print("本接口复用本项目正在运行的机器人。正在打开界面……")

    from aafm_py.gui import main as gui_main
    gui_main()


if __name__ == "__main__":
    main()
