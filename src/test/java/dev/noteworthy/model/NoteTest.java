package dev.noteworthy.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteTest {
    @Test
    void newNoteStartsTodoWithMatchingTimestamps() {
        Note note = Note.create();

        assertEquals(NoteStatus.TODO, note.status());
        assertEquals(note.createdAt(), note.updatedAt());
        assertNotEquals(new UUID(0, 0), note.id());
    }

    @Test
    void updatePreservesIdentityAndCreationTimeAndCanChangeAnyStatus() {
        Instant created = Instant.parse("2026-09-20T10:15:30Z");
        Note original = new Note(UUID.randomUUID(), "Title", "Body", created, created, NoteStatus.DONE);

        Note updated = original.update("Revised", "New body", NoteStatus.TODO);

        assertEquals(original.id(), updated.id());
        assertEquals(created, updated.createdAt());
        assertEquals("Revised", updated.title());
        assertEquals(NoteStatus.TODO, updated.status());
        assertTrue(!updated.updatedAt().isBefore(created));
    }

    @Test
    void legacyTitleFallsBackToFirstBodyLine() {
        Note note = new Note(UUID.randomUUID(), "", "  First line  \nsecond line", Instant.now(),
                Instant.now(), NoteStatus.TODO);

        assertEquals("First line", note.displayTitle());
    }
}