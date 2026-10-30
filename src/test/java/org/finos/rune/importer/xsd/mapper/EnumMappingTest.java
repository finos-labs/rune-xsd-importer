package org.finos.rune.importer.xsd.mapper;

import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaModel;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.mapping.EnumMapping;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierValidator;
import org.finos.rune.importer.xsd.registry.RegistryTestUtils;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnumMappingTest {

    @Test
    void createsOneImmutableMappingForRuneAndXmlValues(@TempDir Path temporaryDirectory) throws IOException {
        RosettaEnumeration runeEnum = mapEnumeration(temporaryDirectory, List.of("PENDING", "SETTLED"));

        assertEquals(List.of("PENDING", "SETTLED"),
                runeEnum.getEnumValues().stream().map(value -> value.getName()).toList());
        EnumMapping mapping = new EnumMapping(
                "Status",
                List.of(new EnumMapping.Value("PENDING", "PENDING")));
        assertThrows(UnsupportedOperationException.class,
                () -> mapping.values().add(new EnumMapping.Value("NEW", "NEW")));
    }

    @Test
    void keepsCollidingLabelsUniqueWhilePreservingXmlValues(@TempDir Path temporaryDirectory) throws IOException {
        RosettaEnumeration runeEnum = mapEnumeration(temporaryDirectory, List.of("A-B", "A_B", "a b", "a_b"));

        assertEquals(
                List.of("A_B", "_a_b", "_a_b_2", "_a_b_3"),
                runeEnum.getEnumValues().stream().map(value -> value.getName()).toList());
    }

    @Test
    void rejectsInvalidEnumLabelsBeforeGeneratingInvalidRuneValues() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> RuneIdentifierValidator.requireValid("", "", "enum label"));

        assertTrue(failure.getMessage().contains("Invalid Rune enum label name"));
    }

    private RosettaEnumeration mapEnumeration(Path temporaryDirectory, List<String> values) throws IOException {
        String enumerationValues = values.stream()
                .map(value -> "<xs:enumeration value=\"" + value + "\"/>")
                .reduce("", String::concat);
        Path schema = temporaryDirectory.resolve("enum.xsd");
        Files.writeString(schema, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:enum-test"
                           xmlns:tns="urn:enum-test">
                    <xs:simpleType name="Status">
                        <xs:restriction base="xs:string">%s</xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """.formatted(enumerationValues));

        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(schema);
        XsdParserInspector inspector = new XsdParserInspector(parser);
        RuneTypeRegistry registry = new RuneTypeRegistry(RegistryTestUtils.completeBuiltinRegistry());
        EnumMapper mapper = new EnumMapper(new MappingSession());
        mapper.register(inspector, "urn:enum-test", registry);
        RosettaEnumeration runeEnum = (RosettaEnumeration) registry.get("urn:enum-test", "Status");
        RosettaModel model = RosettaFactory.eINSTANCE.createRosettaModel();
        model.setName("com.example.enum");
        model.getElements().add(runeEnum);
        mapper.populate(inspector, "urn:enum-test", registry);

        return runeEnum;
    }
}
