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
        this.teacherId = teacherId; this.template = template; this.title = template.getTitle();
        this.subjectName = template.getSubject().getName(); this.openedAt = openedAt; this.deadlineAt = deadlineAt; this.active = true;
    }
    public void addSnapshot(BankQuestionEntity source, int position) { questions.add(new RoundQuestionEntity(this, source, position)); }
    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public String getTitle() { return title; }
    public String getSubjectName() { return subjectName; }
    public long getOpenedAt() { return openedAt; }
    public long getDeadlineAt() { return deadlineAt; }
    public boolean isActive() { return active; }
    public void close() { active = false; }
    public List<RoundQuestionEntity> getQuestions() { return questions; }
}
