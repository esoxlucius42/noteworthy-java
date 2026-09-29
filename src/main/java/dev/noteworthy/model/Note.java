package dev.noteworthy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Note(
        UUID id,
        String title,
        String body,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        NoteStatus status) {

    public Note {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(status, "status");
    }

    public static Note create() {
        Instant now = Instant.now();
        return new Note(UUID.randomUUID(), "", "", now, now, NoteStatus.TODO);
    }

    public Note update(String newTitle, String newBody, NoteStatus newStatus) {
        return new Note(id, newTitle, newBody, createdAt, Instant.now(), newStatus);
    }

    public String displayTitle() {
        if (!title.isBlank()) {
            return title;
        }
        String firstLine = body.lines().findFirst().orElse("").strip();
        return firstLine.isBlank() ? "Untitled note" : firstLine;
    }
}