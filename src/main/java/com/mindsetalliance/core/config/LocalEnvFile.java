package com.mindsetalliance.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Charge {@code .env} comme spring-dotenv, y compris si le cwd IntelliJ est le monorepo parent.
 * N’écrase pas une variable déjà définie dans l’environnement système.
 */
public final class LocalEnvFile {

    private static final Logger log = LoggerFactory.getLogger(LocalEnvFile.class);

    private LocalEnvFile() {
    }

    public static void load() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path[] candidates = {
                dir.resolve("ma-core-backend").resolve(".env"),
                dir.resolve(".env")
        };
        for (Path file : candidates) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            int loaded = apply(file);
            log.info("Fichier .env lu ({} variables) depuis {}", loaded, file);
            return;
        }
        log.warn("Aucun .env trouvé (cherché ma-core-backend/.env puis .env du répertoire de lancement)");
    }

    private static int apply(Path file) {
        int count = 0;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                String name = line.substring(0, eq).strip();
                if (name.isEmpty()) {
                    continue;
                }
                String value = unquote(line.substring(eq + 1).strip());
                if (System.getenv(name) != null) {
                    continue;
                }
                String existing = System.getProperty(name);
                if (existing != null && !existing.isBlank()) {
                    continue;
                }
                System.setProperty(name, value);
                count++;
            }
        } catch (Exception ex) {
            log.warn("Lecture .env impossible: {}", ex.getMessage());
        }
        return count;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
