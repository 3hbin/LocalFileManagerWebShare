"""Kiểm thử client với một máy chủ giả đúng theo hợp đồng API của LocalWebServer."""
import email.parser
import email.policy
import http.server
import json
import os
import secrets
import tempfile
import threading
import unittest
import urllib.parse

from lfm_core import AuthError, LfmClient, LfmError, human_size, join_rel, parent_rel

PASSWORD = "123456"
TOKEN = secrets.token_hex(8)


class FakeShare(http.server.BaseHTTPRequestHandler):
    root = None  # gán khi khởi động
    password = PASSWORD  # thuộc tính lớp để kiểm thử đổi được

    def log_message(self, *args):  # im lặng
        pass

    def _authed(self):
        return f"lfm={TOKEN}" in self.headers.get("Cookie", "")

    def _send(self, code, body=b"", ctype="text/plain", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _login_page(self):
        self._send(200, b"<html>login</html>", "text/html")

    def do_POST(self):
        u = urllib.parse.urlsplit(self.path)
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        if u.path == "/login":
            if not self.password:  # máy chủ không đặt mật khẩu thì không có trang đăng nhập
                return self._send(404, b"not found")
            form = urllib.parse.parse_qs(body.decode())
            if form.get("password", [""])[0] == self.password:
                return self._send(200, b"ok", extra={"Set-Cookie": f"lfm={TOKEN}; Path=/; HttpOnly"})
            return self._login_page()
        if u.path == "/upload":
            if not self._authed():
                return self._login_page()
            msg = email.parser.BytesParser(policy=email.policy.default).parsebytes(
                b"Content-Type: " + self.headers["Content-Type"].encode() + b"\r\n\r\n" + body)
            fields = {}
            for part in msg.iter_parts():
                disp = part.get("Content-Disposition", "")
                name = part.get_param("name", header="Content-Disposition")
                fname = part.get_filename()
                payload = part.get_payload(decode=True)
                if fname:
                    fields["file_name"] = fname
                    fields["file_data"] = payload
                else:
                    fields[name] = payload.decode("utf-8")
            target_dir = os.path.join(self.root, fields.get("dir", ""))
            os.makedirs(target_dir, exist_ok=True)
            with open(os.path.join(target_dir, fields["file_name"]), "wb") as f:
                f.write(fields["file_data"])
            return self._send(302, b"", extra={"Location": "/"})
        self._send(404, b"not found")

    def do_GET(self):
        u = urllib.parse.urlsplit(self.path)
        q = urllib.parse.parse_qs(u.query)
        rel = q.get("p", [""])[0]
        if not self._authed() and self.password:
            return self._login_page()
        full = os.path.join(self.root, rel)
        if u.path == "/api/list":
            if not os.path.isdir(full):
                return self._send(404, b'{"error":"not_found"}', "application/json")
            items = []
            for n in sorted(os.listdir(full), key=lambda x: (not os.path.isdir(os.path.join(full, x)), x.lower())):
                p = os.path.join(full, n)
                items.append({"name": n, "dir": os.path.isdir(p),
                              "size": os.path.getsize(p) if os.path.isfile(p) else 0,
                              "modified": int(os.path.getmtime(p) * 1000),
                              "path": join_rel(rel, n)})
            body = json.dumps({"path": rel, "items": items}).encode()
            return self._send(200, body, "application/json; charset=utf-8")
        if u.path == "/download" and os.path.isfile(full):
            with open(full, "rb") as f:
                data = f.read()
            return self._send(200, data, "application/octet-stream",
                              {"Content-Disposition": f'attachment; filename="{os.path.basename(full)}"'})
        self._send(404, b"not found")


class ClientTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        os.makedirs(os.path.join(cls.tmp, "Download"))
        with open(os.path.join(cls.tmp, "Download", "hello.txt"), "wb") as f:
            f.write(b"xin chao\n")
        FakeShare.root = cls.tmp
        cls.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FakeShare)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def test_list_requires_login(self):
        c = LfmClient(self.url)
        with self.assertRaises(AuthError):
            c.list("")

    def test_wrong_password_rejected(self):
        c = LfmClient(self.url)
        with self.assertRaises(AuthError):
            c.login("sai")

    def test_login_then_list(self):
        c = LfmClient(self.url)
        c.login(PASSWORD)
        path, items = c.list("")
        self.assertEqual(path, "")
        names = [i.name for i in items]
        self.assertIn("Download", names)
        self.assertTrue(next(i for i in items if i.name == "Download").is_dir)
        _, sub = c.list("Download")
        self.assertEqual([i.name for i in sub], ["hello.txt"])
        self.assertEqual(sub[0].size, len(b"xin chao\n"))
        self.assertEqual(sub[0].path, "Download/hello.txt")

    def test_download_file(self):
        c = LfmClient(self.url)
        c.login(PASSWORD)
        _, sub = c.list("Download")
        dest = os.path.join(self.tmp, "copy.txt")
        seen = []
        n = c.download(sub[0], dest, progress=lambda d, t: seen.append(d))
        self.assertEqual(n, len(b"xin chao\n"))
        with open(dest, "rb") as f:
            self.assertEqual(f.read(), b"xin chao\n")
        self.assertTrue(seen)

    def test_upload_file(self):
        c = LfmClient(self.url)
        c.login(PASSWORD)
        src = os.path.join(self.tmp, "gui.bin")
        payload = os.urandom(700_000)  # lớn hơn một chunk để kiểm tra streaming
        with open(src, "wb") as f:
            f.write(payload)
        c.upload("Download", src)
        with open(os.path.join(self.tmp, "Download", "gui.bin"), "rb") as f:
            self.assertEqual(f.read(), payload)

    def test_blank_password_skips_login(self):
        saved = FakeShare.password
        FakeShare.password = ""
        try:
            c = LfmClient(self.url)
            c.login("")  # không có mật khẩu: không gọi /login, chỉ kiểm tra danh sách
            _, items = c.list("")
            self.assertIn("Download", [i.name for i in items])
        finally:
            FakeShare.password = saved

    def test_helpers(self):
        self.assertEqual(join_rel("", "a"), "a")
        self.assertEqual(join_rel("x/y", "a"), "x/y/a")
        self.assertEqual(parent_rel("x/y/a"), "x/y")
        self.assertEqual(parent_rel("a"), "")
        self.assertEqual(human_size(1500), "1.5 KB")
        self.assertEqual(human_size(12), "12 B")

    def test_silent_server_gives_clear_error(self):
        import socket
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.bind(("127.0.0.1", 0))
        srv.listen(5)  # nhận kết nối nhưng không bao giờ trả lời
        port = srv.getsockname()[1]
        try:
            c = LfmClient(f"http://127.0.0.1:{port}", timeout=1)
            with self.assertRaises(LfmError) as ctx:
                c.list("")
            self.assertIn("không phản hồi", str(ctx.exception))
        finally:
            srv.close()

    def test_connection_error_message(self):
        c = LfmClient("http://127.0.0.1:1", timeout=2)
        with self.assertRaises(LfmError):
            c.login("x")


if __name__ == "__main__":
    unittest.main(verbosity=2)
