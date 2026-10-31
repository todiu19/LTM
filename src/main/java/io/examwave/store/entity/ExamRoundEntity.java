package io.examwave.store.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ew_exam_rounds")
public class ExamRoundEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private long teacherId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ExamTemplateEntity template;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "subject_name", nullable = false, length = 120)
    private String subjectName;
    @Column(name = "target_class", length = 50)
    private String targetClass;
    @Column(name = "max_participants")
    private Integer maxParticipants;
    @Column(name = "assigned_students", length = 8000)
    private String assignedStudents;
    @Column(name = "opened_at", nullable = false)
    private long openedAt;
    @Column(name = "deadline_at", nullable = false)
    private long deadlineAt;
    @Column(nullable = false)
    private boolean active;
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RoundQuestionEntity> questions = new ArrayList<>();

    protected ExamRoundEntity() {}
    public ExamRoundEntity(long teacherId, ExamTemplateEntity template, long openedAt, long deadlineAt) {
        this(teacherId, template, template.getTitle(), null, null, null, openedAt, deadlineAt);
    }
    public ExamRoundEntity(long teacherId, ExamTemplateEntity template, String customTitle,
                           String targetClass, Integer maxParticipants, String assignedStudents,
                           long openedAt, long deadlineAt) {
        this.teacherId = teacherId;
        this.template = template;
        this.title = customTitle == null || customTitle.isBlank() ? template.getTitle() : customTitle.strip();
        this.subjectName = template.getSubject().getName();
        this.targetClass = targetClass == null || targetClass.isBlank() || "Tất cả".equalsIgnoreCase(targetClass.strip()) ? null : targetClass.strip();
        this.maxParticipants = maxParticipants != null && maxParticipants > 0 ? maxParticipants : null;
        this.assignedStudents = assignedStudents == null || assignedStudents.isBlank() ? null : assignedStudents.strip();
        this.openedAt = openedAt;
        this.deadlineAt = deadlineAt;
        this.active = true;
    }

    public void addSnapshot(BankQuestionEntity source, int position) {
        questions.add(new RoundQuestionEntity(this, source, position));
    }

    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public ExamTemplateEntity getTemplate() { return template; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubjectName() { return subjectName; }
    public String getTargetClass() { return targetClass; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public String getAssignedStudents() { return assignedStudents; }
    public long getOpenedAt() { return openedAt; }
    public long getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(long deadlineAt) { this.deadlineAt = deadlineAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public void close() { active = false; }
    public List<RoundQuestionEntity> getQuestions() { return questions; }
}
