"""
Giao thức Web Share cho app ngoài (Windows, Linux, và các nền tảng khác dùng Python).
Không phụ thuộc giao diện: có thể dùng từ dòng lệnh hoặc từ GUI.

Hợp đồng với máy chủ Android (LocalWebServer):
  POST /login            form: password=...   -> cookie "lfm" khi đúng mật khẩu
  GET  /api/list?p=REL   -> {"path": "...", "items": [{name, dir, size, modified, path}]}
  GET  /download?p=REL   -> nội dung tệp (có Content-Disposition)
  GET  /zip?p=REL        -> thư mục dạng ZIP
  POST /upload           multipart: dir=<thư mục đích>, file=<tệp>
"""
from __future__ import annotations

import http.client
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from http.cookiejar import CookieJar
from typing import Callable, List, Optional, Tuple

ProgressFn = Optional[Callable[[int, int], None]]


class LfmError(Exception):
    """Lỗi chung khi nói chuyện với máy chủ Web Share."""


class AuthError(LfmError):
    """Chưa đăng nhập hoặc mật khẩu sai."""


@dataclass
class Entry:
    name: str
    is_dir: bool
    size: int
    modified: int
    path: str


class LfmClient:
    def __init__(self, base_url: str, timeout: float = 15.0):
        base_url = base_url.strip()
        if not base_url.startswith(("http://", "https://")):
            base_url = "http://" + base_url
        self.base = base_url.rstrip("/")
        self.timeout = timeout
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    # ------------------------------------------------------------------ helpers
    def _url(self, endpoint: str, **params: str) -> str:
        q = urllib.parse.urlencode(params)
        return f"{self.base}{endpoint}" + (f"?{q}" if q else "")

    def _cookie_header(self) -> str:
        return "; ".join(f"{c.name}={c.value}" for c in self.jar)

    # ------------------------------------------------------------------ API
    def login(self, password: str) -> None:
        """Đăng nhập. Ném AuthError nếu sai mật khẩu. Máy không đặt mật khẩu thì luôn qua."""
        if not password:
            # Máy chủ không đặt mật khẩu: không cần đăng nhập, chỉ cần kiểm tra truy cập được
            self.list("")
            return
        data = urllib.parse.urlencode({"password": password}).encode("utf-8")
        req = urllib.request.Request(f"{self.base}/login", data=data, method="POST")
        try:
            with self.opener.open(req, timeout=self.timeout) as r:
                r.read()
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                raise AuthError("Sai mật khẩu") from e
            if e.code == 404:
                # Máy chủ không có trang đăng nhập: kiểm tra trực tiếp bằng API
                self.list("")
                return
            raise LfmError(f"Đăng nhập lỗi, mã {e.code}") from e
        except urllib.error.URLError as e:
            raise LfmError(f"Không kết nối được tới {self.base}: {e.reason}") from e
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e
        # Kiểm tra lại: nếu vẫn bị chặn thì mật khẩu sai
        self.list("")

    def list(self, rel: str = "") -> Tuple[str, List[Entry]]:
        url = self._url("/api/list", p=rel)
        try:
            with self.opener.open(url, timeout=self.timeout) as r:
                ctype = r.headers.get("Content-Type", "")
                body = r.read()
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                raise AuthError("Cần đăng nhập") from e
            if e.code == 404:
                raise LfmError("Thư mục không tồn tại") from e
            raise LfmError(f"Máy chủ trả lỗi {e.code}") from e
        except urllib.error.URLError as e:
            raise LfmError(f"Không kết nối được tới {self.base}: {e.reason}") from e
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e
        if "application/json" not in ctype:
            # Máy chủ trả trang đăng nhập thay vì JSON
            raise AuthError("Cần đăng nhập")
        data = json.loads(body.decode("utf-8"))
        entries = [
            Entry(
                name=i["name"],
                is_dir=bool(i["dir"]),
                size=int(i.get("size", 0)),
                modified=int(i.get("modified", 0)),
                path=i["path"],
            )
            for i in data.get("items", [])
        ]
        return data.get("path", rel), entries

    def download(self, entry: Entry, dest: str, progress: ProgressFn = None) -> int:
        """Tải một tệp (hoặc thư mục dạng ZIP) về đường dẫn dest. Trả về số byte đã ghi."""
        endpoint = "/zip" if entry.is_dir else "/download"
        url = self._url(endpoint, p=entry.path)
        # Thư mục phải được nén trên điện thoại trước khi gửi, nên chờ lâu hơn
        wait = max(self.timeout, 600.0) if entry.is_dir else self.timeout
        try:
            with self.opener.open(url, timeout=wait) as r:
                if "Content-Disposition" not in r.headers:
                    raise AuthError("Cần đăng nhập")
                total = int(r.headers.get("Content-Length") or 0)
                done = 0
                tmp = dest + ".part"
                with open(tmp, "wb") as out:
                    while True:
                        chunk = r.read(256 * 1024)
                        if not chunk:
                            break
                        out.write(chunk)
                        done += len(chunk)
                        if progress:
                            progress(done, total)
                os.replace(tmp, dest)
                return done
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                raise AuthError("Cần đăng nhập") from e
            raise LfmError(f"Tải về lỗi, mã {e.code}") from e
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e

    # ------------------------------------------------------------ thao tác (tìm, đổi tên, xoá, tạo thư mục)
    def _get_json(self, endpoint: str, **params: str) -> dict:
        url = self._url(endpoint, **params)
        try:
            with self.opener.open(url, timeout=self.timeout) as r:
                ctype = r.headers.get("Content-Type", "")
                body = r.read()
        except urllib.error.HTTPError as e:
            raise _api_error(e) from e
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e
        if "application/json" not in ctype:
            raise AuthError("Cần đăng nhập")
        return json.loads(body.decode("utf-8"))

    def _post_json(self, endpoint: str, fields: dict) -> dict:
        data = urllib.parse.urlencode(fields).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base}{endpoint}", data=data, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        try:
            with self.opener.open(req, timeout=self.timeout) as r:
                ctype = r.headers.get("Content-Type", "")
                body = r.read()
        except urllib.error.HTTPError as e:
            raise _api_error(e) from e
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e
        if "application/json" not in ctype:
            raise AuthError("Cần đăng nhập")
        return json.loads(body.decode("utf-8"))

    def search(self, rel: str, query: str) -> List[Entry]:
        """Tìm theo tên trong thư mục rel và các thư mục con (tối đa 200 kết quả)."""
        data = self._get_json("/api/search", p=rel, q=query)
        return _entries(data.get("items", []))

    def rename(self, rel: str, new_name: str) -> str:
        data = self._post_json("/api/rename", {"p": rel, "name": new_name})
        return data.get("path", "")

    def delete(self, rel: str) -> None:
        """Xoá: máy chủ chuyển mục vào thư mục ẩn .LocalFM_Trash trên điện thoại (có thể khôi phục)."""
        self._post_json("/api/delete", {"p": rel})

    def mkdir(self, parent_rel: str, name: str) -> str:
        data = self._post_json("/api/mkdir", {"p": parent_rel, "name": name})
        return data.get("path", "")

    def upload(self, rel_dir: str, local_file: str, progress: ProgressFn = None) -> None:
        """Tải một tệp lên thư mục rel_dir trên máy chủ. Gửi theo kiểu streaming, không nạp cả tệp vào bộ nhớ."""
        if not os.path.isfile(local_file):
            raise LfmError(f"Không có tệp: {local_file}")
        u = urllib.parse.urlsplit(self.base)
        boundary = "----lfm" + uuid.uuid4().hex
        name = os.path.basename(local_file).replace('"', "_")
        pre = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="dir"\r\n\r\n{rel_dir}\r\n'
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{name}"\r\n'
            "Content-Type: application/octet-stream\r\n\r\n"
        ).encode("utf-8")
        post = f"\r\n--{boundary}--\r\n".encode("utf-8")
        size = os.path.getsize(local_file)
        total = len(pre) + size + len(post)

        port = u.port or (443 if u.scheme == "https" else 80)
        ConnCls = http.client.HTTPSConnection if u.scheme == "https" else http.client.HTTPConnection
        conn = ConnCls(u.hostname, port, timeout=60)
        path = (u.path.rstrip("/") + "/upload") or "/upload"
        headers = {
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Content-Length": str(total),
        }
        cookie = self._cookie_header()
        if cookie:
            headers["Cookie"] = cookie
        try:
            conn.putrequest("POST", path)
            for k, v in headers.items():
                conn.putheader(k, v)
            conn.endheaders()
            conn.send(pre)
            sent = len(pre)
            with open(local_file, "rb") as f:
                while True:
                    chunk = f.read(256 * 1024)
                    if not chunk:
                        break
                    conn.send(chunk)
                    sent += len(chunk)
                    if progress:
                        progress(sent, total)
            conn.send(post)
            resp = conn.getresponse()
            resp.read()
            if resp.status in (401, 403):
                raise AuthError("Cần đăng nhập")
            if resp.status >= 400:
                raise LfmError(f"Tải lên lỗi, mã {resp.status}")
        except OSError as e:
            raise LfmError(_no_response_msg(self.base, e)) from e
        finally:
            conn.close()


_API_MESSAGES = {
    "not_found": "Không tìm thấy mục này",
    "locked": "Mục đang khoá chỉ đọc",
    "root": "Không thao tác được với thư mục gốc",
    "exists": "Đã có mục cùng tên",
    "bad_name": "Tên không hợp lệ (không dùng dấu / và không bắt đầu bằng dấu chấm)",
    "failed": "Máy chủ không thực hiện được thao tác",
    "not_a_directory": "Đây không phải thư mục",
    "empty_query": "Nhập từ khoá tìm kiếm",
}


def _api_error(e: urllib.error.HTTPError) -> Exception:
    if e.code in (401, 403) and not _has_json_error(e):
        return AuthError("Cần đăng nhập")
    code = None
    try:
        code = json.loads(e.read().decode("utf-8")).get("error")
    except Exception:
        pass
    return LfmError(_API_MESSAGES.get(code, f"Lỗi máy chủ, mã {e.code}"))


def _has_json_error(e: urllib.error.HTTPError) -> bool:
    return "application/json" in (e.headers.get("Content-Type", "") if e.headers else "")


def _entries(items) -> List[Entry]:
    return [
        Entry(name=i["name"], is_dir=bool(i["dir"]), size=int(i.get("size", 0)),
              modified=int(i.get("modified", 0)), path=i["path"])
        for i in items
    ]


def _no_response_msg(base: str, err: Exception) -> str:
    if isinstance(err, TimeoutError) or "timed out" in str(err).lower():
        return (f"Máy chủ {base} không phản hồi. Kiểm tra Web Share đang bật. "
                "Nếu đã bật, mở lại app Web Share (máy có thể đã tạm dừng app chạy nền).")
    return f"Không kết nối được tới {base}: {err}"


def join_rel(parent: str, name: str) -> str:
    return f"{parent}/{name}".strip("/") if parent else name


def parent_rel(rel: str) -> str:
    return rel.rsplit("/", 1)[0] if "/" in rel else ""


def human_size(n: int) -> str:
    for unit, div in (("GB", 1000 ** 3), ("MB", 1000 ** 2), ("KB", 1000)):
        if n >= div:
            return f"{n / div:.1f} {unit}"
    return f"{n} B"
