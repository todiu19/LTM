# Giao thức TCP ExamWave

Mỗi frame gồm:

```text
[4 byte signed big-endian length][JSON UTF-8]
```

JSON có dạng:

```json
{"type":"LOGIN","body":{"studentCode":"B21DCCN001","birthDate":"2003-05-12"}}
```

Các nhóm message chính:

| Client → Server | Server → Client | Ý nghĩa |
|---|---|---|
| `LOGIN` | `LOGIN_REPLY` | Xác thực sinh viên |
| `OFFER` | `OFFER_DATA` | Ca thi đang mở |
| `JOIN` | `PAPER` | Tham gia/làm lại từ đầu |
| `ANSWERS` | — | Snapshot lựa chọn hiện tại |
| `SUBMIT` | `GRADE` | Nộp và chấm bài |
| `HISTORY` | `HISTORY_DATA` | Lịch sử điểm |
| `ASSIGNMENTS` | `ASSIGNMENTS_DATA` | Danh sách bài tập |
| `ASSIGN_SUBMIT` | `ASSIGNMENT_SAVED` | Nộp/nộp lại bài tập |
| `FILE_GET` | `FILE_DATA` | Tải tệp |
| `PING` | `PONG` | Heartbeat |
| — | `WARNING`, `LOCKED`, `ERROR` | Điều hành và lỗi |

Server lấy danh tính từ phiên TCP; payload nộp bài không chứa mã sinh viên tự khai. Mỗi frame tối đa 8 MB.
