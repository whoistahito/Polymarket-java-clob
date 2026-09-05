package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the migration map because an SDK rename cannot otherwise reveal a broken table row. */
class MigrationDocTest {

    private static final Path MIGRATION = Path.of("docs/MIGRATION.md");

    private static final List<String> SDK_PACKAGES = List.of(
            "com.polymarket", "com.polymarket.authentication", "com.polymarket.builders",
            "com.polymarket.markets", "com.polymarket.operations", "com.polymarket.portfolio",
            "com.polymarket.rewards", "com.polymarket.rfq", "com.polymarket.social",
            "com.polymarket.streaming", "com.polymarket.trading");

    private static final Pattern CALL = Pattern.compile("\\b([A-Z]\\w+)\\.(\\w+)\\(");

    @Test
    void shouldFindEveryNamedReplacementWhenReadingMigrationMap() throws IOException {
        List<String> broken = new ArrayList<>();
        Set<String> checked = new LinkedHashSet<>();

        for (String span : replacementCodeSpans()) {
            Matcher matcher = CALL.matcher(span);
            while (matcher.find()) {
                String typeName = matcher.group(1);
                String method = matcher.group(2);
                Optional<Class<?>> type = resolve(typeName);
                if (type.isEmpty()) {
                    continue;
                }
                checked.add(typeName + "." + method);
                if (Stream.of(type.get().getMethods()).noneMatch(m -> m.getName().equals(method))) {
                    broken.add(typeName + "." + method + "() does not exist");
                }
            }
        }

        assertFalse(checked.isEmpty(), "no 2.0 replacement was checked — the parser missed the table");
        assertEquals(List.of(), broken, "migration map entries pointing at nothing");
    }

    @Test
    void shouldFindEveryNamedTypeWhenReadingMigrationMap() throws IOException {
        List<String> broken = new ArrayList<>();
        for (String span : replacementCodeSpans()) {
            for (String word : span.split("[^A-Za-z0-9_.]+")) {
                if (!word.startsWith("com.polymarket.")) {
                    continue;
                }
                String candidate = word.replaceAll("[^A-Za-z0-9_.]+$", "");
                if (!Character.isUpperCase(lastSegment(candidate).charAt(0))) {
                    continue;
                }
                try {
                    Class.forName(candidate);
                } catch (ClassNotFoundException missing) {
                    broken.add(candidate + " is named as a 2.0 replacement but does not exist");
                }
            }
        }
        assertEquals(List.of(), broken, "migration map types pointing at nothing");
    }

    private static String lastSegment(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    private static Optional<Class<?>> resolve(String simpleName) {
        for (String pkg : SDK_PACKAGES) {
            try {
                return Optional.of(Class.forName(pkg + "." + simpleName));
            } catch (ClassNotFoundException next) {
            }
        }
        return Optional.empty();
    }

    private static List<String> replacementCodeSpans() throws IOException {
        List<String> spans = new ArrayList<>();
        for (String line : Files.readAllLines(MIGRATION)) {
            if (!line.startsWith("|") || line.contains("---")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length < 3) {
                continue;
            }
            Matcher code = Pattern.compile("`([^`]+)`").matcher(cells[cells.length - 1]);
            while (code.find()) {
                spans.add(code.group(1));
            }
        }
        return spans;
    }
}
