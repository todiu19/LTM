package io.examwave.store.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ew_exam_templates")
public class ExamTemplateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private long teacherId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private SubjectEntity subject;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TemplateQuestionEntity> questions = new ArrayList<>();

    protected ExamTemplateEntity() {}
    public ExamTemplateEntity(long teacherId, SubjectEntity subject, String title, int durationMinutes) {
        this.teacherId = teacherId; this.subject = subject; this.title = title; this.durationMinutes = durationMinutes;
    }
    public void addQuestion(BankQuestionEntity question, int position) { questions.add(new TemplateQuestionEntity(this, question, position)); }
    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public SubjectEntity getSubject() { return subject; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public List<TemplateQuestionEntity> getQuestions() { return questions; }
}
