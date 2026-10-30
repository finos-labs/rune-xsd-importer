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
 * Verifies nested same-namespace model-group composition, virtual group references, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario does not claim coverage for repeated, recursive, cross-namespace, or choice-composed model groups; the unrelated xs:QName value is represented as a string.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupNestingIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/groups/group-nesting.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/groups/group-nesting.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/groups/group-nesting.invalid-missing-nested-member.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/groups/nesting";
    private static final String RUNE_PKG = "com.example.rune.test.groups.nesting";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_test_groups_nesting-type.rune";
    private static final String TARGET_RUNE_TYPE_NAME = "TestGroupNesting";

    @Test
    void shouldCompileSourceSchema() throws Exception {
        assertNotNull(getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        assertNotNull(getSourceSchema(XSD_FILE));
        XsdValidationUtils.validate(getSourceSchema(XSD_FILE), VALID_XML_FILE);
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
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = state.generatedOutput().resolve("xml/group-nesting.valid.xml");
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
        assertDeclarationContains(generatedRune, "type GroupNestingType:", "beforeNested number (1..1)");
        assertDeclarationContains(generatedRune, "type GroupNestingType:", "nestedFields NestedFields (1..1)");
        assertDeclarationContains(generatedRune, "type GroupNestingType:", "afterNested string (1..1)");
        assertDeclarationContains(generatedRune, "type NestedFields:", "nestedBool boolean (1..1)");
        assertDeclarationContains(generatedRune, "type NestedFields:", "baseFields BaseFields (1..1)");
        assertDeclarationContains(generatedRune, "type NestedFields:", "nestedDate date (1..1)");
        assertDeclarationContains(generatedRune, "type BaseFields:", "baseString string (1..1)");
        assertDeclarationContains(generatedRune, "type BaseFields:", "baseCode BaseCode (1..1)");
        assertDeclarationContains(generatedRune, "type BaseFields:", "baseInt int (0..1)");
        assertDeclarationContains(generatedRune, "type TestGroupNesting extends GroupNestingType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode root = configuration.path(RUNE_PKG + ".TestGroupNesting");
        assertFalse(root.isMissingNode());
        assertEquals("testGroupNesting", root.path("xmlElementName").asText());
        assertEquals("VIRTUAL", configuration.path(RUNE_PKG + ".GroupNestingType").path("attributes").path("nestedFields").path("xmlRepresentation").asText());
        assertEquals("VIRTUAL", configuration.path(RUNE_PKG + ".NestedFields").path("attributes").path("baseFields").path("xmlRepresentation").asText());
        assertEquals(TARGET_NS + "/baseCode",
                configuration.path(RUNE_PKG + ".BaseFields").path("attributes").path("baseCode").path("elementRef").asText());
    }
}
