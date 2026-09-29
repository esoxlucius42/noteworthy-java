package dev.noteworthy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GroupColor {
    BLUE("blue", "Blue", "#244B78", "#C4DCF8"),
    RED("red", "Red", "#7A252C", "#F2BCC0"),
    GREEN("green", "Green", "#245C3A", "#BFE7C9"),
    MAGENTA("magenta", "Magenta", "#6B2767", "#EBC2E7"),
    BROWN("brown", "Brown", "#694326", "#E9C6A3");

    private final String value;
    private final String label;
    private final String inactiveHex;
    private final String selectedHex;

    GroupColor(String value, String label, String inactiveHex, String selectedHex) {
        this.value = value;
        this.label = label;
        this.inactiveHex = inactiveHex;
        this.selectedHex = selectedHex;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public String inactiveHex() {
        return inactiveHex;
    }

    public String selectedHex() {
        return selectedHex;
    }

    @JsonCreator
    public static GroupColor fromValue(String value) {
        for (GroupColor color : values()) {
            if (color.value.equals(value)) return color;
        }
        GroupColor previousTheme = switch (value) {
            case "slate", "lagoon", "azure", "indigo", "graphite" -> BLUE;
            case "pine" -> GREEN;
            case "plum", "rose" -> MAGENTA;
            case "terracotta", "olive" -> BROWN;
            default -> null;
        };
        if (previousTheme != null) return previousTheme;
        throw new IllegalArgumentException("Unknown group color: " + value);
    }
}