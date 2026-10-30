package org.finos.rune.importer.xsd.integration.features.groups;

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

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies FpML-shaped dotted model-group names with qualified references
 * through a nested sequence and a nested group-to-group reference.
 *
 * <p>Status: VERIFIED</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DottedGroupNestingIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/groups/dotted-group-nesting.xsd");
    private static final Path VALID_XML_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/groups/dotted-group-nesting.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/groups/dotted-group-nesting.invalid-missing-member.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/groups/dotted";
    private static final String RUNE_PKG = "com.example.rune.test.groups.dotted";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_test_groups_dotted-type.rune";
    private static final String TARGET_RUNE_TYPE_NAME = "DottedGroupNesting";

    @Test
    void shouldCompileSourceSchema() throws Exception {
        assertNotNull(getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, VALID_XML_FILE));
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
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = state.generatedOutput().resolve("xml/dotted-group-nesting.valid.xml");
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
        assertDeclarationContains(generatedRune, "type DottedGroupNestingType:", "beforeGroup number (1..1)");
        assertDeclarationContains(generatedRune, "type DottedGroupNestingType:", "outerModel OuterModel (1..1)");
        assertDeclarationContains(generatedRune, "type DottedGroupNestingType:", "afterGroup string (1..1)");
        assertDeclarationContains(generatedRune, "type OuterModel:", "outerFlag boolean (1..1)");
        assertDeclarationContains(generatedRune, "type OuterModel:", "innerModel InnerModel (1..1)");
        assertDeclarationContains(generatedRune, "type OuterModel:", "outerDate date (1..1)");
        assertDeclarationContains(generatedRune, "type InnerModel:", "innerCode string (1..1)");
        assertDeclarationContains(generatedRune, "type InnerModel:", "innerCount int (0..1)");
        assertDeclarationContains(generatedRune, "type DottedGroupNesting extends DottedGroupNestingType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertFalse(configuration.path(RUNE_PKG + ".DottedGroupNesting").isMissingNode());
        assertEquals("VIRTUAL", configuration.path(RUNE_PKG + ".DottedGroupNestingType")
                .path("attributes").path("outerModel").path("xmlRepresentation").asText());
        assertEquals("VIRTUAL", configuration.path(RUNE_PKG + ".OuterModel")
                .path("attributes").path("innerModel").path("xmlRepresentation").asText());
    }
}
