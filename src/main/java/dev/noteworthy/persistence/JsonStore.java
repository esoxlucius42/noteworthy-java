package dev.noteworthy.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.noteworthy.model.GroupColor;
import dev.noteworthy.model.Note;
import dev.noteworthy.model.NoteGroup;
import dev.noteworthy.model.NoteStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class JsonStore {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_BACKUPS = 10;
    private final Path file;
    private final Path backupDir;
    private final ObjectMapper mapper;
    private boolean recoveryRequired;

    public JsonStore(Path file) {
        this(file, file.toAbsolutePath().normalize().getParent().resolve("backup"));
    }

    public JsonStore(Path file, Path backupDir) {
        this.file = file.toAbsolutePath().normalize();
        this.backupDir = backupDir.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path file() {
        return file;
    }

    public boolean recoveryRequired() {
        return recoveryRequired;
    }

    public LoadResult load() {
        if (!Files.exists(file)) {
            recoveryRequired = false;
            return new LoadResult(List.of(NoteGroup.create("New Group")), false, "");
        }
        List<NoteGroup> groups = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(file.toFile());
            if (root == null || !root.isArray()) {
                throw new IOException("The top-level JSON value must be an array of groups.");
            }
            Set<UUID> groupIds = new HashSet<>();
            Set<UUID> noteIds = new HashSet<>();
            boolean selectedSeen = false;
            for (int index = 0; index < root.size(); index++) {
                try {
                    JsonNode groupNode = root.get(index);
                    UUID id = UUID.fromString(requiredText(groupNode, "id"));
                    if (!groupIds.add(id)) {
                        throw new IllegalArgumentException("duplicate group id");
                    }
                    String name = requiredText(groupNode, "name").strip();
                    if (name.isEmpty()) {
                        throw new IllegalArgumentException("group name is empty");
                    }
                    GroupColor color = GroupColor.BLUE;
                    JsonNode colorNode = groupNode.get("color");
                    if (colorNode != null && !colorNode.isNull()) {
                        try {
                            if (!colorNode.isTextual()) throw new IllegalArgumentException("color must be text");
                            color = GroupColor.fromValue(colorNode.textValue());
                        } catch (RuntimeException exception) {
                            problems.add("Group " + (index + 1) + " has an invalid color; Blue was used.");
                        }
                    }
                    JsonNode notesNode = groupNode.get("notes");
                    if (notesNode == null || !notesNode.isArray()) {
                        throw new IllegalArgumentException("notes must be an array");
                    }
                    boolean selected = groupNode.path("selected").asBoolean(false);
                    if (selected && selectedSeen) {
                        problems.add("More than one group was marked selected; the first selection was kept.");
                        selected = false;
                    }
                    selectedSeen |= selected;
                    List<Note> notes = new ArrayList<>();
                    for (int noteIndex = 0; noteIndex < notesNode.size(); noteIndex++) {
                        try {
                            Note note = parseNote(notesNode.get(noteIndex));
                            if (!noteIds.add(note.id())) {
                                throw new IllegalArgumentException("duplicate note id");
                            }
                            notes.add(note);
                        } catch (RuntimeException exception) {
                            problems.add("Group " + (index + 1) + ", note " + (noteIndex + 1)
                                    + ": " + message(exception));
                        }
                    }
                    groups.add(new NoteGroup(id, name, notes, selected, color));
                } catch (RuntimeException exception) {
                    problems.add("Group " + (index + 1) + ": " + message(exception));
                }
            }
            if (groups.isEmpty()) {
                groups.add(NoteGroup.create("New Group"));
                problems.add("No valid groups were found; a replacement group was created.");
            }
        } catch (Exception exception) {
            groups = new ArrayList<>(List.of(NoteGroup.create("New Group")));
            problems.add("Could not read notes.json: " + message(exception));
        }
        recoveryRequired = !problems.isEmpty();
        return new LoadResult(List.copyOf(groups), recoveryRequired, String.join("\n", problems));
    }

    public void save(List<NoteGroup> groups) throws IOException {
        if (recoveryRequired) {
            throw new IOException("Saving is paused until damaged data is explicitly recovered.");
        }
        writeAtomically(groups);
    }

    public Path recoverAndSave(List<NoteGroup> groups) throws IOException {
        Path backup = null;
        if (Files.exists(file)) {
            String stamp = LocalDateTime.now().format(BACKUP_TIME);
            backup = file.resolveSibling("notes.json.backup-" + stamp);
            int suffix = 1;
            while (Files.exists(backup)) {
                backup = file.resolveSibling("notes.json.backup-" + stamp + "-" + suffix++);
            }
            Files.copy(file, backup);
        }
        writeAtomically(groups);
        recoveryRequired = false;
        return backup;
    }

    /** Copies notes.json into the backup folder with a timestamped name, keeping only the newest {@value MAX_BACKUPS}. */
    public void backupNow() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        Files.createDirectories(backupDir);
        String stamp = LocalDateTime.now().format(BACKUP_TIME);
        Path backup = backupDir.resolve("notes-" + stamp + ".json");
        int suffix = 1;
        while (Files.exists(backup)) {
            backup = backupDir.resolve("notes-" + stamp + "-" + suffix++ + ".json");
        }
        Files.copy(file, backup);
        pruneOldBackups(backupDir);
    }

    private void pruneOldBackups(Path backupDir) throws IOException {
        try (Stream<Path> listing = Files.list(backupDir)) {
            List<Path> backups = listing
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (int index = 0; index < backups.size() - MAX_BACKUPS; index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private Note parseNote(JsonNode node) {
        UUID id = UUID.fromString(requiredText(node, "id"));
        String title = requiredText(node, "title");
        String body = requiredText(node, "body");
        Instant created = Instant.parse(requiredText(node, "created_at"));
        Instant updated = Instant.parse(requiredText(node, "updated_at"));
        NoteStatus status = NoteStatus.fromValue(requiredText(node, "status"));
        return new Note(id, title, body, created, updated, status);
    }

    private String requiredText(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("record must be a JSON object");
        }
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("missing or invalid '" + field + "'");
        }
        return value.textValue();
    }

    private void writeAtomically(List<NoteGroup> groups) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = mapper.writeValueAsString(groups);
        Path temp = Files.createTempFile(parent, "notes-", ".tmp");
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record LoadResult(List<NoteGroup> groups, boolean recoveryRequired, String problem) { }
}