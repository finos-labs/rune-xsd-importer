package org.finos.rune.importer.xsd.mapper.resolution;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaType;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MappingServiceTest {

    @Test
    void resolvesRegisteredTypeReferences() {
        RuneTypeRegistry registry = registry();
        RosettaType customType = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        customType.setName("CustomType");
        registry.register(RegistryKey.of("http://example.com/source", "CustomType"), customType);

        TypeReferenceResolver resolver = new TypeReferenceResolver(
                inspector(),
                registry);

        assertSame(customType, resolver.resolve(RegistryKey.of("http://example.com/source", "CustomType")));
    }

    @Test
    void failsClosedForUnresolvedAttributeTypes() {
        RuneTypeRegistry registry = registry();
        RosettaType stringType = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        stringType.setName("string");
        registry.register(RegistryKey.of("http://www.w3.org/2001/XMLSchema", "string"), stringType);

        TypeReferenceResolver resolver = new TypeReferenceResolver(
                inspector(),
                registry);

        MappingResolutionException failure = assertThrows(
                MappingResolutionException.class,
                () -> resolver.resolveAttributeType(
                        Optional.empty(),
                        RegistryKey.of("http://example.com/source", "MissingType"),
                        "value"));

        assertEquals("{http://example.com/source}MissingType", failure.reference());
        assertEquals("attribute 'value'", failure.context());
    }

    @Test
    void resolvesRequiredQualifiedReferences() {
        RuneTypeRegistry registry = registry();
        RosettaType customType = RosettaFactory.eINSTANCE.createRosettaTypeAlias();
        customType.setName("CustomType");
        registry.register(RegistryKey.of("http://example.com/source", "CustomType"), customType);

        TypeReferenceResolver resolver = new TypeReferenceResolver(
                inspector(),
                registry);

        assertSame(
                customType,
                resolver.resolveRequired(
                        RegistryKey.of("http://example.com/source", "CustomType"),
                        "base type of simple type 'Alias'"));
    }

    @Test
    void preservesUnqualifiedReferenceDiagnosticsAtTheMapperBoundary() {
        MappingResolutionException failure = MappingResolutionException.invalidReference(
                "SharedType",
                "cross-namespace reference");

        assertEquals("SharedType", failure.reference());
    }

    private XsdParserInspector inspector() {
        return new XsdParserInspector(XsdUtils.parseXsd(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>"));
    }

    private RuneTypeRegistry registry() {
        return new TestRegistry();
    }

    private static final class TestRegistry extends RuneTypeRegistry {

        private final Map<RegistryKey, RosettaType> types = new HashMap<>();

        private TestRegistry() {
            super(RegistryTestUtils.completeBuiltinRegistry());
        }

        @Override
        public void register(RegistryKey key, RosettaType type) {
            types.put(key, type);
        }

        @Override
        public RosettaType resolve(RegistryKey key) {
            return types.get(key);
        }
    }
}
