package org.finos.rune.importer.xsd.integration.features.simpletype;

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

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies named XSD simple-type restrictions for enumerations, string pattern and length facets, decimal bounds and digits, and integer bounds, including source-schema validation, generated Rune aliases, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario verifies representative restriction facets and does not claim exhaustive XSD restriction or runtime facet coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleTypeRestrictionIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-restriction.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-restriction.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-restriction.invalid-enum.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-restriction.invalid-pattern.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-restriction.invalid-range.xml"));
    private static final String TARGET_NS = "http://example.com/rune/restriction";
    private static final String RUNE_PKG = "com.example.rune.simpletype.restriction";
    private static final String TARGET_RUNE_TYPE_NAME = "RestrictionTest";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_simpletype_restriction-type.rune";
    private static final String GENERATED_ENUM_FILE = "com_example_rune_simpletype_restriction-enum.rune";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        for (Path validXmlFile : VALID_XML_FILES) {
            assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, validXmlFile), "Expected schema acceptance for " + validXmlFile);
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
        for (Path validXmlFile : VALID_XML_FILES) {
            String originalXml = Files.readString(validXmlFile, StandardCharsets.UTF_8);
            Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
            String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

            Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(validXmlFile.getFileName());
            Files.createDirectories(generatedXmlPath.getParent());
            Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

            XsdValidationUtils.validate(schema, generatedXmlPath);
            assertXmlEquals(originalXml, generatedXml);
        }
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, RUNE_PKG));
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
        assertEquals(Set.of(GENERATED_RUNE_FILE, GENERATED_ENUM_FILE), generatedRuneFiles);

        Path generatedRuneFile = generatedOutput.resolve(GENERATED_RUNE_FILE);
        Path generatedEnumFile = generatedOutput.resolve(GENERATED_ENUM_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedRuneFile));
        assertTrue(Files.exists(generatedEnumFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedRune = Files.readString(generatedRuneFile, StandardCharsets.UTF_8);
        String generatedEnum = Files.readString(generatedEnumFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedEnum, "enum SideEnum:", "BUY");
        assertDeclarationContains(generatedEnum, "enum SideEnum:", "SELL");
        assertDeclarationContains(generatedRune, "typeAlias AlphaCode:", "pattern: \"[A-Z]{3}\"");
        assertDeclarationContains(generatedRune, "typeAlias AlphaCode:", "minLength: 3");
        assertDeclarationContains(generatedRune, "typeAlias AlphaCode:", "maxLength: 3");
        assertDeclarationContains(generatedRune, "typeAlias Amount2dp:", "min: 0");
        assertDeclarationContains(generatedRune, "typeAlias Amount2dp:", "digits: 18");
        assertDeclarationContains(generatedRune, "typeAlias Amount2dp:", "fractionalDigits: 2");
        assertDeclarationContains(generatedRune, "typeAlias SmallInt:", "min: -10");
        assertDeclarationContains(generatedRune, "typeAlias SmallInt:", "max: 10");
        assertDeclarationContains(generatedRune, "type RestrictionType:", "side SideEnum (1..1)");
        assertDeclarationContains(generatedRune, "type RestrictionType:", "ccy AlphaCode (1..1)");
        assertDeclarationContains(generatedRune, "type RestrictionType:", "amount Amount2dp (1..1)");
        assertDeclarationContains(generatedRune, "type RestrictionType:", "qty SmallInt (1..1)");
        assertDeclarationContains(generatedRune, "type RestrictionTest extends RestrictionType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode restrictionType = configuration.path(RUNE_PKG + ".RestrictionType");
        assertFalse(restrictionType.isMissingNode());
        assertEquals("side", restrictionType.path("attributes").path("side").path("xmlName").asText());
        assertEquals("ccy", restrictionType.path("attributes").path("ccy").path("xmlName").asText());
        assertEquals("amount", restrictionType.path("attributes").path("amount").path("xmlName").asText());
        assertEquals("qty", restrictionType.path("attributes").path("qty").path("xmlName").asText());
    }
}
