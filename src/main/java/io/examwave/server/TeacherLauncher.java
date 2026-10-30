package io.examwave.server;

/** Plain main class keeps packaged JavaFX startup independent from module-path launcher detection. */
public final class TeacherLauncher {
    private TeacherLauncher() {}
    public static void main(String[] args) { TeacherApp.main(args); }
}
