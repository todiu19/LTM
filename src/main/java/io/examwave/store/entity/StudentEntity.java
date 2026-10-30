package io.examwave.store.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ew_students")
public class StudentEntity {
    @Id @Column(length = 30)
    private String code;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;
    @Column(nullable = false)
    private boolean locked;
    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    protected StudentEntity() {}
    public StudentEntity(String code, String fullName, LocalDate birthDate) {
        this.code = code; this.fullName = fullName; this.birthDate = birthDate;
    }
    public String getCode() { return code; }
    public String getFullName() { return fullName; }
    public LocalDate getBirthDate() { return birthDate; }
    public boolean isLocked() { return locked; }
    public String getLockReason() { return lockReason; }
    public void setLocked(boolean value, String reason) { locked = value; lockReason = reason; }
}
