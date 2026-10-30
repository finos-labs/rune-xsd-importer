package org.finos.rune.importer.xsd.integration.features.choice;

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
 * Verifies a named model group used as one branch of an XSD choice, virtual group composition, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: PARTIAL</p>
 * <p>Limitation: this scenario does not claim coverage for repeated or nested choices, arbitrary branch ordering, or native Rune choice declarations.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("partial")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupChoiceIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/choice/group-choice.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/choice/group-choice.element-branch.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/choice/group-choice.group-branch.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/choice/group-choice.invalid-multiple-branches.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/groups/choice";
    private static final String RUNE_PKG = "com.example.rune.test.groups._choice";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_test_groups__choice-type.rune";
    private static final String TARGET_RUNE_TYPE_NAME = "TestGroupChoice";

    @Test
    void shouldCompileSourceSchema() throws Exception {
        assertNotNull(getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        for (Path validXmlFile : VALID_XML_FILES) {
            XsdValidationUtils.validate(schema, validXmlFile);
        }
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

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        Path generatedXmlDirectory = state.generatedOutput().resolve("xml");
        Files.createDirectories(generatedXmlDirectory);
        for (Path validXmlFile : VALID_XML_FILES) {
            String originalXml = Files.readString(validXmlFile, StandardCharsets.UTF_8);
            Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
            String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);
            Path generatedXmlPath = generatedXmlDirectory.resolve(validXmlFile.getFileName());
            Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

            XsdValidationUtils.validate(schema, generatedXmlPath);
            assertXmlEquals(originalXml, generatedXml);
        }
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
        assertDeclarationContains(generatedRune, "type GroupChoiceType:", "beforeChoice boolean (1..1)");
        assertDeclarationContains(generatedRune, "type GroupChoiceType:", "choiceAltDate date (0..1)");
        assertDeclarationContains(generatedRune, "type GroupChoiceType:", "choiceGroupFields ChoiceGroupFields (0..1)");
        assertDeclarationContains(generatedRune, "type GroupChoiceType:", "afterChoice number (1..1)");
        assertDeclarationContains(generatedRune, "type GroupChoiceType:", "required choice choiceAltDate, choiceGroupFields");
        assertDeclarationContains(generatedRune, "type ChoiceGroupFields:", "choiceCode ChoiceCode (1..1)");
        assertDeclarationContains(generatedRune, "type ChoiceGroupFields:", "choiceInt int (0..1)");
        assertDeclarationContains(generatedRune, "type TestGroupChoice extends GroupChoiceType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode root = configuration.path(RUNE_PKG + ".TestGroupChoice");
        assertFalse(root.isMissingNode());
        assertEquals("testGroupChoice", root.path("xmlElementName").asText());
        assertEquals("VIRTUAL", configuration.path(RUNE_PKG + ".GroupChoiceType").path("attributes").path("choiceGroupFields").path("xmlRepresentation").asText());
        assertEquals(TARGET_NS + "/choiceCode",
                configuration.path(RUNE_PKG + ".ChoiceGroupFields").path("attributes").path("choiceCode").path("elementRef").asText());
    }
}
