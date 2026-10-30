package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataComplexTypeMapperTest {

    @Test
    void failsWhenAComplexTypeBaseCannotBeResolved() {
        String namespace = "http://example.com/strict";
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "xmlns:tns=\"" + namespace + "\" targetNamespace=\"" + namespace + "\">"
                        + "<xs:complexType name=\"Child\"><xs:complexContent>"
                        + "<xs:extension base=\"tns:MissingBase\"><xs:sequence/></xs:extension>"
                        + "</xs:complexContent></xs:complexType></xs:schema>"));

        TestRegistry registry = new TestRegistry();
        Data child = SimpleFactory.eINSTANCE.createData();
        child.setName("Child");
        registry.register(RegistryKey.of(namespace, "Child"), child);

        MappingContext context = MappingContext.of(
                inspector,
                namespace,
                registry,
                new MappingSession());
        DataAttributeMapper attributes = new DataAttributeMapper();
        DataComplexTypeMapper mapper = new DataComplexTypeMapper(
                new DataContentMapper(attributes),
                attributes);

        MappingResolutionException failure = assertThrows(
                MappingResolutionException.class,
                () -> mapper.populateComplexTypes(
                        List.of("Child"),
                        context));

        assertTrue(failure.getMessage().contains("unresolved-qname"));
        assertTrue(failure.getMessage().contains("Child"));
    }

    @Test
    void resolvesTheBaseTypeOnceBeforePopulatingIt(@TempDir Path directory) throws IOException {
        String namespace = "http://example.com/base";
        String baseNamespace = "http://example.com/external";
        Files.writeString(directory.resolve("base.xsd"),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "targetNamespace=\"" + baseNamespace + "\">"
                        + "<xs:complexType name=\"Parent\"><xs:sequence/></xs:complexType>"
                        + "</xs:schema>");
        Files.writeString(directory.resolve("child.xsd"),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "xmlns:other=\"" + baseNamespace + "\" targetNamespace=\"" + namespace + "\">"
                        + "<xs:import namespace=\"" + baseNamespace + "\" schemaLocation=\"base.xsd\"/>"
                        + "<xs:complexType name=\"Child\"><xs:complexContent>"
                        + "<xs:extension base=\"other:Parent\"><xs:sequence/></xs:extension>"
                        + "</xs:complexContent></xs:complexType></xs:schema>");
        XsdParserInspector inspector = new XsdParserInspector(XsdUtils.parseXsd(directory.resolve("child.xsd")));

        TestRegistry registry = new TestRegistry();
        Data parent = SimpleFactory.eINSTANCE.createData();
        parent.setName("Parent");
        Data child = SimpleFactory.eINSTANCE.createData();
        child.setName("Child");
        RosettaModel model = RosettaFactory.eINSTANCE.createRosettaModel();
        model.setName("com.example.base");
        model.getElements().add(child);
        RegistryKey parentKey = RegistryKey.of(baseNamespace, "Parent");
        registry.register(parentKey, parent);
        registry.register(RegistryKey.of(namespace, "Child"), child);

        MappingContext context = MappingContext.of(inspector, namespace, registry, new MappingSession());
        DataAttributeMapper attributes = new DataAttributeMapper();
        DataComplexTypeMapper mapper = new DataComplexTypeMapper(
                new DataContentMapper(attributes),
                attributes);

        mapper.populateComplexTypes(List.of("Child"), context);

        assertSame(parent, child.getSuperType());
        assertEquals(1, registry.resolveCount(parentKey));
    }

    private static final class TestRegistry extends RuneTypeRegistry {

        private final Map<RegistryKey, com.regnosys.rosetta.rosetta.RosettaType> types = new HashMap<>();
        private final Map<RegistryKey, Integer> resolveCounts = new HashMap<>();

        private TestRegistry() {
            super(RegistryTestUtils.completeBuiltinRegistry());
        }

        @Override
        public void register(RegistryKey key, com.regnosys.rosetta.rosetta.RosettaType type) {
            types.put(key, type);
        }

        @Override
        public com.regnosys.rosetta.rosetta.RosettaType resolve(RegistryKey key) {
            resolveCounts.merge(key, 1, Integer::sum);
            return types.get(key);
        }

        private int resolveCount(RegistryKey key) {
            return resolveCounts.getOrDefault(key, 0);
        }
    }
}
