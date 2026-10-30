# Kiến trúc ExamWave

```text
ExamWaveStudent.app                       ExamWaveTeacher.app
┌──────────────────────┐                  ┌────────────────────────┐
│ JavaFX student shell │                  │ JavaFX sidebar console │
│ StudentConnection    │── TCP frames ───▶│ ExamCoordinator        │
│ Exam + Assignment UI │◀── JSON body ────│ one virtual thread/peer│
└──────────────────────┘                  └───────────┬────────────┘
                                                    │ JPA / Hibernate
                                        ┌───────────▼────────────┐
                                        │ MySQL 8.4 in Docker    │
                                        │ localhost:3308         │
                                        └────────────────────────┘
```

## 

- Entity và service JPA được viết mới; `ExamStore` quản lý transaction qua `EntityManager` và MySQL Docker.
- Không dùng JSON Lines; `Wire` gửi độ dài 4 byte rồi mới tới JSON.
- `ExamCoordinator` điều phối phiên, deadline và answer snapshot trong RAM.
- Giao diện giảng viên dùng sidebar điều hướng và dashboard dạng card.
- Giao diện sinh viên dùng trang đăng nhập chia đôi, tab Bài thi/Bài tập và thẻ câu hỏi.
- Chỉ có launcher và bộ đóng gói macOS; H2 chỉ xuất hiện ở scope test.

## Quy tắc dữ liệu

- Template đề và câu hỏi ca thi là hai lớp riêng; mở ca sẽ snapshot câu hỏi.
- Mỗi sinh viên có tối đa một attempt trong một ca.
- Hạn thi và hạn bài tập luôn lấy thời điểm máy Server nhận đủ frame.
- Đáp án đang làm giữ trong RAM Server; kết quả cuối cùng lưu MySQL.
- File truyền qua `byte[]` trong JSON và giới hạn 5 MB.
