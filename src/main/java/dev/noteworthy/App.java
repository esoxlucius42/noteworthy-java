package dev.noteworthy;

import dev.noteworthy.persistence.JsonStore;
import dev.noteworthy.persistence.StoragePaths;
import dev.noteworthy.ui.WorkspaceFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;

public final class App {
    private App() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The application palette is applied directly to its components.
        }
        JsonStore store = new JsonStore(StoragePaths.notesFile());
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