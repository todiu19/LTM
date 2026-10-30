package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_subjects", uniqueConstraints = @UniqueConstraint(columnNames = {"teacher_id", "normalized_name"}))
public class SubjectEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "teacher_id", nullable = false)
    private long teacherId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 140)
    private String normalizedName;

    protected SubjectEntity() {}
    public SubjectEntity(long teacherId, String name, String normalizedName) {
        this.teacherId = teacherId; this.name = name; this.normalizedName = normalizedName;
    }
    public Long getId() { return id; }
    public long getTeacherId() { return teacherId; }
    public String getName() { return name; }
}
