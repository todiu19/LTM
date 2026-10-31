package io.examwave.store;

import io.examwave.common.CsvHelper;
import io.examwave.common.Domain;
import io.examwave.common.Domain.*;
import io.examwave.common.Passwords;
import io.examwave.store.entity.*;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExamStore implements AutoCloseable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final EntityManagerFactory factory;
    public record PendingAttempt(long teacherId, long roundId, String studentCode, long deadlineEpochMillis) {}

    public ExamStore() {
        this(createFactory(Map.of(
                "jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver",
                "jakarta.persistence.jdbc.url", env("DB_URL", "jdbc:mysql://localhost:3308/examwave_next?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"),
                "jakarta.persistence.jdbc.user", env("DB_USER", "root"),
                "jakarta.persistence.jdbc.password", env("DB_PASSWORD", "root"),
                "hibernate.hbm2ddl.auto", "update")), true);
    }

    /** Constructor dành cho test H2; ứng dụng thật luôn dùng MySQL. */
    public ExamStore(String jdbcUrl) {
        this(createFactory(Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", jdbcUrl.contains("MODE=") ? jdbcUrl : jdbcUrl + ";MODE=MySQL",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "hibernate.dialect", "org.hibernate.dialect.H2Dialect",
                "hibernate.hbm2ddl.auto", "create-drop")), false);
    }

    private ExamStore(EntityManagerFactory factory, boolean includeDemoData) {
        this.factory = factory;
        seedStudents();
        if (includeDemoData) seedDemoData();
    }

    private static EntityManagerFactory createFactory(Map<String, Object> properties) {
        return Persistence.createEntityManagerFactory("examwave-pu", properties);
    }

    private void seedStudents() {
        tx(em -> {
            if (em.createQuery("select count(s) from StudentEntity s", Long.class).getSingleResult() > 0) return null;
            em.persist(new StudentEntity("B21DCCN001", "Nguyễn Văn An", LocalDate.of(2003, 5, 12), "D21CQCN01-B"));
            em.persist(new StudentEntity("B21DCCN002", "Trần Minh Bình", LocalDate.of(2003, 8, 20), "D21CQCN01-B"));
            em.persist(new StudentEntity("B21DCCN003", "Lê Hoàng Cường", LocalDate.of(2003, 11, 3), "D21CQCN01-B"));
            for (int index = 4; index <= 50; index++) {
                em.persist(new StudentEntity("B21DCCN%03d".formatted(index), "Sinh viên %03d".formatted(index), LocalDate.of(2003, 12, 12), "D21CQCN01-B"));
            }
            for (int index = 51; index <= 103; index++) {
                em.persist(new StudentEntity("B21DCCN%03d".formatted(index), "Sinh viên %03d".formatted(index), LocalDate.of(2003, 12, 12), "D21CQCN02-B"));
            }
            return null;
        });
    }

    private void seedDemoData() {
        tx(em -> {
            boolean exists = !em.createQuery("select t.id from TeacherEntity t where t.username=:username", Long.class)
                    .setParameter("username", "giangvien.demo").setMaxResults(1).getResultList().isEmpty();
            if (exists) return null;

            TeacherEntity teacher = new TeacherEntity("giangvien.demo", "Giảng viên Demo", Passwords.hash("examwave123"));
            em.persist(teacher);
            em.flush();

            em.persist(new ClassEntity(teacher.getId(), "D21CQCN01-B", System.currentTimeMillis()));
            em.persist(new ClassEntity(teacher.getId(), "D21CQCN02-B", System.currentTimeMillis()));

            SubjectEntity networking = new SubjectEntity(teacher.getId(), "Lập trình mạng", "lập trình mạng");
            SubjectEntity database = new SubjectEntity(teacher.getId(), "Cơ sở dữ liệu", "cơ sở dữ liệu");
            em.persist(networking);
            em.persist(database);

            List<BankQuestionEntity> networkQuestions = List.of(
                    demoQuestion(teacher.getId(), networking, 1, "TCP là giao thức thuộc tầng nào trong mô hình TCP/IP?", "Tầng ứng dụng", "Tầng giao vận", "Tầng Internet", "Tầng truy nhập mạng", "B"),
                    demoQuestion(teacher.getId(), networking, 2, "Lớp Java nào dùng để chờ kết nối TCP từ client?", "DatagramSocket", "HttpClient", "ServerSocket", "URL", "C"),
                    demoQuestion(teacher.getId(), networking, 2, "Cổng TCP hợp lệ nằm trong khoảng nào?", "0–255", "1–65535", "1024–9999", "1–99999", "B"),
                    demoQuestion(teacher.getId(), networking, 3, "Thao tác nào giúp server phục vụ nhiều client đồng thời?", "Dùng một luồng cho toàn bộ client", "Đóng socket sau khi accept", "Xử lý mỗi kết nối bằng một luồng riêng", "Chỉ tăng kích thước buffer", "C")
            );
            List<BankQuestionEntity> databaseQuestions = List.of(
                    demoQuestion(teacher.getId(), database, 1, "Annotation nào đánh dấu một lớp là JPA entity?", "@Table", "@Entity", "@Column", "@Bean", "B"),
                    demoQuestion(teacher.getId(), database, 2, "Khóa chính trong JPA được đánh dấu bằng annotation nào?", "@Key", "@Primary", "@Id", "@Unique", "C"),
                    demoQuestion(teacher.getId(), database, 2, "Lệnh SQL nào kết hợp dữ liệu từ nhiều bảng?", "JOIN", "DROP", "TRUNCATE", "GRANT", "A"),
                    demoQuestion(teacher.getId(), database, 3, "Mục đích chính của transaction là gì?", "Đổi tên bảng", "Đảm bảo nhóm thao tác hoàn thành nhất quán", "Tăng kích thước cột", "Mã hóa mật khẩu", "B")
            );
            networkQuestions.forEach(em::persist);
            databaseQuestions.forEach(em::persist);

            ExamTemplateEntity networkExam = new ExamTemplateEntity(teacher.getId(), networking, "Ôn tập lập trình mạng", 20);
            for (int index = 0; index < networkQuestions.size(); index++) networkExam.addQuestion(networkQuestions.get(index), index);
            ExamTemplateEntity databaseExam = new ExamTemplateEntity(teacher.getId(), database, "Kiểm tra nhanh JPA và MySQL", 15);
            for (int index = 0; index < databaseQuestions.size(); index++) databaseExam.addQuestion(databaseQuestions.get(index), index);
            em.persist(networkExam);
            em.persist(databaseExam);

            long now = System.currentTimeMillis();
            em.persist(new AssignmentEntity(teacher.getId(), "Xây dựng TCP Chat",
                    "Viết ứng dụng chat client/server dùng TCP, hỗ trợ tối thiểu ba client đồng thời.",
                    now, now + 7L * 24 * 60 * 60 * 1000, null, null));
            em.persist(new AssignmentEntity(teacher.getId(), "Thiết kế mô hình JPA",
                    "Thiết kế các entity cho hệ thống quản lý lớp học và mô tả quan hệ giữa chúng.",
                    now, now + 14L * 24 * 60 * 60 * 1000, null, null));
            return null;
        });
    }

    private static BankQuestionEntity demoQuestion(long teacherId, SubjectEntity subject, int difficulty,
                                                    String prompt, String a, String b, String c, String d, String answer) {
        return new BankQuestionEntity(teacherId, subject, difficulty, prompt, a, b, c, d, answer);
    }

    public Teacher createTeacher(String username, String fullName, String password) {
        String login = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        String name = fullName == null ? "" : fullName.strip();
        if (!login.matches("[a-z0-9][a-z0-9._-]{2,49}")) throw new IllegalArgumentException("Tên đăng nhập phải có 3–50 ký tự không dấu.");
        if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Họ tên không hợp lệ.");
        String hash = Passwords.hash(password);
        return tx(em -> {
            if (!em.createQuery("select t.id from TeacherEntity t where t.username=:login", Long.class).setParameter("login", login).getResultList().isEmpty())
                throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
            TeacherEntity entity = new TeacherEntity(login, name, hash);
            em.persist(entity); em.flush(); return teacher(entity);
        });
    }

    public Optional<Teacher> authenticateTeacher(String username, String password) {
        String login = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return read(em -> em.createQuery("select t from TeacherEntity t where t.username=:login", TeacherEntity.class)
                .setParameter("login", login).getResultStream().findFirst()
                .filter(entity -> Passwords.matches(password, entity.getPasswordHash())).map(ExamStore::teacher));
    }

    public void changeTeacherPassword(long teacherId, String oldPassword, String newPassword) {
        Passwords.validate(newPassword);
        tx(em -> {
            TeacherEntity teacher = em.find(TeacherEntity.class, teacherId, LockModeType.PESSIMISTIC_WRITE);
            if (teacher == null || !Passwords.matches(oldPassword, teacher.getPasswordHash())) throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
            teacher.setPasswordHash(Passwords.hash(newPassword)); return null;
        });
    }

    public Optional<Student> authenticateStudent(String code, String birthDate) {
        String normalized = normalizeCode(code);
        final LocalDate birth;
        try { birth = LocalDate.parse(birthDate); } catch (Exception ignored) { return Optional.empty(); }
        return read(em -> {
            StudentEntity entity = em.find(StudentEntity.class, normalized);
            if (entity == null || !entity.getBirthDate().equals(birth)) return Optional.empty();
            if (entity.isLocked()) throw new IllegalStateException("Tài khoản đã bị khóa: " + safe(entity.getLockReason()));
            return Optional.of(new Student(entity.getCode(), entity.getFullName(), entity.getClassName()));
        });
    }

    // --- QUẢN LÝ LỚP HỌC ---

    public List<StudentClassItem> listClasses(long teacherId) {
        return read(em -> {
            // Lấy các lớp đã được tạo cho teacher
            List<ClassEntity> entities = em.createQuery("select c from ClassEntity c where c.teacherId=:teacher order by c.name", ClassEntity.class)
                    .setParameter("teacher", teacherId).getResultList();
            Set<String> classNames = new LinkedHashSet<>();
            Map<String, Long> idsByName = new HashMap<>();
            for (ClassEntity c : entities) {
                classNames.add(c.getName());
                idsByName.put(c.getName(), c.getId());
            }

            // Lấy thêm các lớp sinh viên đang có trong hệ thống nếu chưa có trong bảng
            List<String> studentClasses = em.createQuery("select distinct s.className from StudentEntity s where s.className is not null", String.class).getResultList();
            for (String sc : studentClasses) {
                if (sc != null && !sc.isBlank() && !classNames.contains(sc)) {
                    classNames.add(sc);
                }
            }

            List<StudentClassItem> items = new ArrayList<>();
            for (String name : classNames) {
                long count = em.createQuery("select count(s) from StudentEntity s where s.className=:name", Long.class)
                        .setParameter("name", name).getSingleResult();
                long id = idsByName.getOrDefault(name, 0L);
                items.add(new StudentClassItem(id, name, count));
            }
            return items;
        });
    }

    public long createClass(long teacherId, String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (name.isBlank() || name.length() > 50) throw new IllegalArgumentException("Tên lớp phải có từ 1 đến 50 ký tự.");
        return tx(em -> {
            List<ClassEntity> exists = em.createQuery("select c from ClassEntity c where c.teacherId=:teacher and lower(c.name)=lower(:name)", ClassEntity.class)
                    .setParameter("teacher", teacherId).setParameter("name", name).getResultList();
            if (!exists.isEmpty()) return exists.get(0).getId();
            ClassEntity entity = new ClassEntity(teacherId, name, System.currentTimeMillis());
            em.persist(entity);
            em.flush();
            return entity.getId();
        });
    }

    public void deleteClass(long teacherId, long classId) {
        tx(em -> {
            ClassEntity entity = em.find(ClassEntity.class, classId);
            if (entity != null && entity.getTeacherId() == teacherId) {
                String className = entity.getName();
                em.createQuery("update StudentEntity s set s.className='Chưa phân lớp' where s.className=:name")
                        .setParameter("name", className).executeUpdate();
                em.remove(entity);
            }
            return null;
        });
    }

    // --- QUẢN LÝ SINH VIÊN & CSV IMPORT ---

    public List<StudentView> listStudents(String className) {
        return read(em -> {
            String jpql = "select s from StudentEntity s";
            boolean filter = className != null && !className.isBlank() && !"Tất cả".equalsIgnoreCase(className.strip());
            if (filter) jpql += " where s.className=:className";
            jpql += " order by s.code asc";

            TypedQuery<StudentEntity> query = em.createQuery(jpql, StudentEntity.class);
            if (filter) query.setParameter("className", className.strip());
            return query.getResultList().stream().map(s -> new StudentView(
                    s.getCode(),
                    s.getFullName(),
                    s.getBirthDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    s.getClassName(),
                    s.isLocked(),
                    safe(s.getLockReason())
            )).toList();
        });
    }

    public void saveStudent(String code, String fullName, LocalDate birthDate, String className) {
        String normCode = normalizeCode(code);
        if (normCode.isBlank() || normCode.length() > 30) throw new IllegalArgumentException("Mã sinh viên không hợp lệ.");
        if (fullName == null || fullName.isBlank() || fullName.strip().length() > 120) throw new IllegalArgumentException("Họ tên sinh viên không hợp lệ.");
        if (birthDate == null) throw new IllegalArgumentException("Ngày sinh không được để trống.");
        String normClass = className == null || className.isBlank() ? "Chưa phân lớp" : className.strip();

        tx(em -> {
            StudentEntity student = em.find(StudentEntity.class, normCode);
            if (student == null) {
                em.persist(new StudentEntity(normCode, fullName.strip(), birthDate, normClass));
            } else {
                student.setFullName(fullName.strip());
                student.setBirthDate(birthDate);
                student.setClassName(normClass);
            }
            return null;
        });
    }

    public StudentImportSummary importStudentsFromCsv(long teacherId, String csvContent, String defaultClassName) {
        if (csvContent == null || csvContent.isBlank()) {
            throw new IllegalArgumentException("Nội dung file CSV trống.");
        }

        List<List<String>> parsedRows = CsvHelper.parseCsv(csvContent);
        if (parsedRows.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy dòng dữ liệu nào trong file CSV.");
        }

        int inserted = 0;
        int updated = 0;
        int processed = 0;
        List<String> errors = new ArrayList<>();
        Set<String> newClassesToCreate = new HashSet<>();

        for (int i = 0; i < parsedRows.size(); i++) {
            List<String> row = parsedRows.get(i);
            if (row.isEmpty()) continue;

            // Kiểm tra nếu là dòng header
            String firstCell = row.get(0).toLowerCase(Locale.ROOT);
            if (firstCell.contains("mã") || firstCell.contains("code") || firstCell.contains("mssv") || firstCell.contains("stt")) {
                continue;
            }

            processed++;
            int rowNumber = i + 1;
            try {
                String code;
                String name;
                String birthStr;
                String studentClass = defaultClassName;

                if (row.size() >= 5 && isInteger(row.get(0))) {
                    // Cấu trúc 5 cột: STT, Mã SV, Họ tên, Ngày sinh, Lớp
                    code = row.get(1);
                    name = row.get(2);
                    birthStr = row.get(3);
                    if (row.size() > 4 && !row.get(4).isBlank()) studentClass = row.get(4);
                } else if (row.size() >= 4) {
                    // Cấu trúc 4 cột: Mã SV, Họ tên, Ngày sinh, Lớp
                    code = row.get(0);
                    name = row.get(1);
                    birthStr = row.get(2);
                    if (!row.get(3).isBlank()) studentClass = row.get(3);
                } else if (row.size() == 3) {
                    // Cấu trúc 3 cột: Mã SV, Họ tên, Ngày sinh (lấy lớp mặc định)
                    code = row.get(0);
                    name = row.get(1);
                    birthStr = row.get(2);
                } else {
                    errors.add("Dòng " + rowNumber + ": Thiếu cột dữ liệu (cần ít nhất Mã SV, Họ tên, Ngày sinh).");
                    continue;
                }

                String normCode = normalizeCode(code);
                if (normCode.isBlank()) {
                    errors.add("Dòng " + rowNumber + ": Mã sinh viên trống.");
                    continue;
                }
                if (name == null || name.isBlank()) {
                    errors.add("Dòng " + rowNumber + ": Họ tên trống.");
                    continue;
                }

                LocalDate birthDate = CsvHelper.parseDate(birthStr);
                String finalClass = studentClass == null || studentClass.isBlank() ? "Chưa phân lớp" : studentClass.strip();

                boolean isNew = tx(em -> {
                    StudentEntity student = em.find(StudentEntity.class, normCode);
                    if (student == null) {
                        em.persist(new StudentEntity(normCode, name.strip(), birthDate, finalClass));
                        return true;
                    } else {
                        student.setFullName(name.strip());
                        student.setBirthDate(birthDate);
                        student.setClassName(finalClass);
                        return false;
                    }
                });

                if (isNew) inserted++;
                else updated++;

                if (!"Chưa phân lớp".equalsIgnoreCase(finalClass)) {
                    newClassesToCreate.add(finalClass);
                }
            } catch (Exception ex) {
                errors.add("Dòng " + rowNumber + ": " + ex.getMessage());
            }
        }

        // Tự động tạo các lớp mới nếu chưa có
        for (String cName : newClassesToCreate) {
            try {
                createClass(teacherId, cName);
            } catch (Exception ignored) {}
        }

        return new StudentImportSummary(processed, inserted, updated, errors);
    }

    private static boolean isInteger(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            Integer.parseInt(str.strip());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // --- MÔN HỌC & CÂU HỎI ---

    public long createSubject(long teacherId, String rawName) {
        String name = rawName == null ? "" : rawName.strip().replaceAll("\\s+", " ");
        if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Tên môn phải có từ 1 đến 120 ký tự.");
        String normalized = name.toLowerCase(Locale.ROOT);
        return tx(em -> {
            if (!em.createQuery("select s.id from SubjectEntity s where s.teacherId=:teacher and s.normalizedName=:name", Long.class)
                    .setParameter("teacher", teacherId).setParameter("name", normalized).getResultList().isEmpty())
                throw new IllegalArgumentException("Môn học đã tồn tại.");
            SubjectEntity subject = new SubjectEntity(teacherId, name, normalized);
            em.persist(subject); em.flush(); return subject.getId();
        });
    }

    public List<SubjectItem> listSubjects(long teacherId) {
        return read(em -> em.createQuery("select s from SubjectEntity s where s.teacherId=:teacher order by s.name", SubjectEntity.class)
                .setParameter("teacher", teacherId).getResultList().stream().map(subject -> {
                    long count = em.createQuery("select count(q) from BankQuestionEntity q where q.subject.id=:subject", Long.class)
                            .setParameter("subject", subject.getId()).getSingleResult();
                    return new SubjectItem(subject.getId(), subject.getName(), count);
                }).toList());
    }

    public long addQuestion(long teacherId, QuestionDraft draft) {
        validateQuestion(draft);
        return tx(em -> {
            SubjectEntity subject = requireSubject(em, teacherId, draft.subjectId());
            BankQuestionEntity question = new BankQuestionEntity(teacherId, subject, draft.difficulty(), draft.prompt().strip(),
                    draft.optionA().strip(), draft.optionB().strip(), draft.optionC().strip(), draft.optionD().strip(),
                    draft.correctOption().strip().toUpperCase(Locale.ROOT));
            em.persist(question); em.flush(); return question.getId();
        });
    }

    public List<BankQuestion> listQuestions(long teacherId, Long subjectId) {
        return read(em -> {
            String jpql = "select q from BankQuestionEntity q join fetch q.subject where q.teacherId=:teacher"
                    + (subjectId == null ? "" : " and q.subject.id=:subject") + " order by q.id desc";
            TypedQuery<BankQuestionEntity> query = em.createQuery(jpql, BankQuestionEntity.class).setParameter("teacher", teacherId);
            if (subjectId != null) query.setParameter("subject", subjectId);
            return query.getResultList().stream().map(ExamStore::bankQuestion).toList();
        });
    }

    public long createTemplate(long teacherId, TemplateDraft draft) {
        validateTemplate(draft);
        return tx(em -> {
            SubjectEntity subject = requireSubject(em, teacherId, draft.subjectId());
            List<BankQuestionEntity> found = em.createQuery("select q from BankQuestionEntity q join fetch q.subject where q.teacherId=:teacher and q.id in :ids", BankQuestionEntity.class)
                    .setParameter("teacher", teacherId).setParameter("ids", draft.questionIds()).getResultList();
            Map<Long, BankQuestionEntity> byId = found.stream().collect(Collectors.toMap(BankQuestionEntity::getId, Function.identity()));
            ExamTemplateEntity template = new ExamTemplateEntity(teacherId, subject, draft.title().strip(), draft.durationMinutes());
            int position = 0;
            for (Long id : draft.questionIds()) {
                BankQuestionEntity question = byId.get(id);
                if (question == null || !Objects.equals(question.getSubject().getId(), subject.getId())) throw new IllegalArgumentException("Câu hỏi không thuộc môn đã chọn.");
                template.addQuestion(question, position++);
            }
            em.persist(template); em.flush(); return template.getId();
        });
    }

    public List<TemplateItem> listTemplates(long teacherId) {
        return read(em -> em.createQuery("select distinct t from ExamTemplateEntity t join fetch t.subject left join fetch t.questions where t.teacherId=:teacher order by t.id desc", ExamTemplateEntity.class)
                .setParameter("teacher", teacherId).getResultList().stream()
                .map(t -> new TemplateItem(t.getId(), t.getTitle(), t.getSubject().getName(), t.getDurationMinutes(), t.getQuestions().size())).toList());
    }

    // --- QUẢN LÝ CA THI & CHIA CA THI ---

    public RoundInfo openTemplate(long teacherId, long templateId, long now) {
        return openRound(teacherId, templateId, null, null, null, null, -1, now);
    }

    public RoundInfo openRound(long teacherId, OpenRoundDraft draft, long now) {
        return openRound(teacherId, draft.templateId(), draft.title(), draft.targetClass(),
                draft.maxParticipants(), null, draft.durationMinutes(), now);
    }

    public RoundInfo openRound(long teacherId, long templateId, String customTitle,
                              String targetClass, Integer maxParticipants, String assignedStudents,
                              int customDurationMinutes, long now) {
        return tx(em -> {
            ExamTemplateEntity template = em.createQuery("select distinct t from ExamTemplateEntity t join fetch t.subject left join fetch t.questions x left join fetch x.question where t.id=:id and t.teacherId=:teacher", ExamTemplateEntity.class)
                    .setParameter("id", templateId).setParameter("teacher", teacherId).getResultStream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đề thi."));
            if (template.getQuestions().isEmpty()) throw new IllegalStateException("Đề thi không có câu hỏi.");

            int duration = customDurationMinutes > 0 ? customDurationMinutes : template.getDurationMinutes();
            long deadline = now + duration * 60_000L;

            ExamRoundEntity round = new ExamRoundEntity(teacherId, template, customTitle, targetClass, maxParticipants, assignedStudents, now, deadline);
            for (TemplateQuestionEntity link : template.getQuestions()) {
                round.addSnapshot(link.getQuestion(), link.getPosition());
            }
            em.persist(round);
            em.flush();
            return roundInfo(round);
        });
    }

    /**
     * Tự động chia sinh viên của lớp thành nhiều ca thi dựa trên số lượng người / ca thi.
     */
    public List<RoundSummary> splitExamRounds(long teacherId, SplitSessionDraft draft, long now) {
        if (draft.studentsPerRound() <= 0) throw new IllegalArgumentException("Số lượng người / ca thi phải lớn hơn 0.");

        return tx(em -> {
            ExamTemplateEntity template = em.createQuery("select distinct t from ExamTemplateEntity t join fetch t.subject left join fetch t.questions x left join fetch x.question where t.id=:id and t.teacherId=:teacher", ExamTemplateEntity.class)
                    .setParameter("id", draft.templateId()).setParameter("teacher", teacherId).getResultStream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đề thi."));
            if (template.getQuestions().isEmpty()) throw new IllegalStateException("Đề thi không có câu hỏi.");

            String targetClass = draft.targetClass() == null || draft.targetClass().isBlank() || "Tất cả".equalsIgnoreCase(draft.targetClass().strip()) ? null : draft.targetClass().strip();
            String studentJpql = "select s.code from StudentEntity s" + (targetClass != null ? " where s.className=:className" : "") + " order by s.code asc";
            TypedQuery<String> query = em.createQuery(studentJpql, String.class);
            if (targetClass != null) query.setParameter("className", targetClass);
            List<String> studentCodes = query.getResultList();

            if (studentCodes.isEmpty()) {
                throw new IllegalStateException("Không tìm thấy sinh viên nào thuộc lớp: " + (targetClass != null ? targetClass : "Tất cả"));
            }

            int perRound = draft.studentsPerRound();
            int totalStudents = studentCodes.size();
            int numRounds = (int) Math.ceil((double) totalStudents / perRound);
            int duration = draft.durationMinutes() > 0 ? draft.durationMinutes() : template.getDurationMinutes();
            long deadline = now + duration * 60_000L;

            List<RoundSummary> created = new ArrayList<>();
            for (int i = 0; i < numRounds; i++) {
                int fromIndex = i * perRound;
                int toIndex = Math.min(fromIndex + perRound, totalStudents);
                List<String> sub = studentCodes.subList(fromIndex, toIndex);
                String assigned = String.join(",", sub);

                String roundTitle = template.getTitle() + " - Ca " + (i + 1) + (targetClass != null ? " (" + targetClass + ")" : "");
                ExamRoundEntity round = new ExamRoundEntity(teacherId, template, roundTitle, targetClass, perRound, assigned, now, deadline);
                // Ca đầu tiên kích hoạt, các ca sau cũng kích hoạt hoặc chờ giảng viên mở
                round.setActive(true);

                for (TemplateQuestionEntity link : template.getQuestions()) {
                    round.addSnapshot(link.getQuestion(), link.getPosition());
                }
                em.persist(round);
                em.flush();
                created.add(new RoundSummary(round.getId(), round.getTitle(), round.getSubjectName(), round.getTargetClass(),
                        round.getMaxParticipants(), 0, round.getOpenedAt(), round.getDeadlineAt(), round.isActive()));
            }
            return created;
        });
    }

    public List<RoundSummary> listRounds(long teacherId) {
        return read(em -> {
            List<ExamRoundEntity> rounds = em.createQuery("select r from ExamRoundEntity r where r.teacherId=:teacher order by r.id desc", ExamRoundEntity.class)
                    .setParameter("teacher", teacherId).getResultList();
            List<RoundSummary> list = new ArrayList<>();
            for (ExamRoundEntity r : rounds) {
                long count = em.createQuery("select count(a) from AttemptEntity a where a.round.id=:roundId", Long.class)
                        .setParameter("roundId", r.getId()).getSingleResult();
                list.add(new RoundSummary(r.getId(), r.getTitle(), r.getSubjectName(), r.getTargetClass(),
                        r.getMaxParticipants(), count, r.getOpenedAt(), r.getDeadlineAt(), r.isActive()));
            }
            return list;
        });
    }

    public void activateRound(long teacherId, long roundId, int durationMinutes, long now) {
        tx(em -> {
            ExamRoundEntity round = em.find(ExamRoundEntity.class, roundId, LockModeType.PESSIMISTIC_WRITE);
            if (round == null || round.getTeacherId() != teacherId) throw new IllegalArgumentException("Không tìm thấy ca thi.");
            int duration = durationMinutes > 0 ? durationMinutes : (int) ((round.getDeadlineAt() - round.getOpenedAt()) / 60_000L);
            if (duration <= 0) duration = 45;
            round.setActive(true);
            round.setDeadlineAt(now + duration * 60_000L);
            return null;
        });
    }

    public void closeRound(long teacherId, long roundId) {
        tx(em -> {
            ExamRoundEntity round = em.find(ExamRoundEntity.class, roundId, LockModeType.PESSIMISTIC_WRITE);
            if (round != null && round.getTeacherId() == teacherId) {
                round.close();
            }
            return null;
        });
    }

    public Optional<RoundInfo> activeRound(long teacherId, long now) {
        return read(em -> em.createQuery("select r from ExamRoundEntity r where r.teacherId=:teacher and r.active=true and r.deadlineAt>:now order by r.id desc", ExamRoundEntity.class)
                .setParameter("teacher", teacherId).setParameter("now", now).setMaxResults(1).getResultStream().findFirst().map(ExamStore::roundInfo));
    }

    /**
     * Tìm ca thi đang mở phù hợp cho một sinh viên cụ thể:
     * - Xét theo ca có gán mã SV trực tiếp
     * - Xét theo lớp của sinh viên
     * - Xét giới hạn số lượng người / ca
     */
    public Optional<ExamRoundEntity> activeRoundForStudent(EntityManager em, long teacherId, String studentCode, long now) {
        StudentEntity student = em.find(StudentEntity.class, normalizeCode(studentCode));
        if (student == null) return Optional.empty();

        List<ExamRoundEntity> activeRounds = em.createQuery("select r from ExamRoundEntity r where r.teacherId=:teacher and r.active=true and r.deadlineAt>:now order by r.id desc", ExamRoundEntity.class)
                .setParameter("teacher", teacherId).setParameter("now", now).getResultList();

        for (ExamRoundEntity r : activeRounds) {
            // 1. Kiểm tra danh sách gán mã sinh viên cụ thể nếu có
            if (r.getAssignedStudents() != null && !r.getAssignedStudents().isBlank()) {
                List<String> assignedList = Arrays.stream(r.getAssignedStudents().split(","))
                        .map(String::trim).map(String::toUpperCase).toList();
                if (assignedList.contains(normalizeCode(studentCode))) {
                    return Optional.of(r);
                }
                continue; // Nếu ca này có danh sách chỉ định riêng mà SV không có trong đó thì bỏ qua
            }

            // 2. Kiểm tra lớp học nếu có
            if (r.getTargetClass() != null && !r.getTargetClass().isBlank()) {
                if (!r.getTargetClass().equalsIgnoreCase(student.getClassName())) {
                    continue; // Khác lớp -> bỏ qua
                }
            }

            // 3. Kiểm tra số lượng người / ca thi
            if (r.getMaxParticipants() != null && r.getMaxParticipants() > 0) {
                long currentAttempts = em.createQuery("select count(a) from AttemptEntity a where a.round.id=:roundId", Long.class)
                        .setParameter("roundId", r.getId()).getSingleResult();
                boolean alreadyJoined = !em.createQuery("select a.id from AttemptEntity a where a.round.id=:roundId and a.student.code=:code", Long.class)
                        .setParameter("roundId", r.getId()).setParameter("code", normalizeCode(studentCode)).getResultList().isEmpty();
                if (!alreadyJoined && currentAttempts >= r.getMaxParticipants()) {
                    continue; // Ca đã đủ số người -> bỏ qua tìm ca khác
                }
            }

            return Optional.of(r);
        }

        return Optional.empty();
    }

    public Optional<ExamOffer> offer(long teacherId, String studentCode, long now) {
        return read(em -> {
            Optional<ExamRoundEntity> active = activeRoundForStudent(em, teacherId, studentCode, now);
            if (active.isEmpty()) return Optional.empty();

            ExamRoundEntity round = active.get();
            Optional<AttemptEntity> attempt = findAttempt(em, round.getId(), studentCode, null);
            if (attempt.isPresent() && attempt.get().getSubmittedAt() != null) return Optional.empty();
            return Optional.of(new ExamOffer(
                    round.getId(),
                    round.getTitle(),
                    round.getSubjectName(),
                    round.getTargetClass(),
                    round.getMaxParticipants(),
                    round.getDeadlineAt(),
                    attempt.isPresent()
            ));
        });
    }

    public ExamPaper joinRound(long teacherId, String studentCode, long roundId, long now) {
        return tx(em -> {
            ExamRoundEntity round = requireRound(em, teacherId, roundId, LockModeType.PESSIMISTIC_READ);
            if (!round.isActive() || now >= round.getDeadlineAt()) throw new IllegalStateException("Ca thi đã hết giờ hoặc đã đóng.");

            StudentEntity student = requireStudent(em, studentCode);

            // Kiểm tra danh sách chỉ định
            if (round.getAssignedStudents() != null && !round.getAssignedStudents().isBlank()) {
                List<String> assignedList = Arrays.stream(round.getAssignedStudents().split(","))
                        .map(String::trim).map(String::toUpperCase).toList();
                if (!assignedList.contains(normalizeCode(studentCode))) {
                    throw new IllegalStateException("Bạn không nằm trong danh sách của ca thi này.");
                }
            }

            // Kiểm tra lớp áp dụng
            if (round.getTargetClass() != null && !round.getTargetClass().isBlank()) {
                if (!round.getTargetClass().equalsIgnoreCase(student.getClassName())) {
                    throw new IllegalStateException("Ca thi này dành riêng cho lớp: " + round.getTargetClass());
                }
            }

            Optional<AttemptEntity> existing = findAttempt(em, roundId, studentCode, LockModeType.PESSIMISTIC_WRITE);
            if (existing.isPresent() && existing.get().getSubmittedAt() != null) throw new IllegalStateException("Bạn đã hoàn thành ca thi này.");

            // Kiểm tra số lượng người / ca
            if (existing.isEmpty() && round.getMaxParticipants() != null && round.getMaxParticipants() > 0) {
                long currentCount = em.createQuery("select count(a) from AttemptEntity a where a.round.id=:roundId", Long.class)
                        .setParameter("roundId", round.getId()).getSingleResult();
                if (currentCount >= round.getMaxParticipants()) {
                    throw new IllegalStateException("Ca thi đã đủ số lượng sinh viên (" + round.getMaxParticipants() + " người). Vui lòng đợi ca sau.");
                }
            }

            if (existing.isEmpty()) {
                em.persist(new AttemptEntity(round, student, now));
            }
            return paper(round);
        });
    }

    public boolean roundAccepts(long teacherId, long roundId, long now) {
        return read(em -> em.createQuery("select count(r) from ExamRoundEntity r where r.id=:id and r.teacherId=:teacher and r.active=true and r.deadlineAt>:now", Long.class)
                .setParameter("id", roundId).setParameter("teacher", teacherId).setParameter("now", now).getSingleResult() == 1);
    }

    public Grade submit(long teacherId, String studentCode, AnswerBundle bundle, long receivedAt) {
        if (bundle == null) throw new IllegalArgumentException("Thiếu bài làm.");
        Map<Long, String> answers = bundle.answers() == null ? Map.of() : bundle.answers();
        return tx(em -> {
            AttemptEntity attempt = findAttempt(em, bundle.roundId(), studentCode, LockModeType.PESSIMISTIC_WRITE)
                    .orElseThrow(() -> new IllegalStateException("Bạn chưa tham gia ca thi này."));
            ExamRoundEntity round = attempt.getRound(); round.getQuestions().size();
            if (round.getTeacherId() != teacherId) throw new IllegalArgumentException("Ca thi không thuộc giảng viên hiện tại.");
            if (attempt.getSubmittedAt() != null) return grade(attempt, "Bài đã được chấm trước đó.");
            if (receivedAt > round.getDeadlineAt()) throw new IllegalStateException("Bài nộp đến sau hạn.");
            Set<Long> validIds = round.getQuestions().stream().map(RoundQuestionEntity::getId).collect(Collectors.toSet());
            if (!validIds.containsAll(answers.keySet())) throw new IllegalArgumentException("Bài làm chứa mã câu hỏi không hợp lệ.");
            int right = 0;
            for (RoundQuestionEntity question : round.getQuestions()) {
                String answer = normalizedAnswer(answers.get(question.getId())); boolean correct = question.getCorrectOption().equalsIgnoreCase(answer);
                if (correct) right++; attempt.addAnswer(question, answer, correct);
            }
            int total = round.getQuestions().size(); double score = total == 0 ? 0 : Math.round(right * 1000.0 / total) / 100.0;
            attempt.complete(Math.min(receivedAt, round.getDeadlineAt()), score, right, total); em.flush();
            return grade(attempt, "Đã nộp và chấm bài.");
        });
    }

    public List<PendingAttempt> expiredAttempts(long now) {
        return read(em -> em.createQuery("select a from AttemptEntity a join fetch a.round join fetch a.student where a.submittedAt is null and a.round.deadlineAt<=:now", AttemptEntity.class)
                .setParameter("now", now).getResultList().stream().map(a -> new PendingAttempt(a.getRound().getTeacherId(), a.getRound().getId(), a.getStudent().getCode(), a.getRound().getDeadlineAt())).toList());
    }

    public void closeExpiredRounds(long now) {
        tx(em -> {
            em.createQuery("update ExamRoundEntity r set r.active=false where r.deadlineAt<=:now and r.active=true").setParameter("now", now).executeUpdate();
            return null;
        });
    }

    public List<HistoryItem> history(long teacherId, String studentCode) {
        return read(em -> em.createQuery("select a from AttemptEntity a join fetch a.round where a.round.teacherId=:teacher and a.student.code=:code and a.submittedAt is not null order by a.submittedAt desc", AttemptEntity.class)
                .setParameter("teacher", teacherId).setParameter("code", studentCode).getResultList().stream()
                .map(a -> new HistoryItem(a.getRound().getId(), a.getRound().getTitle(), a.getRound().getSubjectName(), a.getScore(), a.getCorrectCount(), a.getTotalCount(), a.getSubmittedAt())).toList());
    }

    public List<ResultView> results(long teacherId) {
        return results(teacherId, null, null);
    }

    public List<ResultView> results(long teacherId, Long roundId, String className) {
        return read(em -> {
            String normalizedClass = className == null ? null : className.strip();
            boolean filterByClass = normalizedClass != null
                    && !normalizedClass.isBlank()
                    && !"Tất cả".equalsIgnoreCase(normalizedClass)
                    && !"Tất cả lớp".equalsIgnoreCase(normalizedClass);
            StringBuilder jpql = new StringBuilder("select a from AttemptEntity a join fetch a.round join fetch a.student where a.round.teacherId=:teacher and a.submittedAt is not null");
            if (roundId != null && roundId > 0) {
                jpql.append(" and a.round.id=:roundId");
            }
            if (filterByClass) {
                jpql.append(" and a.student.className=:className");
            }
            jpql.append(" order by a.submittedAt desc");

            TypedQuery<AttemptEntity> query = em.createQuery(jpql.toString(), AttemptEntity.class)
                    .setParameter("teacher", teacherId);
            if (roundId != null && roundId > 0) {
                query.setParameter("roundId", roundId);
            }
            if (filterByClass) {
                query.setParameter("className", normalizedClass);
            }

            return query.getResultList().stream().map(a -> new ResultView(
                    a.getRound().getId(),
                    a.getRound().getTitle(),
                    a.getStudent().getCode(),
                    a.getStudent().getFullName(),
                    a.getStudent().getClassName(),
                    a.getScore(),
                    a.getCorrectCount(),
                    a.getTotalCount(),
                    a.getSubmittedAt()
            )).toList();
        });
    }

    // --- BÀI TẬP ---

    public long createAssignment(long teacherId, AssignmentDraft draft, long now) {
        validateAssignmentDraft(draft, now);
        return tx(em -> { AssignmentEntity assignment = new AssignmentEntity(teacherId, draft.title().strip(), text(draft.description()), now, draft.dueAtEpochMillis(), draft.fileName(), draft.fileData()); em.persist(assignment); em.flush(); return assignment.getId(); });
    }

    public List<AssignmentItem> assignmentsForTeacher(long teacherId) { return listAssignments(teacherId, null); }
    public List<AssignmentItem> assignmentsForStudent(long teacherId, String studentCode) { return listAssignments(teacherId, studentCode); }

    private List<AssignmentItem> listAssignments(long teacherId, String studentCode) {
        return read(em -> em.createQuery("select a from AssignmentEntity a where a.teacherId=:teacher order by a.id desc", AssignmentEntity.class)
                .setParameter("teacher", teacherId).getResultList().stream().map(assignment -> {
                    long count = em.createQuery("select count(s) from AssignmentSubmissionEntity s where s.assignment.id=:id", Long.class).setParameter("id", assignment.getId()).getSingleResult();
                    SubmissionView mine = studentCode == null ? null : findSubmission(em, assignment.getId(), studentCode, null).map(ExamStore::submission).orElse(null);
                    return new AssignmentItem(assignment.getId(), assignment.getTitle(), assignment.getDescription(), assignment.getCreatedAt(), assignment.getDueAt(), assignment.getFileName(), bytes(assignment.getFileData()), count, mine);
                }).toList());
    }

    public SubmissionView submitAssignment(long teacherId, String studentCode, AssignmentTurnIn turnIn, long receivedAt) {
        if (turnIn == null) throw new IllegalArgumentException("Thiếu bài nộp.");
        String content = text(turnIn.content()); validateFile(turnIn.fileName(), turnIn.fileData());
        return tx(em -> {
            AssignmentEntity assignment = em.find(AssignmentEntity.class, turnIn.assignmentId(), LockModeType.PESSIMISTIC_WRITE);
            if (assignment == null || assignment.getTeacherId() != teacherId) throw new IllegalArgumentException("Không tìm thấy bài tập.");
            if (assignment.getDueAt() != null && receivedAt >= assignment.getDueAt()) throw new IllegalStateException("Bài tập đã hết hạn.");
            StudentEntity student = requireStudent(em, studentCode);
            AssignmentSubmissionEntity submission = findSubmission(em, assignment.getId(), studentCode, LockModeType.PESSIMISTIC_WRITE).orElse(null);
            if (submission == null) { submission = new AssignmentSubmissionEntity(assignment, student); em.persist(submission); }
            byte[] data = turnIn.fileData(); String name = turnIn.fileName();
            if (data == null && turnIn.keepExistingFile()) { data = submission.getFileData(); name = submission.getFileName(); }
            if (content.isBlank() && data == null) throw new IllegalArgumentException("Nhập nội dung hoặc chọn tệp bài làm.");
            submission.update(content, name, data, receivedAt); em.flush(); return submission(submission);
        });
    }

    public List<SubmissionView> assignmentSubmissions(long teacherId, long assignmentId) {
        return read(em -> em.createQuery("select s from AssignmentSubmissionEntity s join fetch s.assignment join fetch s.student where s.assignment.teacherId=:teacher and s.assignment.id=:id order by s.submittedAt desc", AssignmentSubmissionEntity.class)
                .setParameter("teacher", teacherId).setParameter("id", assignmentId).getResultList().stream().map(ExamStore::submission).toList());
    }

    public FilePayload assignmentFile(long teacherId, String studentCode, FileRequest request) {
        return read(em -> {
            if (request.submission()) {
                AssignmentSubmissionEntity submission = findSubmission(em, request.assignmentId(), studentCode, null).orElseThrow(() -> new IllegalArgumentException("Bạn chưa nộp bài tập này."));
                if (submission.getAssignment().getTeacherId() != teacherId || submission.getFileData() == null) throw new IllegalArgumentException("Không có tệp đính kèm.");
                return new FilePayload(request.assignmentId(), true, submission.getFileName(), submission.getFileData());
            }
            AssignmentEntity assignment = em.find(AssignmentEntity.class, request.assignmentId());
            if (assignment == null || assignment.getTeacherId() != teacherId || assignment.getFileData() == null) throw new IllegalArgumentException("Không có tệp đính kèm.");
            return new FilePayload(assignment.getId(), false, assignment.getFileName(), assignment.getFileData());
        });
    }

    public FilePayload teacherSubmissionFile(long teacherId, long submissionId) {
        return read(em -> {
            AssignmentSubmissionEntity submission = em.find(AssignmentSubmissionEntity.class, submissionId);
            if (submission == null || submission.getAssignment().getTeacherId() != teacherId || submission.getFileData() == null) throw new IllegalArgumentException("Bài nộp không có tệp.");
            return new FilePayload(submission.getAssignment().getId(), true, submission.getFileName(), submission.getFileData());
        });
    }

    public void setLocked(String code, boolean locked, String reason) {
        if (locked && (reason == null || reason.isBlank() || reason.strip().length() > 500)) throw new IllegalArgumentException("Lý do khóa phải có từ 1 đến 500 ký tự.");
        tx(em -> { StudentEntity student = requireStudent(em, normalizeCode(code)); student.setLocked(locked, locked ? reason.strip() : null); return null; });
    }
    public boolean isLocked(String code) { return read(em -> { StudentEntity student = em.find(StudentEntity.class, code); return student != null && student.isLocked(); }); }
    public List<LockedView> lockedStudents() { return read(em -> em.createQuery("select s from StudentEntity s where s.locked=true order by s.code", StudentEntity.class).getResultList().stream().map(s -> new LockedView(s.getCode(), s.getFullName(), safe(s.getLockReason()))).toList()); }

    private static Teacher teacher(TeacherEntity entity) { return new Teacher(entity.getId(), entity.getUsername(), entity.getFullName()); }
    private static BankQuestion bankQuestion(BankQuestionEntity q) { return new BankQuestion(q.getId(), q.getSubject().getId(), q.getSubject().getName(), q.getDifficulty(), q.getPrompt(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(), q.getCorrectOption()); }
    private static RoundInfo roundInfo(ExamRoundEntity r) {
        return new RoundInfo(r.getId(), r.getTeacherId(), r.getTitle(), r.getSubjectName(), r.getTargetClass(), r.getMaxParticipants(), r.getOpenedAt(), r.getDeadlineAt());
    }
    private static ExamPaper paper(ExamRoundEntity round) { return new ExamPaper(round.getId(), round.getTitle(), round.getSubjectName(), round.getDeadlineAt(), round.getQuestions().stream().map(q -> new PublicQuestion(q.getId(), q.getPrompt(), List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()))).toList()); }
    private static Grade grade(AttemptEntity a, String message) { return new Grade(a.getRound().getId(), a.getScore(), a.getCorrectCount(), a.getTotalCount(), message); }
    private static SubmissionView submission(AssignmentSubmissionEntity s) { return new SubmissionView(s.getId(), s.getStudent().getCode(), s.getStudent().getFullName(), s.getContent(), s.getFileName(), bytes(s.getFileData()), s.getSubmittedAt()); }

    private static SubjectEntity requireSubject(EntityManager em, long teacherId, long id) { SubjectEntity subject = em.find(SubjectEntity.class, id); if (subject == null || subject.getTeacherId() != teacherId) throw new IllegalArgumentException("Không tìm thấy môn học."); return subject; }
    private static StudentEntity requireStudent(EntityManager em, String code) { StudentEntity student = em.find(StudentEntity.class, normalizeCode(code)); if (student == null) throw new IllegalArgumentException("Không tìm thấy sinh viên."); return student; }
    private static ExamRoundEntity requireRound(EntityManager em, long teacherId, long id, LockModeType lock) { ExamRoundEntity round = lock == null ? em.find(ExamRoundEntity.class, id) : em.find(ExamRoundEntity.class, id, lock); if (round == null || round.getTeacherId() != teacherId) throw new IllegalArgumentException("Không tìm thấy ca thi."); round.getQuestions().size(); return round; }
    private static Optional<AttemptEntity> findAttempt(EntityManager em, long roundId, String code, LockModeType lock) { TypedQuery<AttemptEntity> query = em.createQuery("select a from AttemptEntity a join fetch a.round join fetch a.student where a.round.id=:round and a.student.code=:code", AttemptEntity.class).setParameter("round", roundId).setParameter("code", normalizeCode(code)); if (lock != null) query.setLockMode(lock); return query.getResultStream().findFirst(); }
    private static Optional<AssignmentSubmissionEntity> findSubmission(EntityManager em, long assignmentId, String code, LockModeType lock) { TypedQuery<AssignmentSubmissionEntity> query = em.createQuery("select s from AssignmentSubmissionEntity s join fetch s.assignment join fetch s.student where s.assignment.id=:assignment and s.student.code=:code", AssignmentSubmissionEntity.class).setParameter("assignment", assignmentId).setParameter("code", normalizeCode(code)); if (lock != null) query.setLockMode(lock); return query.getResultStream().findFirst(); }

    private static void validateQuestion(QuestionDraft draft) { if (draft == null || draft.difficulty() < 1 || draft.difficulty() > 6) throw new IllegalArgumentException("Độ khó phải từ 1 đến 6."); for (String value : List.of(draft.prompt(), draft.optionA(), draft.optionB(), draft.optionC(), draft.optionD())) if (value == null || value.isBlank() || value.strip().length() > 4000) throw new IllegalArgumentException("Nội dung câu hỏi/lựa chọn không hợp lệ."); if (draft.correctOption() == null || !draft.correctOption().strip().toUpperCase(Locale.ROOT).matches("[ABCD]")) throw new IllegalArgumentException("Đáp án đúng phải là A, B, C hoặc D."); }
    private static void validateTemplate(TemplateDraft draft) { if (draft == null || draft.title() == null || draft.title().isBlank() || draft.title().strip().length() > 200) throw new IllegalArgumentException("Tên đề không hợp lệ."); if (draft.durationMinutes() < 1 || draft.durationMinutes() > 360) throw new IllegalArgumentException("Thời gian phải từ 1 đến 360 phút."); if (draft.questionIds() == null || draft.questionIds().isEmpty() || draft.questionIds().size() > 200 || draft.questionIds().stream().distinct().count() != draft.questionIds().size()) throw new IllegalArgumentException("Chọn từ 1 đến 200 câu hỏi không trùng nhau."); }
    private static void validateAssignmentDraft(AssignmentDraft draft, long now) { if (draft == null || draft.title() == null || draft.title().isBlank() || draft.title().strip().length() > 200) throw new IllegalArgumentException("Tên bài tập không hợp lệ."); if (text(draft.description()).isBlank() && draft.fileData() == null) throw new IllegalArgumentException("Nhập yêu cầu hoặc đính kèm tệp."); if (draft.dueAtEpochMillis() != null && draft.dueAtEpochMillis() <= now) throw new IllegalArgumentException("Hạn nộp phải ở tương lai."); validateFile(draft.fileName(), draft.fileData()); }
    private static void validateFile(String name, byte[] data) { if (data == null) { if (name != null && !name.isBlank()) throw new IllegalArgumentException("Thiếu dữ liệu tệp."); return; } if (data.length == 0 || data.length > Domain.MAX_FILE_BYTES) throw new IllegalArgumentException("Tệp phải có dữ liệu và không vượt quá 5 MB."); if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\")) throw new IllegalArgumentException("Tên tệp không hợp lệ."); }
    private static String text(String value) { if (value == null) return ""; if (value.length() > Domain.MAX_TEXT_CHARS) throw new IllegalArgumentException("Nội dung không vượt quá 20.000 ký tự."); return value.strip(); }
    private static String normalizedAnswer(String answer) { if (answer == null) return ""; String value = answer.strip().toUpperCase(Locale.ROOT); return value.matches("[ABCD]") ? value : ""; }
    private static String normalizeCode(String code) { return code == null ? "" : code.strip().toUpperCase(Locale.ROOT); }
    private static long bytes(byte[] data) { return data == null ? 0 : data.length; }
    private static String safe(String value) { return value == null || value.isBlank() ? "Không có lý do" : value; }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }

    private <T> T read(Function<EntityManager, T> work) { EntityManager em = factory.createEntityManager(); try { return work.apply(em); } finally { em.close(); } }
    private <T> T tx(Function<EntityManager, T> work) { EntityManager em = factory.createEntityManager(); EntityTransaction transaction = em.getTransaction(); try { transaction.begin(); T result = work.apply(em); transaction.commit(); return result; } catch (RuntimeException failure) { if (transaction.isActive()) transaction.rollback(); throw failure; } finally { em.close(); } }
    @Override public void close() { if (factory.isOpen()) factory.close(); }
}
