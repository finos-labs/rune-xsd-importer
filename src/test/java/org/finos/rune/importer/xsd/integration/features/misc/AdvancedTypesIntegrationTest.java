package org.finos.rune.importer.xsd.integration.features.misc;

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
 * Verifies representative XSD restrictions, enumerations, lists, mixed unions, user-defined restrictions, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: PARTIAL</p>
 * <p>Limitation: list values are represented through the public mapper's string fallback and union coverage does not claim exhaustive lexical-space or facet behavior.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("partial")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedTypesIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.invalid-sku.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.invalid-age.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.invalid-color.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/advanced-types.invalid-coordinates.xml"));
    private static final String TARGET_NS = "http://example.com/advanced";
    private static final String RUNE_PKG = "com.example.advanced";
    private static final String TARGET_RUNE_TYPE_NAME = "DataCollection";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, VALID_XML_FILE));
    }

    @Test
    void shouldRejectInvalidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertSamplesRejectedAgainstSchema(schema, INVALID_XML_FILES);
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

        Path generatedXmlPath = generatedOutput.resolve("xml/advanced-types.valid.xml");
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
        assertEquals(Set.of("com_example_advanced-type.rune", "com_example_advanced-enum.rune"), generatedRuneFiles);

        Path generatedTypeFile = generatedOutput.resolve("com_example_advanced-type.rune");
        Path generatedEnumFile = generatedOutput.resolve("com_example_advanced-enum.rune");
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedTypeFile));
        assertTrue(Files.exists(generatedEnumFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedType = Files.readString(generatedTypeFile, StandardCharsets.UTF_8);
        String generatedEnum = Files.readString(generatedEnumFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedType, "type DataCollection:", "productSku SKU (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "userAge Age (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "zip ZipCode (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "favColor Color (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "allScores NumberList (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "finalScore ScoreOrNA (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "coordinates ThreeNumbers (1..1)");
        assertDeclarationContains(generatedType, "type DataCollection:", "[rootType]");
        assertDeclarationContains(generatedType, "type ScoreOrNA:", "_int int (0..1)");
        assertDeclarationContains(generatedType, "type ScoreOrNA:", "_string string (0..1)");
        assertDeclarationContains(generatedType, "type ScoreOrNA:", "one-of");
        assertDeclarationContains(generatedType, "typeAlias SKU:", "string(pattern: \"[A-Z]{3}-\\\\d{3}\")");
        assertDeclarationContains(generatedType, "typeAlias Age:", "int(min: 0, max: 120)");
        assertDeclarationContains(generatedType, "typeAlias ZipCode:", "string(minLength: 5, maxLength: 5)");
        assertDeclarationContains(generatedType, "typeAlias NumberList(pattern string, minLength int, maxLength int):", "string(pattern:");
        assertDeclarationContains(generatedType, "typeAlias ThreeNumbers:", "NumberList(minLength: 3, maxLength: 3)");
        assertTrue(generatedEnum.contains("enum Color:"));
        assertTrue(generatedEnum.contains("RED displayName \"Red\""));
        assertTrue(generatedEnum.contains("GREEN displayName \"Green\""));
        assertTrue(generatedEnum.contains("BLUE displayName \"Blue\""));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode dataCollection = configuration.path(RUNE_PKG + ".DataCollection");
        assertFalse(dataCollection.isMissingNode());
        assertEquals("DataCollection", dataCollection.path("xmlElementName").asText());
        assertTrue(dataCollection.path("attributes").path("allScores").isObject());
        assertTrue(dataCollection.path("attributes").path("coordinates").isObject());
    }
}
