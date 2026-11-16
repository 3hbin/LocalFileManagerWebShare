# Web Share · Client Windows / Linux (Python)

Client nhỏ để duyệt, tải về và tải lên tới điện thoại đang bật **Web Share** trong cùng mạng Wi-Fi.
Chỉ dùng thư viện chuẩn của Python, không cần cài thêm gì.

## Yêu cầu
- Python 3.9 trở lên (Windows và Linux). Trên Linux có thể cần cài gói `python3-tk`.
- Điện thoại đang bật Web Share (cùng mạng Wi-Fi).

## Chạy
```
python app_gui.py
```
1. Nhập địa chỉ hiển thị trong app điện thoại, ví dụ `http://192.168.1.8:8120`
   (nếu đã đặt tên link: `http://192.168.1.8:8120/ten-link`).
2. Nhập mật khẩu Web Share (để trống nếu không đặt).
3. Bấm **Kết nối**, duyệt thư mục, bấm đúp để mở.
4. **Tải về**: chọn một hoặc nhiều mục, chọn thư mục lưu. Thư mục được tải về dạng ZIP.
5. **Tải lên**: chọn tệp, tệp được gửi vào thư mục đang mở trên điện thoại.

## Chạy trên Termux (Android)
Termux không có giao diện đồ họa, nên dùng bản dòng lệnh `app_cli.py`:
```
pkg install python
python app_cli.py http://192.168.1.8:8120
```
Trong chế độ tương tác: `ls`, `cd <tên>`, `cd ..`, `get <số>` (tải về), `put <đường dẫn>` (tải lên), `find <từ khoá>` (tìm), `rename <số> <tên mới>`, `rm <số>` (chuyển vào thùng rác trên điện thoại, có hỏi xác nhận), `mkdir <tên>`, `quit`.
Lệnh `get` lưu vào thư mục đang đứng trong Termux (`pwd` để xem).
Chạy trên Termux cần quyền bộ nhớ: `termux-setup-storage` (để lưu vào Download nếu muốn).

## Kiểm thử (không cần điện thoại)
```
python test_lfm_core.py
```
Kiểm thử chạy một máy chủ giả đúng theo hợp đồng API và kiểm tra đăng nhập,
liệt kê, tải về, tải lên (kể cả tệp lớn gửi theo từng phần).

## Cấu trúc
- `lfm_core.py`: giao thức (không có giao diện), dùng lại được cho các client khác.
- `app_gui.py`: giao diện Tkinter (Windows, Linux có màn hình).
- `app_cli.py`: bản dòng lệnh (Termux, Linux, Windows).
- `test_lfm_core.py`: kiểm thử giao thức.

## Giới hạn
- Chưa có di chuyển/sao chép giữa các thư mục trên điện thoại.
- Xoá không xoá hẳn: mục được chuyển vào thư mục ẩn `.LocalFM_Trash` trên điện thoại (cần cài Web Share bản 1.12.0 trở lên).
- Chưa có tự phát hiện máy trong mạng (phải nhập địa chỉ).
- Web Share không mã hoá (HTTP). Chỉ dùng trong mạng nội bộ tin cậy.
- Giao diện Tkinter chưa được chạy thử trên màn hình thật trong môi trường đã kiểm tra.
