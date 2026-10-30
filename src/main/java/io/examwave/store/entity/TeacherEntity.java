package io.examwave.store.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ew_teachers")
public class TeacherEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(name = "password_hash", nullable = false, length = 500)
    private String passwordHash;

    protected TeacherEntity() {}
    public TeacherEntity(String username, String fullName, String passwordHash) {
        this.username = username; this.fullName = fullName; this.passwordHash = passwordHash;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
}
