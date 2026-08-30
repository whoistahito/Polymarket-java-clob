package com.polymarket.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Guards the opt-in lane both ways: an untagged live check would hit the network in the
 * deterministic suite, and a stray {@code @Tag("live")} elsewhere would silently stop running.
 */
@DisplayName("Live checks stay gated out of the deterministic suite")
class LiveCheckGatingTest {

    private static final String LIVE_PACKAGE = "com.polymarket.live";

    private static final JavaClasses ALL_TESTS =
            new ClassFileImporter().importPackages("com.polymarket");

    @Test
    @DisplayName("TC-LG-001: every live check class carries @Tag(\"live\")")
    void everyLiveCheckIsTagged() {
        List<JavaClass> liveChecks = testClassesIn(LIVE_PACKAGE);

        assertFalse(liveChecks.isEmpty(), "no live checks found — the rule would pass vacuously");
        liveChecks.forEach(c -> assertTrue(isTaggedLive(c),
                c.getName() + " is a live check without @Tag(\"live\"); "
                        + "it would run in the deterministic suite"));
    }

    @Test
    @DisplayName("TC-LG-002: nothing outside the live package claims the live tag")
    void liveTagIsConfinedToTheLivePackage() {
        List<String> strays = ALL_TESTS.stream()
                .filter(LiveCheckGatingTest::isTaggedLive)
                .filter(c -> !c.getPackageName().startsWith(LIVE_PACKAGE))
                .map(JavaClass::getName)
                .toList();

        assertEquals(List.of(), strays, "live-tagged classes outside " + LIVE_PACKAGE);
    }

    @Test
    @DisplayName("TC-LG-003: the live property is off unless -Plive sets it")
    void liveGuardIsOffByDefault() {
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
