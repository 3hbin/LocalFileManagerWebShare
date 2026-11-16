"""
Giao diện Tkinter cho Windows và Linux (chỉ dùng thư viện chuẩn của Python).
Chạy:  python app_gui.py
"""
import os
import queue
import threading
import time
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from lfm_core import AuthError, Entry, LfmClient, LfmError, human_size, join_rel, parent_rel


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Web Share · Máy tính")
        self.geometry("880x560")
        self.minsize(640, 420)
        self.client = None
        self.rel = ""           # thư mục đang mở trên máy chủ
        self.entries = []
        self.ui_queue = queue.Queue()
        self._build()
        self.after(100, self._drain)

    # ---------------------------------------------------------------- giao diện
    def _build(self):
        top = ttk.Frame(self, padding=8)
        top.pack(fill="x")
        ttk.Label(top, text="Địa chỉ:").pack(side="left")
        self.url_var = tk.StringVar(value="http://192.168.1.")
        ttk.Entry(top, textvariable=self.url_var, width=32).pack(side="left", padx=4)
        ttk.Label(top, text="Mật khẩu:").pack(side="left")
        self.pw_var = tk.StringVar()
        ttk.Entry(top, textvariable=self.pw_var, show="*", width=14).pack(side="left", padx=4)
        ttk.Button(top, text="Kết nối", command=self.connect).pack(side="left", padx=4)

        nav = ttk.Frame(self, padding=(8, 0))
        nav.pack(fill="x")
        ttk.Button(nav, text="⬆ Lên", command=self.go_up).pack(side="left")
        ttk.Button(nav, text="⟳ Làm mới", command=self.refresh).pack(side="left", padx=4)
        ttk.Button(nav, text="Tải về", command=self.download_selected).pack(side="left", padx=4)
        ttk.Button(nav, text="Tải lên", command=self.upload_files).pack(side="left", padx=4)
        self.path_var = tk.StringVar(value="(chưa kết nối)")
        ttk.Label(nav, textvariable=self.path_var).pack(side="left", padx=12)

        body = ttk.Frame(self, padding=8)
        body.pack(fill="both", expand=True)
        cols = ("name", "type", "size", "modified")
        self.tree = ttk.Treeview(body, columns=cols, show="headings", selectmode="extended")
        for c, text, w in (("name", "Tên", 380), ("type", "Loại", 80),
                           ("size", "Dung lượng", 120), ("modified", "Sửa đổi", 160)):
            self.tree.heading(c, text=text)
            self.tree.column(c, width=w, anchor="w" if c == "name" else "e" if c == "size" else "w")
        sb = ttk.Scrollbar(body, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=sb.set)
        self.tree.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")
        self.tree.bind("<Double-1>", lambda e: self.open_selected())

        bottom = ttk.Frame(self, padding=8)
        bottom.pack(fill="x")
        self.progress = ttk.Progressbar(bottom, maximum=100)
        self.progress.pack(fill="x")
        self.status_var = tk.StringVar(value="Sẵn sàng")
        ttk.Label(bottom, textvariable=self.status_var).pack(anchor="w", pady=(4, 0))

    # ---------------------------------------------------------------- tiện ích UI
    def _set_status(self, text):
        self.status_var.set(text)

    def _set_progress(self, pct):
        self.progress["value"] = max(0, min(100, pct))

    def _run(self, fn, on_ok=None, busy="Đang xử lý…"):
        """Chạy một việc nền rồi trả kết quả về luồng giao diện qua hàng đợi."""
        self._set_status(busy)
        self._set_progress(0)

        def worker():
            try:
                result = fn()
                self.ui_queue.put(("ok", on_ok, result))
            except AuthError as e:
                self.ui_queue.put(("auth", None, str(e)))
            except LfmError as e:
                self.ui_queue.put(("error", None, str(e)))
            except Exception as e:  # lỗi không lường trước
                self.ui_queue.put(("error", None, f"Lỗi: {e}"))

        threading.Thread(target=worker, daemon=True).start()

    def _drain(self):
        try:
            while True:
                kind, callback, payload = self.ui_queue.get_nowait()
                if kind == "progress":
                    self._set_progress(payload)
                elif kind == "ok":
                    if callback:
                        callback(payload)
                    self._set_status("Xong")
                elif kind == "auth":
                    self._set_status("Cần đăng nhập")
                    messagebox.showwarning("Cần đăng nhập", payload)
                else:
                    self._set_status(payload)
                    messagebox.showerror("Lỗi", payload)
        except queue.Empty:
            pass
        self.after(100, self._drain)

    def _progress_cb(self, done, total):
        if total:
            self.ui_queue.put(("progress", None, done * 100 // total))

    # ---------------------------------------------------------------- hành động
    def connect(self):
        url = self.url_var.get().strip()
        pw = self.pw_var.get()

        def job():
            c = LfmClient(url)
            c.login(pw)
            return c

        def ok(c):
            self.client = c
            self.open_path("")

        self._run(job, on_ok=ok, busy="Đang kết nối…")

    def open_path(self, rel):
        if not self.client:
            messagebox.showinfo("Chưa kết nối", "Nhập địa chỉ và mật khẩu rồi bấm Kết nối.")
            return
        client = self.client

        def ok(result):
            path, entries = result
            self.rel = path
            self.entries = entries
            self.path_var.set("/" + path if path else "/ (gốc)")
            self.tree.delete(*self.tree.get_children())
            for i, e in enumerate(entries):
                self.tree.insert("", "end", iid=str(i), values=(
                    e.name,
                    "Thư mục" if e.is_dir else "Tệp",
                    "" if e.is_dir else human_size(e.size),
                    time.strftime("%d/%m/%Y %H:%M", time.localtime(e.modified / 1000)) if e.modified else "",
                ))

        self._run(lambda: client.list(rel), on_ok=ok, busy="Đang tải danh sách…")

    def refresh(self):
        self.open_path(self.rel)

    def go_up(self):
        if self.rel:
            self.open_path(parent_rel(self.rel))

    def _selected_entries(self):
        return [self.entries[int(i)] for i in self.tree.selection()]

    def open_selected(self):
        sel = self._selected_entries()
        if sel and sel[0].is_dir:
            self.open_path(sel[0].path)

    def download_selected(self):
        sel = self._selected_entries()
        if not sel:
            messagebox.showinfo("Chọn mục", "Chọn một hoặc nhiều mục để tải về.")
            return
        folder = filedialog.askdirectory(title="Chọn thư mục lưu")
        if not folder:
            return
        client = self.client

        def job():
            for e in sel:
                dest = os.path.join(folder, e.name + (".zip" if e.is_dir else ""))
                client.download(e, dest, progress=self._progress_cb)
            return len(sel)

        self._run(job, on_ok=lambda n: messagebox.showinfo("Xong", f"Đã tải {n} mục về {folder}"),
                  busy="Đang tải về…")

    def upload_files(self):
        if not self.client:
            messagebox.showinfo("Chưa kết nối", "Nhập địa chỉ và mật khẩu rồi bấm Kết nối.")
            return
        paths = filedialog.askopenfilenames(title="Chọn tệp để tải lên")
        if not paths:
            return
        client, target = self.client, self.rel

        def job():
            for p in paths:
                client.upload(target, p, progress=self._progress_cb)
            return len(paths)

        def ok(n):
            messagebox.showinfo("Xong", f"Đã tải lên {n} tệp")
            self.refresh()

        self._run(job, on_ok=ok, busy="Đang tải lên…")


if __name__ == "__main__":
    App().mainloop()
