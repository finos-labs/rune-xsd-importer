package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubstitutionXsiTypeEmissionRegressionTest extends AbstractRuneIntegrationTest {
    private static final Path RESOURCE_ROOT = Path.of(
            "src/test/resources/org/finos/rune/xsd/regression/substitutionxsitype");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("substitution-xsi-type-emission.xsd");
    private static final Path DECLARED_SAMPLE = RESOURCE_ROOT.resolve("declared-swap.valid.xml");
    private static final Path DERIVED_SAMPLE = RESOURCE_ROOT.resolve("derived-swap.valid.xml");
    private static final Path XML_CONFIG = Path.of("config/xml-config.json");
    private static final String NAMESPACE = "http://example.com/substitution-xsi-type";
    private static final String RUNE_PACKAGE = "com.example.substitutionxsitype";
    private static final QName SPECIAL_SWAP = new QName(NAMESPACE, "SpecialSwap");

    @Test
    void emitsTypeOnlyForFurtherDerivedSubstitutionMemberWithGeneratedConfiguration() throws Exception {
        Path generatedConfig = getScenarioState().generatedOutput().resolve(XML_CONFIG);
        assertSubstitutionRoundTrips(generatedConfig);
    }

    @Test
    void emitsTypeOnlyForFurtherDerivedSubstitutionMemberWithAdjustedConfiguration() throws Exception {
        Path generatedConfig = getScenarioState().generatedOutput().resolve(XML_CONFIG);
        ObjectMapper json = new ObjectMapper();
        ObjectNode configuration = assertInstanceOf(ObjectNode.class, json.readTree(generatedConfig.toFile()));
        ObjectNode product = assertInstanceOf(ObjectNode.class, configuration.path(RUNE_PACKAGE + ".Product"));

        // Exercise abstract head metadata identified by its type QName.
        assertTrue(product.path("abstract").asBoolean(), "Expected Product to remain abstract");
        product.remove("xmlElementName");
        product.put("xmlElementFullyQualifiedName", NAMESPACE + "/Product");

        Path adjustedConfig = generatedConfig.resolveSibling("adjusted-xml-config.json");
        json.writeValue(adjustedConfig.toFile(), configuration);
        assertSubstitutionRoundTrips(adjustedConfig);
    }

    private void assertSubstitutionRoundTrips(Path configuration) throws Exception {
        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> tradeClass = state.compiledModel().getTypeJavaClass("Trade");
        assertNotNull(tradeClass, "Expected generated Trade model");
        ObjectMapper mapper = getXmlMapper(configuration, tradeClass.getClassLoader());
        Path output = state.generatedOutput().resolve("xml").resolve(configuration.getFileName().toString());
        Files.createDirectories(output);

        assertAll(
                () -> assertRoundTrip(DECLARED_SAMPLE, null, mapper, tradeClass, output),
                () -> assertRoundTrip(DERIVED_SAMPLE, SPECIAL_SWAP, mapper, tradeClass, output));
    }

    private void assertRoundTrip(Path input, QName expectedType, ObjectMapper mapper,
            Class<? extends RosettaModelObject> tradeClass, Path outputDirectory) throws Exception {
        var schema = getSourceSchema(XSD_FILE);
        XsdValidationUtils.validate(schema, input);

        String originalXml = Files.readString(input);
        RosettaModelObject trade = mapper.readValue(originalXml, tradeClass);
        String serializedXml = mapper.writeValueAsString(trade);
        Path output = Files.writeString(outputDirectory.resolve(input.getFileName()), serializedXml);

        assertAll(input.getFileName().toString(),
                () -> assertEquals(expectedType, readSwapXsiType(output), "Unexpected xsi:type on swap"),
                () -> XsdValidationUtils.validate(schema, output),
                () -> assertXmlEquals(originalXml, serializedXml));
    }

    private QName readSwapXsiType(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newNSInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var elements = factory.newDocumentBuilder().parse(xml.toFile()).getElementsByTagNameNS(NAMESPACE, "swap");
        assertEquals(1, elements.getLength(), "Expected one substitution element");
        Element swap = (Element) elements.item(0);
        Attr type = swap.getAttributeNodeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (type == null) {
            return null;
        }

        String value = type.getValue().strip();
        int separator = value.indexOf(':');
        String prefix = separator < 0 ? null : value.substring(0, separator);
        return new QName(swap.lookupNamespaceURI(prefix), value.substring(separator + 1));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(NAMESPACE)
                .addNamespaceMapping(NAMESPACE, RUNE_PACKAGE);
        return generateRuneModels(XSD_FILE, temporaryDirectory.resolve("generated"), config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.isRegularFile(generatedOutput.resolve(XML_CONFIG)), "Expected generated XML configuration");
    }
}
