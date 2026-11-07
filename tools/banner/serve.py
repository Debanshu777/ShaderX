#!/usr/bin/env python3
"""Preview the banner and save its browser-rendered PNG to the repository."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUTPUT = HERE.parents[1] / "docs/assets/shaderx-banner.png"
ORIGIN = "http://127.0.0.1:8876"


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(HERE), **kwargs)

    def do_POST(self):
        if self.path != "/export" or self.headers.get("Origin") != ORIGIN:
            self.send_error(403)
            return
        size = int(self.headers.get("Content-Length", "0"))
        if not 0 < size < 5_000_000 or self.headers.get("Content-Type") != "image/png":
            self.send_error(400)
            return
        data = self.rfile.read(size)
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            self.send_error(400)
            return
        OUTPUT.write_bytes(data)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"Saved docs/assets/shaderx-banner.png")


if __name__ == "__main__":
    print(f"Open {ORIGIN}; Ctrl+C stops the local renderer.", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 8876), Handler).serve_forever()
