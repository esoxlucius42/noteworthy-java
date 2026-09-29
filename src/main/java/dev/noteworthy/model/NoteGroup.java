package dev.noteworthy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record NoteGroup(
        UUID id,
        String name,
        List<Note> notes,
        @JsonProperty("selected") boolean selected,
        GroupColor color) {

    public NoteGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        Objects.requireNonNull(color, "color");
    }

    public NoteGroup(UUID id, String name, List<Note> notes, boolean selected) {
        this(id, name, notes, selected, GroupColor.BLUE);
    }

    public static NoteGroup create(String name) {
        return new NoteGroup(UUID.randomUUID(), name, List.of(), false);
    }

    public NoteGroup withName(String newName) {
        return new NoteGroup(id, newName, notes, selected, color);
    }

    public NoteGroup withNotes(List<Note> newNotes) {
        return new NoteGroup(id, name, newNotes, selected, color);
    }

    public NoteGroup withSelected(boolean isSelected) {
        return new NoteGroup(id, name, notes, isSelected, color);
    }

    public NoteGroup withColor(GroupColor newColor) {
        return new NoteGroup(id, name, notes, selected, newColor);
    }
}