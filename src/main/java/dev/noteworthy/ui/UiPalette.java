package dev.noteworthy.ui;

import dev.noteworthy.model.NoteStatus;

import java.awt.Color;

public final class UiPalette {
    public static final Color BACKGROUND = new Color(21, 31, 48);
    public static final Color SURFACE = new Color(29, 43, 63);
    public static final Color RAISED = new Color(38, 55, 77);
    public static final Color BORDER = new Color(59, 77, 99);
    public static final Color TEXT = new Color(232, 239, 246);
    public static final Color MUTED = new Color(158, 177, 197);
    public static final Color ACCENT = new Color(112, 210, 198);
    public static final Color FOCUS = new Color(137, 207, 240);
    public static final Color TODO = new Color(225, 232, 239);
    public static final Color IN_PROGRESS = new Color(123, 190, 255);
    public static final Color DONE = new Color(133, 220, 171);
    public static final Color CANCELLED = new Color(255, 143, 151);
    public static final Color WARNING = new Color(245, 196, 119);
    public static final Color SUCCESS = new Color(76, 175, 80);

    private UiPalette() { }

    public static Color status(NoteStatus status) {
        return switch (status) {
            case TODO -> TODO;
            case IN_PROGRESS -> IN_PROGRESS;
            case DONE -> DONE;
            case CANCELLED -> CANCELLED;
        };
    }
}