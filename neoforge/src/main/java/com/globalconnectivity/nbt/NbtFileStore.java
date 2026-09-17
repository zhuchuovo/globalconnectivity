package com.globalconnectivity.nbt;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The local ".nbt folder" of this mod, mirroring how Create keeps its schematics:
 * a plain folder named {@code nbtfiles} in the game directory that the player can
 * drop structure files into and browse from the in-game UI.
 */
public final class NbtFileStore {
    public record LocalFile(String name, long sizeBytes, long lastModified) {}

    private NbtFileStore() {}

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("nbtfiles");
    }

    public static void ensureFolder() throws IOException {
        Files.createDirectories(folder());
    }

    public static List<LocalFile> list() {
        try {
            ensureFolder();
            List<LocalFile> out = new ArrayList<>();
            try (var stream = Files.list(folder())) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                        out.add(new LocalFile(name, sizeOf(p), Files.getLastModifiedTime(p).toMillis()));
                    }
                }
            }
            out.sort(Comparator.comparing(LocalFile::name, String.CASE_INSENSITIVE_ORDER));
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Strips any path components and control characters, and forces the .nbt suffix. */
    public static String sanitizeName(String raw) {
        String n = raw == null ? "" : raw.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[\\x00-\\x1f<>:\"|?*]", "_");
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            n = "file.nbt";
        }
        if (!n.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            n = n + ".nbt";
        }
        if (n.length() > 120) {
            n = n.substring(0, 116) + ".nbt";
        }
        return n;
    }

    static boolean isSafeName(String name) {
        return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    /** Resolves a name that must already exist in the store, or null. */
    public static Path resolveExisting(String name) {
        if (!isSafeName(name)) {
            return null;
        }
        Path p = folder().resolve(name);
        return Files.isRegularFile(p) ? p : null;
    }

    /** Copies an external file into the store with a unique, sanitized name. Returns the stored name or null. */
    public static String importFile(Path source) {
        try {
            ensureFolder();
            if (!Files.isRegularFile(source)) {
                return null;
            }
            String name = sanitizeName(source.getFileName().toString());
            String base = name.substring(0, name.length() - 4);
            Path target = folder().resolve(name);
            int i = 1;
            while (Files.exists(target)) {
                if (i > 999) {
                    return null;
                }
                name = base + "(" + i++ + ").nbt";
                target = folder().resolve(name);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return name;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean delete(String name) {
        Path p = resolveExisting(name);
        if (p == null) {
            return false;
        }
        try {
            Files.delete(p);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
