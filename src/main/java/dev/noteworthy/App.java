package dev.noteworthy;

import dev.noteworthy.persistence.JsonStore;
import dev.noteworthy.persistence.StoragePaths;
import dev.noteworthy.ui.WorkspaceFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Path;

public final class App {
    private App() { }

    public static void main(String[] args) {
        Path notesFile = StoragePaths.notesFile();
        Path backupDir = null;
        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            String value = null;
            String name = arg;
            int equals = arg.indexOf('=');
            if (equals >= 0) {
                name = arg.substring(0, equals);
                value = arg.substring(equals + 1);
            } else if (index + 1 < args.length) {
                value = args[++index];
            }
            switch (name) {
                case "--notes-file" -> notesFile = requirePath(name, value);
                case "--backup-dir" -> backupDir = requirePath(name, value);
                default -> {
                    System.err.println("Unknown argument: " + name);
                    System.exit(1);
                }
            }
        }
        Path finalNotesFile = notesFile;
        Path finalBackupDir = backupDir;
        SwingUtilities.invokeLater(() -> start(finalNotesFile, finalBackupDir));
    }

    private static Path requirePath(String name, String value) {
        if (value == null || value.isBlank()) {
            System.err.println(name + " requires a path argument");
            System.exit(1);
        }
        return Path.of(value);
    }

    private static void start(Path notesFile, Path backupDir) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The application palette is applied directly to its components.
        }
        JsonStore store = backupDir == null ? new JsonStore(notesFile) : new JsonStore(notesFile, backupDir);
        JsonStore.LoadResult loaded = store.load();
        if (loaded.recoveryRequired()) {
            Object[] choices = {"Recover with backup", "Open without saving", "Exit"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Some records in notes.json could not be loaded. The original file will not be changed unless you choose recovery.\n\n"
                            + loaded.problem(),
                    "Notes need recovery", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, choices, choices[0]);
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (choice == 0) {
                try {
                    store.recoverAndSave(loaded.groups());
                } catch (IOException exception) {
                    JOptionPane.showMessageDialog(null,
                            "Recovery could not be saved. The original file is unchanged.\n" + exception.getMessage(),
                            "Recovery failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }
        new WorkspaceFrame(store, loaded.groups()).setVisible(true);
    }
}