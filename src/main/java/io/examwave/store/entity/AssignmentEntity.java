package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_assignments")
public class AssignmentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false) private long teacherId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "created_at", nullable = false) private long createdAt;
    @Column(name = "due_at") private Long dueAt;
    @Column(name = "file_name", length = 255) private String fileName;
    @Lob @Basic(fetch = FetchType.LAZY) @Column(name = "file_data", columnDefinition = "LONGBLOB") private byte[] fileData;

    protected AssignmentEntity() {}
    public AssignmentEntity(long teacherId, String title, String description, long createdAt, Long dueAt, String fileName, byte[] fileData) {
        this.teacherId = teacherId; this.title = title; this.description = description; this.createdAt = createdAt;
        this.dueAt = dueAt; this.fileName = fileName; this.fileData = fileData;
    }
    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getCreatedAt() { return createdAt; }
    public Long getDueAt() { return dueAt; }
    public String getFileName() { return fileName; }
    public byte[] getFileData() { return fileData; }
}
