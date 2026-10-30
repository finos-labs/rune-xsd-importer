package org.finos.rune.importer.xsd.mapper.mapping;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.mapper.DataMapper;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalElementBindingTest {

    @Test
    void recordsEachGlobalElementBindingWithItsQualifiedTarget() {
        assertBinding("inheritance/multi-importer.xsd", "http://example.com/multi", "UserRecord",
                GlobalElementBinding.Mode.MERGED_TYPED_ELEMENT, RegistryKey.of("http://example.com/multi", "UserRecord"));
        assertBinding("misc/simple-test.xsd", "http://example.com/simple", "TestObject",
                GlobalElementBinding.Mode.TYPED_PROXY, RegistryKey.of("http://example.com/simple", "TestObjectType"));
        assertBinding("refs/element-ref-pattern.xsd", "http://example.com/rune/test/refs/pattern", "root",
                GlobalElementBinding.Mode.ANONYMOUS_INLINE, RegistryKey.of("http://example.com/rune/test/refs/pattern", "root"));
        assertBinding("choice/nested-choice.xsd", "http://example.com/nestedchoice", "Trade",
                GlobalElementBinding.Mode.INLINE_PROXY, RegistryKey.of("http://example.com/nestedchoice", "PricingModel"));
        assertBinding("refs/element-ref.xsd", "http://example.com/rune/test/refs/element", "globalRefEl",
                GlobalElementBinding.Mode.NATIVE_WRAPPER, RegistryKey.of("http://example.com/rune/test/refs/element", "globalRefEl"));
        assertBinding("refs/element-ref-pattern.xsd", "http://example.com/rune/test/refs/pattern", "patternRefEl",
                GlobalElementBinding.Mode.VIRTUAL_ALIAS, RegistryKey.of("http://example.com/rune/test/refs/pattern", "patternRefEl"));
    }

    @Test
    void recordsImportedTypedProxyUsingTheReferencedTypeKey(@TempDir Path directory) throws IOException {
        String rootNamespace = "urn:binding:root";
        String importedNamespace = "urn:binding:imported";
        Files.writeString(directory.resolve("root.xsd"), schema(
                "xmlns:imp=\"" + importedNamespace + "\" targetNamespace=\"" + rootNamespace + "\">"
                        + "<xs:import namespace=\"" + importedNamespace + "\" schemaLocation=\"imported.xsd\"/>"
                        + "<xs:element name=\"Reference\" type=\"imp:ReferenceType\"/>"));
        Files.writeString(directory.resolve("imported.xsd"), schema(
                "targetNamespace=\"" + importedNamespace + "\">"
                        + "<xs:complexType name=\"ReferenceType\"><xs:sequence/></xs:complexType>"));

        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("root.xsd")));
        MappingSession session = new MappingSession();
        RuneTypeRegistry registry = new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry());
        DataMapper mapper = new DataMapper(session);
        RosettaModel model = RosettaFactory.eINSTANCE.createRosettaModel();
        model.setName("binding.test");
        model.getElements().addAll(mapper.register(inspector, importedNamespace, registry));
        model.getElements().addAll(mapper.register(inspector, rootNamespace, registry));
        GlobalElementBinding binding = session.globalElementBinding(RegistryKey.of(rootNamespace, "Reference"));

        assertEquals(GlobalElementBinding.Mode.TYPED_PROXY, binding.mode());
        assertEquals(RegistryKey.of(importedNamespace, "ReferenceType"), binding.targetKey());

        mapper.populate(inspector, rootNamespace, registry);

        Data reference = assertInstanceOf(Data.class, registry.resolve(RegistryKey.of(rootNamespace, "Reference")));
        assertEquals("ReferenceType", reference.getSuperType().getName());
    }

    @Test
    void skipsAParserCandidateRejectedByTheGlobalElementPredicateInBothPhases(@TempDir Path directory) throws IOException {
        String namespace = "urn:binding:filtered";
        Files.writeString(directory.resolve("filtered.xsd"), schema("targetNamespace=\"" + namespace + "\">"));
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("filtered.xsd"))) {
            @Override
            public List<String> getAllGlobalElements(String ignoredNamespace) {
                return List.of("animal");
            }

            @Override
            public boolean isGlobalElement(String ignoredNamespace, String name) {
                return false;
            }
        };
        MappingSession session = new MappingSession();
        RuneTypeRegistry registry = new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry());
        DataMapper mapper = new DataMapper(session);

        assertDoesNotThrow(() -> mapper.register(inspector, namespace, registry));
        assertDoesNotThrow(() -> mapper.populate(inspector, namespace, registry));
    }

    private void assertBinding(
            String resource,
            String namespace,
            String elementName,
            GlobalElementBinding.Mode mode,
            RegistryKey targetKey) {
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                Path.of("src/test/resources/org/finos/rune/xsd/integration/features", resource)));
        MappingSession session = new MappingSession();
        new DataMapper(session).register(inspector, namespace, new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry()));

        GlobalElementBinding binding = session.globalElementBinding(RegistryKey.of(namespace, elementName));
        assertEquals(mode, binding.mode());
        assertEquals(targetKey, binding.targetKey());
    }

    private String schema(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                + body
                + "</xs:schema>";
    }
}
