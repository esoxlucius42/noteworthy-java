# Noteworthy

Noteworthy is a Java 21+ desktop note and reminder manager built with Swing. Groups contain notes with a title, multiline body, creation and modification timestamps, and an editable status.

## Run

Build once with Java 21 or newer and Maven, then launch with the script for your operating system:

```sh
mvn test
mvn package
./run.sh
```

On Windows, run `run.bat` from Explorer or a command prompt. Both scripts expect the packaged JAR at `target/noteworthy-1.0.0.jar` and accept additional Java application arguments.

The shaded JAR contains its runtime dependencies. When started from source, `notes.json` is stored in the project directory. When started from a packaged JAR, it is stored beside that JAR.

## Data and recovery

`notes.json` is a UTF-8 JSON array in group order. Each group contains its stable `id`, `name`, selected marker, color theme ID, and `notes` array. Note timestamps are ISO 8601 instants in UTC; displayed dates use the computer's local time and `dd.MM.yyyy` format. Changes are saved automatically through a temporary file and atomic replacement when supported.

If the file is malformed or contains invalid records, Noteworthy leaves it untouched and offers to recover valid records. Recovery first copies the original to a timestamped `notes.json.backup-*` file. Choosing to open without saving keeps the original protected; changes made in that session cannot be persisted.

## Workspace controls

- Create groups with the `+` tab. Right-click a group tab to rename it, delete it, or choose from five paired dark/light tab color themes.
- Drag tabs to reorder groups. The active group is restored on restart.
- Use `Alt+Left` and `Alt+Right` to move between groups without changing normal Tab navigation in text fields.
- Use `Ctrl+N` in the note list to create a note, `Up`/`Down` to move through notes, `Enter` to edit the selected note, and `Delete` to remove selected notes.
- Search is case-insensitive. Status, creation-date ranges, custom dates, and sorting can be combined. Custom dates use `dd.MM.yyyy`.
- Select multiple notes with the standard platform selection gestures; deleting asks for confirmation.