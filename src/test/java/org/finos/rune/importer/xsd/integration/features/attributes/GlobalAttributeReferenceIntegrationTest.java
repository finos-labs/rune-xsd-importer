package org.finos.rune.importer.xsd.integration.features.attributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.xml.validation.Schema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies global attribute references, required and optional cardinalities, local attributes, XML configuration, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one qualified global-attribute reference and does not claim exhaustive global-attribute, namespace, or attribute-use coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalAttributeReferenceIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/global-attribute-reference.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/global-attribute-reference.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/global-attribute-reference.invalid-missing-required.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/global-attribute-reference.invalid-integer.xml"));
    private static final String TARGET_NS = "http://example.com/rune/attrref";
    private static final String RUNE_PKG = "example.com.rune.attrref";
    private static final String TARGET_RUNE_TYPE_NAME = "GlobalAttributeReference";
    private static final String GENERATED_RUNE_FILE = "example_com_rune_attrref-type.rune";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        assertDoesNotThrow(() -> XsdValidationUtils.validate(getSourceSchema(XSD_FILE), VALID_XML_FILE));
    }

    @Test
    void shouldRejectInvalidSamplesAgainstSchema() throws Exception {
        assertSamplesRejectedAgainstSchema(getSourceSchema(XSD_FILE), INVALID_XML_FILES);
    }

    @Test
    void shouldGenerateRuneModelsAndConfiguration() throws Exception {
        assertNotNull(getScenarioState().generatedOutput());
    }

    @Test
    void shouldCompileGeneratedRuneModel() throws Exception {
        JavaTestModel compiledModel = getScenarioState().compiledModel();
        assertNotNull(compiledModel);
        assertNotNull(compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME));
    }

    @Test
    void shouldRoundTripValidSamples() throws Exception {

        ScenarioState state = getScenarioState();
        Path generatedOutput = state.generatedOutput();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(generatedOutput.resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = generatedOutput.resolve("xml/global-attribute-reference.valid.xml");
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

        assertXmlEquals(originalXml, generatedXml);
    }

    @Test
    void shouldValidateGeneratedXmlAgainstSourceSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = state.generatedOutput().resolve("xml/global-attribute-reference.valid.xml");
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);
        XsdValidationUtils.validate(schema, generatedXmlPath);
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
        Set<String> generatedRuneFiles;
        try (var generatedFiles = Files.walk(generatedOutput)) {
            generatedRuneFiles = generatedFiles
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".rune"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
        assertEquals(Set.of(GENERATED_RUNE_FILE), generatedRuneFiles);

        Path generatedRuneFile = generatedOutput.resolve(GENERATED_RUNE_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedRune = Files.readString(generatedRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedRune, "type GlobalAttributeReferenceType:", "globalId string (1..1)");
        assertDeclarationContains(generatedRune, "type GlobalAttributeReferenceType:", "globalCode int (0..1)");
        assertDeclarationContains(generatedRune, "type GlobalAttributeReferenceType:", "localFlag boolean (0..1)");
        assertDeclarationContains(generatedRune, "type GlobalAttributeReferenceType:", "name string (1..1)");
        assertDeclarationContains(generatedRune, "type GlobalAttributeReference extends GlobalAttributeReferenceType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode attributes = configuration.path(RUNE_PKG + ".GlobalAttributeReferenceType").path("attributes");
        assertFalse(attributes.isMissingNode());
        assertAttributeConfig(attributes, "globalId", "ATTRIBUTE", TARGET_NS + "/globalId");
        assertAttributeConfig(attributes, "globalCode", "ATTRIBUTE", TARGET_NS + "/globalCode");
        assertAttributeConfig(attributes, "localFlag", "ATTRIBUTE");
        assertAttributeConfig(attributes, "name", "ELEMENT");

        JsonNode rootXmlAttributes = configuration
                .path(RUNE_PKG + ".GlobalAttributeReference")
                .path("xmlAttributes");
        assertTrue(rootXmlAttributes.fields().hasNext());
        assertTrue(StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(rootXmlAttributes.fields(), 0), false)
                .anyMatch(entry -> TARGET_NS.equals(entry.getValue().asText())),
                "Root XML configuration must declare the namespace used by qualified global attributes");
    }

    private void assertAttributeConfig(JsonNode attributesNode, String attributeName, String expectedRepresentation) {
        assertAttributeConfig(attributesNode, attributeName, expectedRepresentation, null);
    }

    private void assertAttributeConfig(
            JsonNode attributesNode,
            String attributeName,
            String expectedRepresentation,
            String expectedElementRef) {
        assertTrue(attributesNode.has(attributeName), "XML Config missing entry for: " + attributeName);
        assertEquals(expectedRepresentation, attributesNode.get(attributeName).get("xmlRepresentation").asText(),
                "Incorrect xmlRepresentation for " + attributeName);
        if (expectedElementRef != null) {
            assertEquals(expectedElementRef, attributesNode.get(attributeName).get("elementRef").asText(),
                    "Incorrect qualified XML identity for " + attributeName);
        }
    }
}
