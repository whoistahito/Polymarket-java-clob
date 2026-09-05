package com.polymarket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.operations.ForeignSignatureLeak;
import com.polymarket.operations.GenericSignatureLeak;
import com.polymarket.operations.InternalTransportLeak;
import com.polymarket.operations.NullableModelLeak;
import com.polymarket.operations.TransportLibraryLeak;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PublicBoundaryTest {

    /** Public 2.0 surface is everything outside {@code internal}; new contexts are covered automatically. */
    private static final String INTERNAL = "com.polymarket.internal..";

    private static final JavaClasses SHIPPED = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.polymarket");

    private static final ArchRule PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL =
            noClasses().that().resideOutsideOfPackage(INTERNAL)
                    // Polymarket is the composition root: wiring internal adapters is its whole job.
                    .and().doNotBelongToAnyOf(Polymarket.class)
                    .should().dependOnClassesThat().resideInAPackage(INTERNAL);

    private static final ArchRule DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES =
            noClasses().that().resideOutsideOfPackage(INTERNAL)
                    // Narrow crypto exemption: PrivateKeySigner and its package-private Addresses helper are the
                    // local key-custody adapter, not domain models — the JDK has no secp256k1 or keccak.
                    .and().doNotBelongToAnyOf(PrivateKeySigner.class)
                    .and().doNotHaveFullyQualifiedName("com.polymarket.authentication.Addresses")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.fasterxml.jackson..", "okhttp3..", "org.web3j..");

    private static final ArchRule PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES =
            codeUnits().that().arePublic()
                    .and().areDeclaredInClassesThat().arePublic()
                    .and().areDeclaredInClassesThat().resideOutsideOfPackage(INTERNAL)
                    .should(useOnlySdkAndJdkTypes());

    /** Public models reject null references at construction; absence is represented by Optional. */
    private static final ArchRule PUBLIC_RECORD_COMPONENTS_REJECT_NULL =
            classes().that().arePublic().and().resideOutsideOfPackage(INTERNAL)
                    .should(rejectNullComponents());

    @Test
    void shouldThrowWhenPublicCodeDependsOnInternal() {
        assertThrows(AssertionError.class, () ->
                PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL.check(forbidden(InternalTransportLeak.class)));
    }

    @Test
    void shouldKeepShippedCodeOutsideInternalWhenBoundaryIsChecked() {
        PUBLIC_PACKAGES_DO_NOT_DEPEND_ON_INTERNAL.check(SHIPPED);
    }

    @Test
    void shouldThrowWhenDomainModelUsesTransportLibrary() {
        AssertionError rejected = assertThrows(AssertionError.class, () ->
                DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES.check(forbidden(TransportLibraryLeak.class)));

        assertTrue(rejected.getMessage().contains("com.fasterxml.jackson"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("okhttp3."), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("org.web3j."), rejected.getMessage());
    }

    @Test
    void shouldKeepShippedModelsFreeOfTransportWhenBoundaryIsChecked() {
        DOMAIN_MODELS_DO_NOT_USE_TRANSPORT_LIBRARIES.check(SHIPPED);
    }

    @Test
    void shouldThrowWhenPublicSignatureUsesForeignType() {
        assertThrows(AssertionError.class, () ->
                PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES.check(forbidden(ForeignSignatureLeak.class)));
    }

    @Test
    void shouldThrowWhenForeignTypeIsHiddenInGenericSignature() {
        AssertionError rejected = assertThrows(AssertionError.class, () ->
                PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES.check(forbidden(GenericSignatureLeak.class)));

        String message = rejected.getMessage();
        assertTrue(message.contains("responses()"), message);
        assertTrue(message.contains("nested()"), message);
        assertTrue(message.contains("pending()"), message);
        assertTrue(message.contains("accept("), message);
        assertTrue(message.contains("arrays()"), message);
        assertTrue(message.contains("bounded()"), message);
    }

    @Test
    void shouldKeepShippedSignaturesSdkOrJdkWhenBoundaryIsChecked() {
        PUBLIC_SIGNATURES_USE_ONLY_SDK_AND_JDK_TYPES.check(SHIPPED);
    }

    @Test
    void shouldThrowWhenPublicModelAcceptsNull() {
        AssertionError rejected = assertThrows(AssertionError.class, () ->
                PUBLIC_RECORD_COMPONENTS_REJECT_NULL.check(forbidden(NullableModelLeak.class)));

        String message = rejected.getMessage();
        assertTrue(message.contains("required"), message);
        assertTrue(message.contains("optional"), message);
        assertFalse(message.contains("count"), "a primitive component cannot be null: " + message);
    }

    @Test
    void shouldKeepShippedModelsNullSafeWhenBoundaryIsChecked() {
        PUBLIC_RECORD_COMPONENTS_REJECT_NULL.check(SHIPPED);
    }

    private static JavaClasses forbidden(Class<?> violation) {
        return new ClassFileImporter().importClasses(violation);
    }

    /** Walks the whole signature: type arguments, wildcard and type-variable bounds, array components. */
    private static ArchCondition<JavaCodeUnit> useOnlySdkAndJdkTypes() {
        return new ArchCondition<>("use only SDK and JDK types") {
            @Override
            public void check(JavaCodeUnit unit, ConditionEvents events) {
                Set<JavaClass> exposed = new LinkedHashSet<>();
                Stream.of(Stream.of(unit.getReturnType()), unit.getParameterTypes().stream(),
                                unit.getTypeParameters().stream().map(JavaType.class::cast))
                        .flatMap(s -> s)
                        .forEach(type -> collect(type, exposed, new LinkedHashSet<>()));
                exposed.stream().filter(type -> !isSdkOrJdk(type))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(unit,
                                unit.getFullName() + " exposes " + type.getName())));
            }
        };
    }

    /** {@code seen} stops a self-referential bound such as {@code <T extends Comparable<T>>}. */
    private static void collect(JavaType type, Set<JavaClass> into, Set<JavaType> seen) {
        if (!seen.add(type)) return;
        if (type instanceof JavaClass raw) {
            into.add(raw.getBaseComponentType());
        } else if (type instanceof JavaParameterizedType parameterized) {
            collect(parameterized.toErasure(), into, seen);
            parameterized.getActualTypeArguments().forEach(a -> collect(a, into, seen));
        } else if (type instanceof JavaWildcardType wildcard) {
            wildcard.getUpperBounds().forEach(b -> collect(b, into, seen));
            wildcard.getLowerBounds().forEach(b -> collect(b, into, seen));
        } else if (type instanceof JavaTypeVariable<?> variable) {
            variable.getUpperBounds().forEach(b -> collect(b, into, seen));
        } else {
            collect(type.toErasure(), into, seen);
        }
    }


    /** Lombok's {@code @NonNull} is CLASS-retained, so it is read from the canonical constructor. */
    private static ArchCondition<JavaClass> rejectNullComponents() {
        return new ArchCondition<>("reject null in every reference component") {
            @Override
            public void check(JavaClass record, ConditionEvents events) {
                Class<?> reflected = record.reflect();
                if (!reflected.isRecord()) return;
                List<String> names = Stream.of(reflected.getRecordComponents())
                        .map(RecordComponent::getName).toList();
                Optional<JavaConstructor> canonical = canonicalConstructorOf(record, reflected);
                if (canonical.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(record,
                            record.getName() + " has no canonical constructor to guard"));
                    return;
                }
                canonical.get().getParameters().stream()
                        .filter(p -> !p.getRawType().isPrimitive())
                        .filter(p -> p.getAnnotations().stream()
                                .noneMatch(a -> a.getRawType().getName().equals("lombok.NonNull")))
                        .forEach(p -> events.add(SimpleConditionEvent.violated(record,
                                record.getName() + " component " + names.get(p.getIndex())
                                        + " accepts null; mark it @NonNull")));
            }
        };
    }

    private static Optional<JavaConstructor> canonicalConstructorOf(
            JavaClass record, Class<?> reflected) {
        List<String> components = Stream.of(reflected.getRecordComponents())
                .map(c -> c.getType().getName()).toList();
        return record.getConstructors().stream()
                .filter(c -> c.getRawParameterTypes().stream()
                        .map(JavaClass::getName).toList().equals(components))
                .findFirst();
    }

    private static boolean isSdkOrJdk(JavaClass type) {
        String name = type.getName();
        return type.isPrimitive() || name.startsWith("java.")
                || (name.startsWith("com.polymarket.") && !name.startsWith("com.polymarket.internal."));
    }
}
