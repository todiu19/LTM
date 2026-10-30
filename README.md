# ExamWave — hệ thống thi LAN 

ExamWave là project mới viết từ đầu, chỉ dành cho macOS. Ứng dụng gồm hai chương trình JavaFX độc lập:

- **ExamWaveTeacher**: giảng viên quản lý dữ liệu và chạy TCP Server.
- **ExamWaveStudent**: sinh viên kết nối tới Server trong cùng mạng LAN.

Project không dùng mã nguồn, package, entity hay giao thức của `LTM-main`. Dữ liệu ứng dụng được lưu bằng JPA/Hibernate trong MySQL 8 chạy bằng Docker trên máy giảng viên. Client sinh viên chỉ kết nối TCP tới Server và không truy cập MySQL.

## Công nghệ và kiến trúc

- Java 21, JavaFX 21.
- TCP Socket với JSON có khung độ dài 4 byte; giới hạn 8 MB mỗi gói.
- Virtual thread cho từng Client.
- JPA/Hibernate ORM và MySQL 8.4 chạy bằng Docker.
- PBKDF2-HMAC-SHA256 cho mật khẩu giảng viên.
- Maven chỉ dùng để build/chạy trong môi trường phát triển.

Xem thêm [kiến trúc](docs/ARCHITECTURE.md) và [giao thức](docs/PROTOCOL.md).

## Chức năng

### Giảng viên

- Tạo/đăng nhập tài khoản, đổi mật khẩu.
- Dữ liệu môn, câu hỏi, đề, ca thi và bài tập tách theo giảng viên.
- Ngân hàng câu hỏi trắc nghiệm theo môn và độ khó 1–6.
- Tạo đề từ nhiều câu hỏi, mở ca thi với hạn chung.
- Theo dõi sinh viên online và trạng thái `ONLINE`, `DOING_EXAM`, `SUBMITTED`.
- Gửi cảnh báo, khóa và mở khóa tài khoản sinh viên.
- Chấm bài thủ công khi nộp và tự chấm khi hết giờ.
- Xem lịch sử kết quả.
- Giao bài tập có hạn nộp/tệp đính kèm; xem và tải bài nộp.

### Sinh viên

- Đăng nhập bằng mã sinh viên và ngày sinh.
- Xem ca thi đang mở, lịch sử điểm và danh sách bài tập.
- Tham gia hoặc vào lại với bài trống và hạn chung không đổi.
- Làm trắc nghiệm, đồng hồ đếm ngược, tự nộp khi hết giờ.
- Nhận cảnh báo hoặc thông báo khóa tài khoản.
- Nộp/nộp lại bài tập bằng nội dung và tệp tối đa 5 MB trước hạn.

## Tài khoản sinh viên demo

| Mã | Ngày sinh | Họ tên |
|---|---|---|
| B21DCCN001 | 12/05/2003 | Nguyễn Văn An |
| B21DCCN002 | 20/08/2003 | Trần Minh Bình |
| B21DCCN003 | 03/11/2003 | Lê Hoàng Cường |
| B21DCCN004–B21DCCN103 | 12/12/2003 | Sinh viên 004–103 |

Giảng viên tự tạo tài khoản ở lần mở đầu tiên.

## Chạy từ mã nguồn trên macOS

Cần JDK 21, Maven 3.9+ và Docker Desktop.

Khởi động database trước:

```bash
./start-mysql.command
```

Container dùng `localhost:3308`, database `examwave_next`, tài khoản mặc định `root/root`. Hibernate tự tạo/cập nhật các bảng tiền tố `ew_`. Có thể đặt `DB_URL`, `DB_USER`, `DB_PASSWORD` nếu dùng MySQL khác.

Tài khoản giảng viên demo được seed tự động: `giangvien.demo` / `examwave123`. Tài khoản này có sẵn 2 môn học, 8 câu hỏi, 2 đề thi và 2 bài tập mẫu.

Sau đó double-click:

```text
run-teacher.command
run-student.command
```

Hoặc Terminal:

```bash
mvn javafx:run -Pteacher
mvn javafx:run -Pstudent
```

Server mặc định chạy cổng `5050`. Khi thử trên cùng máy, Client nhập `127.0.0.1`. Khi dùng nhiều máy Mac trong LAN, nhập IP Wi‑Fi của máy giảng viên.

## Tạo ứng dụng `.app`

Tạo cả hai ứng dụng:

```bash
./build-macos.command
```

Hoặc chỉ tạo một phía:

```bash
./build-client-mac.command
./build-teacher-mac.command
```

Kết quả nằm trong `dist/` và đã kèm Java runtime. Bản Apple Silicon và Intel phải được build trên đúng kiến trúc tương ứng.

## Kiểm thử

```bash
mvn test
```

Bộ test kiểm tra vòng đời đề thi, chấm điểm, bài tập, khóa tài khoản, khung giao thức và luồng TCP thật.

## Giới hạn triển khai

Giao thức chưa mã hóa TLS và tài khoản sinh viên vẫn dùng ngày sinh. Project phù hợp demo/phòng máy trong LAN tin cậy; cần bổ sung TLS, mật khẩu riêng và audit log trước khi dùng cho kỳ thi chính thức.
