package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_bank_questions")
public class BankQuestionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private long teacherId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private SubjectEntity subject;
    @Column(nullable = false)
    private int difficulty;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;
    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    private String optionA;
    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    private String optionB;
    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT")
    private String optionC;
    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT")
    private String optionD;
    @Column(name = "correct_option", nullable = false, length = 1)
    private String correctOption;

    protected BankQuestionEntity() {}
    public BankQuestionEntity(long teacherId, SubjectEntity subject, int difficulty, String prompt,
                              String optionA, String optionB, String optionC, String optionD, String correctOption) {
        this.teacherId = teacherId; this.subject = subject; this.difficulty = difficulty; this.prompt = prompt;
        this.optionA = optionA; this.optionB = optionB; this.optionC = optionC; this.optionD = optionD;
        this.correctOption = correctOption;
    }
    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public SubjectEntity getSubject() { return subject; }
    public int getDifficulty() { return difficulty; }
    public String getPrompt() { return prompt; }
    public String getOptionA() { return optionA; }
    public String getOptionB() { return optionB; }
    public String getOptionC() { return optionC; }
    public String getOptionD() { return optionD; }
    public String getCorrectOption() { return correctOption; }
}
