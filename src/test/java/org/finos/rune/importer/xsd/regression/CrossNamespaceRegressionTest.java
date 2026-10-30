package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.namespace.QName;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for a global element whose declared type belongs to an imported namespace.
 *
 * <p>This is intentionally a focused regression test rather than a full public integration scenario.
 * It covers qualified type identity and the affected generated artifacts without adding the broader
 * source-schema validation and XML round-trip contract used by the documented integration suite.</p>
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossNamespaceRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/regression/crossnamespace/messages.xsd");
    private static final String TYPES_NS = "urn:example:cross:types";
    private static final String MESSAGES_NS = "urn:example:cross:messages";
    private static final String TYPES_PKG = "com.example.cross.types";
    private static final String MESSAGES_PKG = "com.example.cross.messages";

    @Test
    void resolvesGlobalElementTypeToTheImportedQualifiedType() {
        XsdParserInspector inspector = inspector();

        assertEquals(RegistryKey.of(TYPES_NS, "Trade"), inspector.getElementTypeKey(new QName(MESSAGES_NS, "trade")));
        assertTrue(inspector.getComplexTypeStructuredMembers(new QName(MESSAGES_NS, "Trade")).isEmpty(),
                "The messages namespace must not contain a synthetic local Trade declaration");
    }

    @Test
    void generatedElementUsesTheImportedTypeAndItsMembers() throws Exception {
        ScenarioState state = getScenarioState();
        Path generatedOutput = state.generatedOutput();
        String typesRune = Files.readString(
                generatedOutput.resolve("com_example_cross_types-type.rune"), StandardCharsets.UTF_8);
        String messagesRune = Files.readString(
                generatedOutput.resolve("com_example_cross_messages-type.rune"), StandardCharsets.UTF_8);

        assertDeclarationContains(typesRune, "type Trade:", "tradeId string (1..1)");
        assertTrue(messagesRune.contains("import " + TYPES_PKG + ".*"));
        assertDeclarationContains(messagesRune, "type Trade extends " + TYPES_PKG + ".Trade:", "[rootType]");

        JsonNode configuration = new ObjectMapper().readTree(
                generatedOutput.resolve("config/xml-config.json").toFile());
        assertFalse(configuration.path(TYPES_PKG + ".Trade").isMissingNode());
        assertEquals("trade", configuration.path(MESSAGES_PKG + ".Trade").path("xmlElementName").asText());

        Class<?> generatedTrade = state.compiledModel().getTypeJavaClass("Trade");
        assertEquals(MESSAGES_PKG + ".Trade", generatedTrade.getName());
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, MESSAGES_PKG));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(TYPES_NS)
                .addNamespaceMapping(TYPES_NS, TYPES_PKG)
                .addTargetNamespace(MESSAGES_NS)
                .addNamespaceMapping(MESSAGES_NS, MESSAGES_PKG);
        return generateRuneModels(XSD_FILE, outputDirectory, config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("com_example_cross_types-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("com_example_cross_messages-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }

    private XsdParserInspector inspector() {
        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(XSD_FILE);
        return new XsdParserInspector(parser);
    }
}
