package org.finos.rune.importer.xsd.integration.features.namespace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import java.util.function.Predicate;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.finos.rune.importer.xsd.security.XsdParserJaxpFactory.defaultFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the effective XML namespaces of local elements and attributes in an XSD schema with omitted form defaults.
 *
 * <p>The fixture omits both schema form defaults, which means local elements and
 * attributes are unqualified unless their declaration overrides the default with
 * {@code form="qualified"}. It also includes a global element reference, whose
 * namespace must remain qualified independently of the containing type.</p>
 *
 * <p>Status: PENDING</p>
 * <p>See: documentation/docs/capability-matrix.md</p>
 */
@Tag("pending")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalFormQualificationIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/namespace/local-form-defaults.xsd");
    private static final String TARGET_NS = "urn:example:trade:form-defaults";
    private static final String RUNE_PKG = "com.example.trade.formdefaults";
    @Test
    void shouldCompileSourceSchema() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    @Tag("pending")
    @Disabled("Effective local element and attribute namespaces are not yet resolved from elementFormDefault, attributeFormDefault, and form overrides; see the capability matrix.")
    void shouldInspectEffectiveNamespacesForLocalMembers() {
        XsdParser parser = defaultFactory().createParser(XSD_FILE);
        XsdParserInspector inspector = new XsdParserInspector(parser);

        Map<String, InspectorAttributeNode> elements = inspector
                .getComplexTypeStructuredMembers(new QName(TARGET_NS, "Trade"))
                .stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .collect(java.util.stream.Collectors.toMap(InspectorAttributeNode::name, node -> node));

        assertEquals("", elements.get("defaultElement").xmlNamespace());
        assertEquals(TARGET_NS, elements.get("qualifiedElement").xmlNamespace());
        assertEquals("", elements.get("unqualifiedElement").xmlNamespace());
        assertEquals(TARGET_NS, elements.get("globalElement").xmlNamespace());
        assertEquals(TARGET_NS + "/globalElement", elements.get("globalElement").elementRefUri().orElseThrow());

        Map<String, InspectorAttributeNode> attributes = inspector
                .getComplexTypeAttributeMembers(new QName(TARGET_NS, "Trade"))
                .stream()
                .collect(java.util.stream.Collectors.toMap(InspectorAttributeNode::name, node -> node));

        assertEquals("", attributes.get("version").xmlNamespace());
        assertEquals(TARGET_NS, attributes.get("qualifiedSource").xmlNamespace());
        assertEquals("", attributes.get("unqualifiedSource").xmlNamespace());
    }

    @Test
    void shouldAcceptValidSamplesAgainstSourceSchema() throws Exception {
        var schema = getSourceSchema(XSD_FILE);
        for (String sample : Set.of(
                "local-form-defaults.valid.xml",
                "local-form-defaults.valid-alternate.xml")) {
            org.finos.rune.importer.xsd.XsdValidationUtils.validate(
                    schema,
                    XSD_FILE.resolveSibling(sample));
        }
    }

    @Test
    @Tag("pending")
    @Disabled("Effective local element and attribute namespaces are not yet preserved in generated XML configuration; see the capability matrix.")
    void shouldGenerateConfigurationWithEffectiveNamespaces() throws Exception {
        Path generatedOutput = getScenarioState().generatedOutput();
        JsonNode configuration = new ObjectMapper().readTree(
                generatedOutput.resolve("config/xml-config.json").toFile());
        JsonNode tradeType = findNode(configuration,
                node -> node.path("attributes").has("defaultElement"));
        assertNotNull(tradeType);

        assertEquals("defaultElement", tradeType.path("attributes").path("defaultElement").path("xmlName").asText());
        assertEquals("qualifiedElement", tradeType.path("attributes").path("qualifiedElement").path("xmlName").asText());
        assertEquals(TARGET_NS + "/qualifiedElement",
                tradeType.path("attributes").path("qualifiedElement").path("elementRef").asText());
        assertEquals("unqualifiedElement", tradeType.path("attributes").path("unqualifiedElement").path("xmlName").asText());
        assertEquals(TARGET_NS + "/globalElement",
                tradeType.path("attributes").path("globalElement").path("elementRef").asText());
        assertEquals("", tradeType.path("attributes").path("defaultElement").path("elementRef").asText());

        assertEquals("", tradeType.path("attributes").path("version").path("elementRef").asText());
        assertEquals(TARGET_NS + "/qualifiedSource",
                tradeType.path("attributes").path("qualifiedSource").path("elementRef").asText());
        assertEquals("", tradeType.path("attributes").path("unqualifiedSource").path("elementRef").asText());
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(TARGET_NS)
                .addNamespaceMapping(TARGET_NS, RUNE_PKG);
        return generateRuneModels(XSD_FILE, outputDirectory, config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) throws IOException {
        Path runeFile = generatedOutput.resolve("com_example_trade_formdefaults-type.rune");
        assertTrue(Files.exists(runeFile));
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));

        String generatedRune = Files.readString(runeFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedRune, "type Trade:", "defaultElement string (1..1)");
        assertDeclarationContains(generatedRune, "type Trade:", "qualifiedElement string (1..1)");
        assertDeclarationContains(generatedRune, "type Trade:", "unqualifiedElement string (1..1)");
        assertDeclarationContains(generatedRune, "type Trade:", "globalElement GlobalElement (1..1)");
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, RUNE_PKG));
    }

    private JsonNode findNode(JsonNode node, Predicate<JsonNode> predicate) {
        if (predicate.test(node)) {
            return node;
        }
        if (node.isContainerNode()) {
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                JsonNode match = findNode(children.next(), predicate);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
