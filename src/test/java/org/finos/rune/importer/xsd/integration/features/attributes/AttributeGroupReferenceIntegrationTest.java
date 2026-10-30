package org.finos.rune.importer.xsd.integration.features.attributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies reusable attribute-group references, required and optional attribute cardinalities, default and fixed values, local attributes, XML configuration, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one reusable attribute group and does not claim exhaustive default, fixed, or attribute-use coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttributeGroupReferenceIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/attribute-group-reference.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/attribute-group-reference.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/attribute-group-reference.invalid-missing-required.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/attributes/attribute-group-reference.invalid-fixed.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/attrs/group";
    private static final String RUNE_PKG = "com.rune.test.attrs.group";
    private static final String TARGET_RUNE_TYPE_NAME = "TestAttrGroup";
    private static final String GENERATED_RUNE_FILE = "com_rune_test_attrs_group-type.rune";

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
        Schema schema = getSourceSchema(XSD_FILE);
        ScenarioState state = getScenarioState();
        Path generatedOutput = state.generatedOutput();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(generatedOutput.resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = generatedOutput.resolve("xml/attribute-group-reference.valid.xml");
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

        XsdValidationUtils.validate(schema, generatedXmlPath);
        assertXmlEquals(originalXml, generatedXml);
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
                    .collect(java.util.stream.Collectors.toSet());
        }
        assertEquals(Set.of(GENERATED_RUNE_FILE), generatedRuneFiles);

        Path generatedRuneFile = generatedOutput.resolve(GENERATED_RUNE_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedRune = Files.readString(generatedRuneFile, StandardCharsets.UTF_8);
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "agRequired string (1..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "agOptional int (0..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "agDefault string (0..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "agFixed string (0..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "localAttr boolean (1..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type AttrGroupType:", "value string (1..1)");
        RuneTestModelUtils.assertDeclarationContains(generatedRune, "type TestAttrGroup extends AttrGroupType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode attributes = configuration.path(RUNE_PKG + ".AttrGroupType").path("attributes");
        assertFalse(attributes.isMissingNode());
        assertAttributeConfig(attributes, "agRequired", "ATTRIBUTE");
        assertAttributeConfig(attributes, "agOptional", "ATTRIBUTE");
        assertAttributeConfig(attributes, "agDefault", "ATTRIBUTE");
        assertAttributeConfig(attributes, "agFixed", "ATTRIBUTE");
        assertAttributeConfig(attributes, "localAttr", "ATTRIBUTE");
        assertAttributeConfig(attributes, "value", "ELEMENT");
    }

    private void assertAttributeConfig(JsonNode attributesNode, String attributeName, String expectedRepresentation) {
        assertTrue(attributesNode.has(attributeName), "XML Config missing entry for: " + attributeName);
        assertEquals(expectedRepresentation, attributesNode.get(attributeName).get("xmlRepresentation").asText(),
                "Incorrect xmlRepresentation for " + attributeName);
    }
}
