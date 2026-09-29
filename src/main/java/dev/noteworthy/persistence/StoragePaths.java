package dev.noteworthy.persistence;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StoragePaths {
    private StoragePaths() { }

    public static Path notesFile() {
        try {
            Path location = Path.of(StoragePaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(location)) {
                return location.getParent().resolve("notes.json");
            }
            Path directory = location;
            while (directory != null) {
                if (Files.exists(directory.resolve("pom.xml"))) {
                    return directory.resolve("notes.json");
                }
                directory = directory.getParent();
            }
        } catch (URISyntaxException | SecurityException ignored) {
            // Fall back to the process directory for unusual class loaders.
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("notes.json");
    }
}