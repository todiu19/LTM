package io.examwave;

import io.examwave.store.ExamStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSmokeTest {
    @Test void hibernateCreatesSchemaAndSeedsStudentsInDockerMySql() {
        Assumptions.assumeTrue(Boolean.getBoolean("examwave.mysqlTest"));
        try (ExamStore store = new ExamStore()) {
            assertTrue(store.authenticateStudent("B21DCCN001", "2003-05-12").isPresent());
            var teacher = store.authenticateTeacher("giangvien.demo", "examwave123").orElseThrow();
            assertTrue(store.listSubjects(teacher.id()).size() >= 2);
            assertTrue(store.listTemplates(teacher.id()).size() >= 2);
            assertTrue(store.assignmentsForTeacher(teacher.id()).size() >= 2);
        }
    }
}
