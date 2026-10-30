package io.examwave.store.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ew_attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "student_code"}))
public class AttemptEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private ExamRoundEntity round;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_code", nullable = false)
    private StudentEntity student;
    @Column(name = "joined_at", nullable = false) private long joinedAt;
    @Column(name = "submitted_at") private Long submittedAt;
    private Double score;
    @Column(name = "correct_count") private Integer correctCount;
    @Column(name = "total_count") private Integer totalCount;
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnswerRecordEntity> answers = new ArrayList<>();

    protected AttemptEntity() {}
    public AttemptEntity(ExamRoundEntity round, StudentEntity student, long joinedAt) {
        this.round = round; this.student = student; this.joinedAt = joinedAt;
    }
    public void complete(long submittedAt, double score, int correct, int total) {
        this.submittedAt = submittedAt; this.score = score; this.correctCount = correct; this.totalCount = total;
    }
    public void addAnswer(RoundQuestionEntity question, String answer, boolean correct) {
        answers.add(new AnswerRecordEntity(this, question, answer, correct));
    }
    public Long getId() { return id; }
    public ExamRoundEntity getRound() { return round; }
    public StudentEntity getStudent() { return student; }
    public Long getSubmittedAt() { return submittedAt; }
    public double getScore() { return score == null ? 0 : score; }
    public int getCorrectCount() { return correctCount == null ? 0 : correctCount; }
    public int getTotalCount() { return totalCount == null ? 0 : totalCount; }
}
