package dev.noteworthy.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NoteStatus {
    TODO("todo", "Todo", "○"),
    IN_PROGRESS("in_progress", "In Progress", "◷"),
    DONE("done", "Done", "✓"),
    CANCELLED("cancelled", "Cancelled", "×");

    private final String value;
    private final String label;
    private final String symbol;

    NoteStatus(String value, String label, String symbol) {
        this.value = value;
        this.label = label;
        this.symbol = symbol;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public String symbol() {
        return symbol;
    }

    @JsonCreator
    public static NoteStatus fromValue(String value) {
        for (NoteStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown note status: " + value);
    }

    @Override
    public String toString() {
        return label;
    }
}