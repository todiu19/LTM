package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_classes", uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "name"}))
public class ClassEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private long teacherId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected ClassEntity() {}

    public ClassEntity(long teacherId, String name, long createdAt) {
        this.teacherId = teacherId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getCreatedAt() { return createdAt; }
}

