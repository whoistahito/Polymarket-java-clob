package com.polymarket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.operations.ForeignSignatureLeak;
import com.polymarket.operations.InternalTransportLeak;
import com.polymarket.operations.TransportLibraryLeak;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Public 2.0 boundaries")
class PublicBoundaryTest {

    /** The 2.0 bounded contexts only; the legacy 1.0 packages stay unguarded until #28 deletes them. */
    private static final String[] PUBLIC_PACKAGES =
            {"com.polymarket", "com.polymarket.operations..", "com.polymarket.authentication..",
                    "com.polymarket.markets..", "com.polymarket.trading..",
                    "com.polymarket.portfolio..", "com.polymarket.rewards.."};

    private static final JavaClasses SHIPPED = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.polymarket");

    private static final ArchRule PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL =
            noClasses().that().resideInAnyPackage(PUBLIC_PACKAGES)
                    // Polymarket is the composition root: wiring internal adapters is its whole job.
                    .and().doNotBelongToAnyOf(Polymarket.class)
                    .should().dependOnClassesThat().resideInAPackage("com.polymarket.internal..");

    private static final ArchRule DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES =
            noClasses().that().resideInAnyPackage(PUBLIC_PACKAGES)
                    // Narrow crypto exemption: PrivateKeySigner and its package-private Addresses helper are the
                    // local key-custody adapter, not domain models — the JDK has no secp256k1 or keccak.
                    .and().doNotBelongToAnyOf(PrivateKeySigner.class)
                    .and().doNotHaveFullyQualifiedName("com.polymarket.authentication.Addresses")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..", "okhttp3..", "org.web3j..");

    private static final ArchRule PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES =
            codeUnits().that().arePublic()
                    .and().areDeclaredInClassesThat().arePublic()
                    .and().areDeclaredInClassesThat().resideInAnyPackage(PUBLIC_PACKAGES)
                    .should(useOnlySdkAndJdkTypes());

    @Test
    @DisplayName("TC-AR-001: a public package reaching into internal transport is rejected")
    void internalTransportLeakIsRejected() {
        assertThrows(AssertionError.class, () ->
                PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL.check(forbidden(InternalTransportLeak.class)));
    }

    @Test
    @DisplayName("TC-AR-002: the shipped 2.0 packages keep internal transport out")
    void shippedCodeKeepsInternalTransportOut() {
        PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL.check(SHIPPED);
    }

    @Test
    @DisplayName("TC-AR-003: a domain model using Jackson, OkHttp or Web3j is rejected")
    void transportLibraryLeakIsRejected() {
        AssertionError rejected = assertThrows(AssertionError.class, () ->
                DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES.check(forbidden(TransportLibraryLeak.class)));

        assertTrue(rejected.getMessage().contains("com.fasterxml.jackson"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("okhttp3."), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("org.web3j."), rejected.getMessage());
    }

    @Test
    @DisplayName("TC-AR-004: the shipped 2.0 domain models keep transport libraries out")
    void shippedCodeKeepsTransportLibrariesOut() {
        DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES.check(SHIPPED);
    }

    @Test
    @DisplayName("TC-AR-005: a public signature carrying a non-SDK, non-JDK type is rejected")
    void foreignSignatureLeakIsRejected() {
        assertThrows(AssertionError.class, () ->
                PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES.check(forbidden(ForeignSignatureLeak.class)));
    }

    @Test
    @DisplayName("TC-AR-006: every shipped 2.0 public signature is SDK or JDK only")
    void shippedPublicSignaturesAreSdkOrJdkOnly() {
        PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES.check(SHIPPED);
    }

    private static JavaClasses forbidden(Class<?> violation) {
        return new ClassFileImporter().importClasses(violation);
    }

    /** ponytail: raw types only — a leak hidden in a type argument such as List&lt;OkHttpClient&gt; is not caught. */
    private static ArchCondition<JavaCodeUnit> useOnlySdkAndJdkTypes() {
        return new ArchCondition<>("use only SDK and JDK types") {
            @Override
            public void check(JavaCodeUnit unit, ConditionEvents events) {
                Stream.concat(Stream.of(unit.getRawReturnType()), unit.getRawParameterTypes().stream())
                        .map(JavaClass::getBaseComponentType)
                        .filter(type -> !isSdkOrJdk(type))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(unit,
                                unit.getFullName() + " exposes " + type.getName())));
            }
        };
    }

    private static boolean isSdkOrJdk(JavaClass type) {
        String name = type.getName();
        return type.isPrimitive() || name.startsWith("java.")
                || (name.startsWith("com.polymarket.") && !name.startsWith("com.polymarket.internal."));
    }
}
