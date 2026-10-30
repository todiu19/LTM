package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_assignment_submissions", uniqueConstraints = @UniqueConstraint(columnNames = {"assignment_id", "student_code"}))
public class AssignmentSubmissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private AssignmentEntity assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_code", nullable = false)
    private StudentEntity student;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "file_name", length = 255) private String fileName;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "file_data", columnDefinition = "LONGBLOB") private byte[] fileData;
    @Column(name = "submitted_at", nullable = false) private long submittedAt;

    protected AssignmentSubmissionEntity() {}
    public AssignmentSubmissionEntity(AssignmentEntity assignment, StudentEntity student) {
        this.assignment = assignment; this.student = student; this.content = "";
    }
    public void update(String content, String fileName, byte[] fileData, long submittedAt) {
        this.content = content; this.fileName = fileName; this.fileData = fileData; this.submittedAt = submittedAt;
    }
    public Long getId() { return id; }
    public AssignmentEntity getAssignment() { return assignment; }
    public StudentEntity getStudent() { return student; }
    public String getContent() { return content; }
    public String getFileName() { return fileName; }
    public byte[] getFileData() { return fileData; }
    public long getSubmittedAt() { return submittedAt; }
}
