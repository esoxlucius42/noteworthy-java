package dev.noteworthy.persistence;

import dev.noteworthy.model.Note;
import dev.noteworthy.model.GroupColor;
import dev.noteworthy.model.NoteGroup;
import dev.noteworthy.model.NoteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStoreTest {
    @TempDir
    Path directory;

    @Test
    void missingFileStartsWithNewGroup() {
        JsonStore store = new JsonStore(directory.resolve("notes.json"));

        JsonStore.LoadResult loaded = store.load();

        assertFalse(loaded.recoveryRequired());
        assertEquals("New Group", loaded.groups().getFirst().name());
    }

    @Test
    void writesArrayWithRequiredFieldsAndRestoresSelection() throws Exception {
        Path file = directory.resolve("notes.json");
        Instant timestamp = Instant.parse("2026-09-28T12:34:56Z");
        Note note = new Note(UUID.randomUUID(), "Title", "Several\nlines", timestamp,
                timestamp, NoteStatus.IN_PROGRESS);
        NoteGroup group = new NoteGroup(UUID.randomUUID(), "Planning", List.of(note), true, GroupColor.GREEN);
        JsonStore store = new JsonStore(file);

        store.save(List.of(group));
        String json = Files.readString(file);
        JsonStore.LoadResult loaded = store.load();

        assertTrue(json.stripLeading().startsWith("["));
        assertTrue(json.contains("\"created_at\""));
        assertTrue(json.contains("\"updated_at\""));
        assertTrue(json.contains("\"status\" : \"in_progress\""));
        assertTrue(json.contains("\"color\" : \"green\""));
        assertEquals(List.of(group), loaded.groups());
        assertFalse(loaded.recoveryRequired());
    }

    @Test
    void partiallyInvalidRecordsRequireExplicitBackupRecovery() throws Exception {
        Path file = directory.resolve("notes.json");
        String damaged = """
                [{"id":"%s","name":"Valid group","selected":true,"notes":[
                  {"id":"%s","title":"Keep","body":"Body","created_at":"2026-09-28T12:00:00Z","updated_at":"2026-09-28T12:00:00Z","status":"todo"},
                  {"id":"%s","title":"Bad","body":"Body","created_at":"not-a-date","updated_at":"2026-09-28T12:00:00Z","status":"todo"}
                ]}]
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Files.writeString(file, damaged);
        JsonStore store = new JsonStore(file);

        JsonStore.LoadResult loaded = store.load();

        assertTrue(loaded.recoveryRequired());
        assertEquals(1, loaded.groups().getFirst().notes().size());
        assertEquals(damaged, Files.readString(file));
        assertThrows(java.io.IOException.class, () -> store.save(loaded.groups()));

        Path backup = store.recoverAndSave(loaded.groups());

        assertNotNull(backup);
        assertEquals(damaged, Files.readString(backup));
        assertFalse(store.load().recoveryRequired());
        assertEquals(1, store.load().groups().getFirst().notes().size());
    }

    @Test
    void malformedJsonIsNotReplacedDuringLoad() throws Exception {
        Path file = directory.resolve("notes.json");
        String malformed = "{not-json";
        Files.writeString(file, malformed);
        JsonStore store = new JsonStore(file);

        JsonStore.LoadResult loaded = store.load();

        assertTrue(loaded.recoveryRequired());
        assertEquals("New Group", loaded.groups().getFirst().name());
        assertEquals(malformed, Files.readString(file));
    }

    @Test
    void previousThemeIdsMapToTheReducedPalette() {
        assertEquals(GroupColor.BLUE, GroupColor.fromValue("slate"));
        assertEquals(GroupColor.GREEN, GroupColor.fromValue("pine"));
        assertEquals(GroupColor.MAGENTA, GroupColor.fromValue("rose"));
        assertEquals(GroupColor.BROWN, GroupColor.fromValue("olive"));
    }
}