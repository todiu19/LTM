package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_round_questions")
public class RoundQuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private ExamRoundEntity round;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false, columnDefinition = "TEXT") private String prompt;
    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT") private String optionA;
    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT") private String optionB;
    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT") private String optionC;
    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT") private String optionD;
    @Column(name = "correct_option", nullable = false, length = 1) private String correctOption;

    protected RoundQuestionEntity() {}
    RoundQuestionEntity(ExamRoundEntity round, BankQuestionEntity source, int position) {
        this.round = round; this.position = position; this.prompt = source.getPrompt();
        this.optionA = source.getOptionA(); this.optionB = source.getOptionB(); this.optionC = source.getOptionC();
        this.optionD = source.getOptionD(); this.correctOption = source.getCorrectOption();
    }
    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public String getCorrectOption() { return correctOption; }
}
