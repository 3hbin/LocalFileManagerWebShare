"""
Bản dòng lệnh của Web Share client. Chạy được trên Termux, Linux, Windows (không cần giao diện đồ họa).

  python app_cli.py <địa chỉ> [mật khẩu]

Lệnh trong chế độ tương tác:
  ls                 liệt kê thư mục hiện tại trên điện thoại
  cd <tên>           vào thư mục con (cd .. để lên)
  get <số>           tải mục số <số> về thư mục hiện tại của máy này
  put <đường dẫn>    tải tệp từ máy này lên thư mục đang mở trên điện thoại
  help, quit
"""
import getpass
import os
import sys

from lfm_core import AuthError, LfmClient, LfmError, human_size, join_rel, parent_rel


def _progress(done, total):
    if total:
        sys.stdout.write(f"\r  {done * 100 // total:3d}%")
        sys.stdout.flush()
        if done >= total:
            sys.stdout.write("\n")


class Shell:
    def __init__(self, client):
        self.client = client
        self.rel = ""
        self.entries = []

    def refresh(self):
        self.rel, self.entries = self.client.list(self.rel)

    def ls(self):
        self.refresh()
        print(f"Thư mục: /{self.rel}")
        if not self.entries:
            print("  (trống)")
        for i, e in enumerate(self.entries):
            size = "" if e.is_dir else human_size(e.size)
            kind = "[DIR]" if e.is_dir else "     "
            print(f"  {i:3d}  {kind} {e.name}  {size}")

    def cd(self, arg):
        if arg == "..":
            self.rel = parent_rel(self.rel)
        else:
            match = self._find(arg)
            if not match or not match.is_dir:
                print("Không có thư mục đó. Dùng ls để xem danh sách.")
                return
            self.rel = match.path
        self.refresh()
        print(f"Đã vào /{self.rel}")

    def _find(self, arg):
        if arg.isdigit() and int(arg) < len(self.entries):
            return self.entries[int(arg)]
        for e in self.entries:
            if e.name == arg:
                return e
        return None

    def get(self, arg):
        e = self._find(arg)
        if not e:
            print("Không có mục đó. Dùng ls để xem danh sách.")
            return
        dest = os.path.join(os.getcwd(), e.name + (".zip" if e.is_dir else ""))
        n = self.client.download(e, dest, progress=_progress)
        print(f"Đã tải về {dest} ({human_size(n)})")

    def put(self, path):
        if not os.path.isfile(path):
            print("Không tìm thấy tệp đó trên máy này.")
            return
        self.client.upload(self.rel, path, progress=_progress)
        print(f"Đã tải lên /{self.rel}/{os.path.basename(path)}")
        self.refresh()

    def run(self):
        print("Gõ help để xem lệnh. ls để xem danh sách.")
        try:
            self.refresh()
        except AuthError:
            print("Cần đăng nhập. Chạy lại và nhập đúng mật khẩu.")
            return
        except LfmError as e:
            print(f"Lỗi: {e}")
            return
        self.ls()
        while True:
            try:
                line = input(f"/{self.rel} > ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                return
            if not line:
                continue
            cmd, _, arg = line.partition(" ")
            arg = arg.strip()
            try:
                if cmd in ("quit", "exit", "q"):
                    return
                elif cmd == "help":
                    print(__doc__.split("Lệnh trong chế độ tương tác:")[1])
                elif cmd == "ls":
                    self.ls()
                elif cmd == "cd":
                    self.cd(arg)
                elif cmd == "get":
                    self.get(arg)
                elif cmd == "put":
                    self.put(arg)
                else:
                    print("Lệnh không hợp lệ. Gõ help.")
            except AuthError:
                print("Phiên đăng nhập hết hạn. Khởi động lại client và nhập mật khẩu.")
            except LfmError as e:
                print(f"Lỗi: {e}")


def main(argv):
    if len(argv) < 2:
        print("Cách dùng: python app_cli.py <địa chỉ> [mật khẩu]")
        return 2
    url = argv[1]
    password = argv[2] if len(argv) > 2 else getpass.getpass("Mật khẩu (Enter nếu không có): ")
    client = LfmClient(url)
    try:
        client.login(password)
    except LfmError as e:
        print(f"Không vào được: {e}")
        return 1
    Shell(client).run()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
