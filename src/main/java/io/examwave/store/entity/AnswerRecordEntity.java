package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_answer_records", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
public class AnswerRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AttemptEntity attempt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private RoundQuestionEntity question;
    @Column(length = 10) private String answer;
    @Column(nullable = false) private boolean correct;

    protected AnswerRecordEntity() {}
    public AnswerRecordEntity(AttemptEntity attempt, RoundQuestionEntity question, String answer, boolean correct) {
        this.attempt = attempt; this.question = question; this.answer = answer; this.correct = correct;
    }
    public Long getId() { return id; }
    public AttemptEntity getAttempt() { return attempt; }
    public RoundQuestionEntity getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public boolean isCorrect() { return correct; }
}
