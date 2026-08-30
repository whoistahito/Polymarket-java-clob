package com.polymarket.examples;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The README's Java blocks are the same source {@code ReadmeExamples} compiles, so an example
 * that no longer matches the API fails the build rather than misleading a reader.
 */
@DisplayName("README examples (issue #30)")
class ReadmeExamplesTest {

    private static final Path README = Path.of("README.md");
    /** Not a test source: it is compiled against the packaged jar in the verify phase. */
    private static final Path COMPILED =
            Path.of("src/examples/java/com/polymarket/examples/ReadmeExamples.java");

    @Test
    @DisplayName("TC-RM-001: every README Java block is compiled source, not prose")
    void everyReadmeBlockIsCompiled() throws IOException {
        List<String> compiled = meaningfulLines(Files.readString(COMPILED));
        List<List<String>> blocks = javaBlocks(Files.readString(README));

        assertFalse(blocks.isEmpty(), "the README carries no Java example to verify");
        for (List<String> block : blocks) {
            assertTrue(Collections.indexOfSubList(compiled, block) >= 0,
                    "this README block is not in ReadmeExamples.java:\n" + String.join("\n", block));
        }
    }

    @Test
    @DisplayName("TC-RM-002: every import a README block shows is one the compiled example uses")
    void everyReadmeImportIsReal() throws IOException {
        String compiled = Files.readString(COMPILED);
        for (String line : Files.readString(README).lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import com.polymarket")) {
                assertTrue(compiled.contains(trimmed), trimmed + " is shown but never compiled");
            }
        }
    }

    /** Imports and blank lines are presentation; the statements are what has to match. */
    private static List<String> meaningfulLines(String source) {
        List<String> lines = new ArrayList<>();
        for (String line : source.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("import ")) lines.add(trimmed);
        }
        return lines;
    }

    private static List<List<String>> javaBlocks(String markdown) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : markdown.lines().toList()) {
            if (line.startsWith("```java")) {
                current = new ArrayList<>();
            } else if (line.startsWith("```") && current != null) {
                blocks.add(current);
                current = null;
            } else if (current != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("import ")) current.add(trimmed);
            }
        }
        return blocks;
    }
}
