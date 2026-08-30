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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The migration map is the only bridge from 1.0, so a replacement it names has to exist. Checked
 * here rather than reviewed, because a rename in the SDK cannot know it broke a table row.
 */
@DisplayName("Migration map (issue #30)")
class MigrationDocTest {

    private static final Path MIGRATION = Path.of("docs/MIGRATION.md");

    /** Every shipped public package; a name that resolves in none of them is a JDK or 1.0 type. */
    private static final List<String> SDK_PACKAGES = List.of(
            "com.polymarket", "com.polymarket.authentication", "com.polymarket.builders",
            "com.polymarket.markets", "com.polymarket.operations", "com.polymarket.portfolio",
            "com.polymarket.rewards", "com.polymarket.rfq", "com.polymarket.social",
            "com.polymarket.streaming", "com.polymarket.trading");

    private static final Pattern CALL = Pattern.compile("\\b([A-Z]\\w+)\\.(\\w+)\\(");

    @Test
    @DisplayName("TC-MG-001: every 2.0 replacement the map names exists on the public surface")
    void everyNamedReplacementExists() throws IOException {
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
    @DisplayName("TC-MG-002: every SDK type the map names is a real public type")
    void everyNamedTypeExists() throws IOException {
        List<String> broken = new ArrayList<>();
        for (String span : replacementCodeSpans()) {
            for (String word : span.split("[^A-Za-z0-9_.]+")) {
                // Only names the map presents as SDK types: a leading com.polymarket package.
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
                // Not in this package; a name in none of them is a JDK or retired 1.0 type.
            }
        }
        return Optional.empty();
    }

    /** The right-hand column of every table row: what 2.0 offers instead. */
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
