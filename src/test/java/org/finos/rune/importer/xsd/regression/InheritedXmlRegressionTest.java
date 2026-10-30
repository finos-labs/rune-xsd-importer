package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for XML metadata inherited from an imported complex type whose XSD name is
 * transformed for Rune.
 *
 * <p>This is intentionally a focused regression test rather than a full public integration scenario.
 * It covers qualified XSD identity and the affected XML configuration without adding the broader
 * source-schema validation and XML round-trip contract used by the documented integration suite.</p>
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InheritedXmlRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/regression/inheritedxml/derived.xsd");
    private static final String BASE_NS = "urn:example:inherited:base";
    private static final String DERIVED_NS = "urn:example:inherited:derived";
    private static final String BASE_PKG = "com.example.inherited.base";
    private static final String DERIVED_PKG = "com.example.inherited.derived";

    @Test
    void preservesQualifiedBaseMetadataInInspector() {
        XsdParserInspector inspector = inspector();

        assertEquals(RegistryKey.of(BASE_NS, "trade_base"), inspector.getComplexTypeBaseKey(new QName(DERIVED_NS, "trade_derived")));
        InspectorAttributeNode baseValue = inspector.getComplexTypeStructuredMembers(new QName(BASE_NS, "trade_base")).stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .filter(member -> member.name().equals("baseValue"))
                .findFirst()
                .orElseThrow();
        assertEquals(BASE_NS, baseValue.xmlNamespace());
        assertEquals(BASE_NS + "/baseValue", baseValue.elementRefUri().orElseThrow());

        InspectorAttributeNode baseId = inspector.getComplexTypeAttributeMembers(new QName(BASE_NS, "trade_base")).stream()
                .filter(member -> member.name().equals("baseId"))
                .findFirst()
                .orElseThrow();
        assertEquals(BASE_NS, baseId.xmlNamespace());
        assertEquals(BASE_NS + "/baseId", baseId.elementRefUri().orElseThrow());
    }

    @Test
    void generatedDerivedConfigurationRetainsInheritedXmlMetadata() throws Exception {
        Path generatedOutput = generateScenarioModels();
        assertGeneratedModelContract(generatedOutput);
        JsonNode configuration = new ObjectMapper().readTree(
                generatedOutput.resolve("config/xml-config.json").toFile());
        JsonNode derivedType = configuration.path(DERIVED_PKG + ".TradeDerived").path("attributes");

        assertEquals("baseValue", derivedType.path("baseValue").path("xmlName").asText());
        assertEquals("ELEMENT", derivedType.path("baseValue").path("xmlRepresentation").asText());
        assertEquals(BASE_NS + "/baseValue", derivedType.path("baseValue").path("elementRef").asText());
        assertEquals("baseId", derivedType.path("baseId").path("xmlName").asText());
        assertEquals("ATTRIBUTE", derivedType.path("baseId").path("xmlRepresentation").asText());
        assertEquals(BASE_NS + "/baseId", derivedType.path("baseId").path("elementRef").asText());
        assertEquals("derivedValue", derivedType.path("derivedValue").path("xmlName").asText());
        assertEquals("derivedId", derivedType.path("derivedId").path("xmlName").asText());
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(BASE_NS)
                .addNamespaceMapping(BASE_NS, BASE_PKG)
                .addTargetNamespace(DERIVED_NS)
                .addNamespaceMapping(DERIVED_NS, DERIVED_PKG);
        return generateRuneModels(XSD_FILE, outputDirectory, config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("com_example_inherited_base-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("com_example_inherited_derived-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }

    private XsdParserInspector inspector() {
        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(XSD_FILE);
        return new XsdParserInspector(parser);
    }
}
