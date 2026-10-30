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
    AnswerRecordEntity(AttemptEntity attempt, RoundQuestionEntity question, String answer, boolean correct) {
        this.attempt = attempt; this.question = question; this.answer = answer; this.correct = correct;
    }
}
