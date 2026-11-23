package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.XmlSerializationContractAssertions;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepeatedSubstitutionXsiTypeRegressionTest extends AbstractRuneIntegrationTest {
    private static final Path FIXTURES = Path.of(
            "src/test/resources/org/finos/rune/xsd/regression/substitutionxsitype");
    private static final Path XSD = FIXTURES.resolve("repeated-substitution.xsd");
    private static final Path INPUT = FIXTURES.resolve("mixed-swap-list.valid.xml");
    private static final Path CONFIG = Path.of("config/xml-config.json");
    private static final String NAMESPACE = "http://example.com/substitution-xsi-type";
    private static final String RUNE_PACKAGE = "com.example.substitutionxsitype";

    @Test
    void preservesSubstitutionTagsAndTypesWithGeneratedConfiguration() throws Exception {
        Path generatedConfig = getScenarioState().generatedOutput().resolve(CONFIG);
        assertMixedListRoundTrip(generatedConfig, "mixed-swap-list.generated.roundtrip.xml");
    }

    @Test
    void preservesSubstitutionTagsAndTypesWithAdjustedConfiguration() throws Exception {
        Path generatedConfig = getScenarioState().generatedOutput().resolve(CONFIG);
        ObjectMapper json = new ObjectMapper();
        ObjectNode configuration = assertInstanceOf(ObjectNode.class, json.readTree(generatedConfig.toFile()));
        ObjectNode product = assertInstanceOf(ObjectNode.class, configuration.path(RUNE_PACKAGE + ".Product"));

        // Identify the abstract head by its type QName, as in the scalar regression.
        assertTrue(product.path("abstract").asBoolean(), "Expected Product to remain abstract");
        product.remove("xmlElementName");
        product.put("xmlElementFullyQualifiedName", NAMESPACE + "/Product");

        Path adjustedConfig = generatedConfig.resolveSibling("adjusted-xml-config.json");
        json.writeValue(adjustedConfig.toFile(), configuration);
        assertMixedListRoundTrip(adjustedConfig, "mixed-swap-list.adjusted.roundtrip.xml");
    }

    private void assertMixedListRoundTrip(Path configuration, String outputFilename) throws Exception {
        var schema = getSourceSchema(XSD);
        XsdValidationUtils.validate(schema, INPUT);

        ScenarioState state = getScenarioState();
        var tradeClass = state.compiledModel().getTypeJavaClass("Trade");
        var swapClass = state.compiledModel().getTypeJavaClass("Swap");
        var specialSwapClass = state.compiledModel().getTypeJavaClass("SpecialSwap");
        assertNotNull(tradeClass, "Expected generated Trade model");
        assertNotNull(swapClass, "Expected generated Swap model");
        assertNotNull(specialSwapClass, "Expected generated SpecialSwap model");

        var mapper = getXmlMapper(configuration, tradeClass.getClassLoader());
        String original = Files.readString(INPUT);
        RosettaModelObject trade = mapper.readValue(original, tradeClass);
        var products = (List<?>) tradeClass.getMethod("getProduct").invoke(trade);
        assertEquals(List.of(swapClass, specialSwapClass, swapClass), products.stream()
                .map(RosettaModelObject.class::cast)
                .map(RosettaModelObject::getType)
                .toList(), "Expected the middle list item to deserialize as SpecialSwap");

        String serialized = mapper.writeValueAsString(trade);
        Path output = Files.writeString(state.generatedOutput().resolve(outputFilename), serialized);
        assertAll("serialized list",
                () -> assertSwapTagsAndTypes(output),
                () -> XmlSerializationContractAssertions.assertRootScopedNamespaceDeclarations(serialized),
                () -> XsdValidationUtils.validate(schema, output),
                () -> assertXmlEquals(original, serialized));
    }

    private void assertSwapTagsAndTypes(Path output) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newNSInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var swaps = factory.newDocumentBuilder().parse(output.toFile()).getElementsByTagNameNS(NAMESPACE, "swap");
        assertEquals(3, swaps.getLength(), "Expected three qualified substitution tags");

        var first = (Element) swaps.item(0);
        var middle = (Element) swaps.item(1);
        var last = (Element) swaps.item(2);
        assertAll(
                () -> assertEquals(List.of("plain-first", "derived", "plain-last"),
                        List.of(identifier(first), identifier(middle), identifier(last))),
                () -> assertNull(readXsiType(first), "First Swap uses its declared type"),
                () -> assertEquals(new QName(NAMESPACE, "SpecialSwap"), readXsiType(middle),
                        "Middle Swap requires its further-derived xsi:type"),
                () -> assertNull(readXsiType(last), "Last Swap uses its declared type"));
    }

    private String identifier(Element swap) {
        var identifier = swap.getElementsByTagNameNS(NAMESPACE, "identifier").item(0);
        assertNotNull(identifier, "Expected a qualified identifier on swap");
        return identifier.getTextContent();
    }

    private QName readXsiType(Element swap) {
        var attribute = swap.getAttributeNodeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        if (attribute == null) {
            return null;
        }
        String value = attribute.getValue().strip();
        int colon = value.indexOf(':');
        String prefix = colon < 0 ? null : value.substring(0, colon);
        return new QName(swap.lookupNamespaceURI(prefix), value.substring(colon + 1));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(NAMESPACE)
                .addNamespaceMapping(NAMESPACE, RUNE_PACKAGE);
        return generateRuneModels(XSD, temporaryDirectory.resolve("generated"), config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.isRegularFile(generatedOutput.resolve(CONFIG)), "Expected generated XML configuration");
    }
}
