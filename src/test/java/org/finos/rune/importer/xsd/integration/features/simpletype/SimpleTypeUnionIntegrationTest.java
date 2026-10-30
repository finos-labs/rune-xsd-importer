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
 * Verifies XSD simple-type unions through pure enumeration flattening, mixed union member generation, XML value routing, cardinality preservation, source-schema validation, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: PARTIAL</p>
 * <p>Limitation: the scenario covers representative pure and mixed unions, but it does not establish exhaustive behavior for overlapping lexical spaces, ambiguous member precedence, or native Rune choice declarations.</p>
 * <p>See: documentation/docs/capability-matrix.md, documentation/docs/mapping-reference.md, and documentation/docs/advanced-serialization.md</p>
 */
@Tag("partial")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleTypeUnionIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-union.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-union.1.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-union.2.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-union.invalid-value.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/simpletype/simpletype-union.invalid-effective-date.xml"));
    private static final String TARGET_NS = "http://example.com/rune/union";
    private static final String RUNE_PKG = "com.example.rune.simpletype.union";
    private static final String TARGET_RUNE_TYPE_NAME = "UnionTest";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_simpletype_union-type.rune";
    private static final String GENERATED_ENUM_FILE = "com_example_rune_simpletype_union-enum.rune";

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
        assertDeclarationContains(generatedEnum, "enum TradeStatusEnum:", "PENDING");
        assertDeclarationContains(generatedEnum, "enum TradeStatusEnum:", "SETTLED");
        assertDeclarationContains(generatedEnum, "enum TradeStatusEnum:", "_1 displayName \"1\"");
        assertDeclarationContains(generatedEnum, "enum TradeStatusEnum:", "_2 displayName \"2\"");
        assertDeclarationContains(generatedRune, "type CodeOrSmallInt:", "codeEnum CodeEnum (0..1)");
        assertDeclarationContains(generatedRune, "type CodeOrSmallInt:", "smallInt SmallInt (0..1)");
        assertDeclarationContains(generatedRune, "type DateOrTerm:", "date date (0..1)");
        assertDeclarationContains(generatedRune, "type DateOrTerm:", "termEnum TermEnum (0..1)");
        assertDeclarationContains(generatedRune, "type LimitOrMax:", "positiveInt PositiveInt (0..1)");
        assertDeclarationContains(generatedRune, "type LimitOrMax:", "maxToken MaxToken (0..1)");
        assertDeclarationContains(generatedRune, "type UnionType:", "value CodeOrSmallInt (1..1)");
        assertDeclarationContains(generatedRune, "type UnionType:", "tradeStatus TradeStatusEnum (0..1)");
        assertDeclarationContains(generatedRune, "type UnionType:", "effectiveDate DateOrTerm (1..*)");
        assertDeclarationContains(generatedRune, "type UnionType:", "isCleared BooleanOrNa (0..1)");
        assertDeclarationContains(generatedRune, "type UnionType:", "tradeLimit LimitOrMax (0..3)");
        assertDeclarationContains(generatedRune, "type UnionTest extends UnionType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode mixedUnion = configuration.path(RUNE_PKG + ".CodeOrSmallInt");
        assertFalse(mixedUnion.isMissingNode());
        assertEquals("VALUE", mixedUnion.path("attributes").path("codeEnum").path("xmlRepresentation").asText());
        assertEquals("VALUE", mixedUnion.path("attributes").path("smallInt").path("xmlRepresentation").asText());

        // Stage 5: pure enum-union values are shared by the Rune declaration
        // and XML configuration outputs.
        JsonNode enumUnion = configuration.path(RUNE_PKG + ".TradeStatusEnum");
        assertFalse(enumUnion.isMissingNode());
        assertEquals("PENDING", enumUnion.path("enumValues").path("PENDING").asText());
        assertEquals("SETTLED", enumUnion.path("enumValues").path("SETTLED").asText());
        assertEquals("1", enumUnion.path("enumValues").path("_1").asText());
        assertEquals("2", enumUnion.path("enumValues").path("_2").asText());
    }
}
