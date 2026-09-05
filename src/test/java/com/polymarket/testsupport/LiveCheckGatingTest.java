package com.polymarket.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Guards the live lane so it cannot enter deterministic tests or escape its package. */
class LiveCheckGatingTest {

    private static final String LIVE_PACKAGE = "com.polymarket.live";

    private static final JavaClasses ALL_TESTS =
            new ClassFileImporter().importPackages("com.polymarket");

    @Test
    void shouldTagEveryLiveCheckWhenItContainsTestMethods() {
        List<JavaClass> liveChecks = testClassesIn(LIVE_PACKAGE);

        assertFalse(liveChecks.isEmpty(), "no live checks found — the rule would pass vacuously");
        liveChecks.forEach(c -> assertTrue(isTaggedLive(c),
                c.getName() + " is a live check without @Tag(\"live\"); "
                        + "it would run in the deterministic suite"));
    }

    @Test
    void shouldConfineLiveTagWhenClassIsOutsideLivePackage() {
        List<String> strays = ALL_TESTS.stream()
                .filter(LiveCheckGatingTest::isTaggedLive)
                .filter(c -> !c.getPackageName().startsWith(LIVE_PACKAGE))
                .map(JavaClass::getName)
                .toList();

        assertEquals(List.of(), strays, "live-tagged classes outside " + LIVE_PACKAGE);
    }

    @Test
    void shouldKeepLiveGuardOffWhenPropertyIsUnset() {
        assertFalse(NoExternalNetworkResolverProvider.liveEnabled(),
                "polymarket.live is set during the deterministic suite");
    }

    private static List<JavaClass> testClassesIn(String packageName) {
        return ALL_TESTS.stream()
                .filter(c -> c.getPackageName().startsWith(packageName))
                .filter(c -> c.getMethods().stream()
                        .anyMatch(m -> m.isAnnotatedWith(Test.class)))
                .toList();
    }

    private static boolean isTaggedLive(JavaClass candidate) {
        return candidate.tryGetAnnotationOfType(Tag.class)
                .map(tag -> "live".equals(tag.value()))
                .orElse(false);
    }
}
