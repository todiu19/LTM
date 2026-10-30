package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_template_questions", uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "question_id"}))
public class TemplateQuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ExamTemplateEntity template;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private BankQuestionEntity question;
    @Column(nullable = false)
    private int position;

    protected TemplateQuestionEntity() {}
    TemplateQuestionEntity(ExamTemplateEntity template, BankQuestionEntity question, int position) {
        this.template = template; this.question = question; this.position = position;
    }
    public BankQuestionEntity getQuestion() { return question; }
    public int getPosition() { return position; }
}
