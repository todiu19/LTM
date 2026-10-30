package io.examwave.student;

/** Plain main class keeps packaged JavaFX startup independent from module-path launcher detection. */
public final class StudentLauncher {
    private StudentLauncher() {}
    public static void main(String[] args) { StudentApp.main(args); }
}
