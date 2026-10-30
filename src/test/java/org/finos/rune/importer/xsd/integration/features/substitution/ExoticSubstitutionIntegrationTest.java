package org.finos.rune.importer.xsd.integration.features.substitution;

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
 * Verifies direct and multi-level substitution groups, abstract heads, native-wrapper substitution, referenced container elements, XML configuration, generated-model compilation, source-schema validation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers representative same-namespace substitution groups and does not claim exhaustive root polymorphism, cross-namespace substitution, wildcard, or namespace-prefix preservation coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExoticSubstitutionIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/substitution/exotic-substitution.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/substitution/exotic-substitution.basic.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/substitution/exotic-substitution.multi-level.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/substitution/exotic-substitution.invalid-abstract-head.xml"));
    private static final String TARGET_NS = "http://example.com/rune/exotic-subst";
    private static final String RUNE_PKG = "com.example.rune.exotic.subst";
    private static final String TARGET_RUNE_TYPE_NAME = "ExoticTest";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_exotic_subst-type.rune";

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
        assertDeclarationContains(generatedRune, "type VehicleType:", "make string (1..1)");
        assertDeclarationContains(generatedRune, "type CarType extends VehicleType:", "doors int (1..1)");
        assertDeclarationContains(generatedRune, "type InstrumentType:", "id string (1..1)");
        assertDeclarationContains(generatedRune, "type EquityType extends InstrumentType:", "ticker string (1..1)");
        assertDeclarationContains(generatedRune, "type StockType extends EquityType:", "votingRights boolean (1..1)");
        assertDeclarationContains(generatedRune, "type ExoticContainerType:", "vehicle VehicleType (1..*)");
        assertDeclarationContains(generatedRune, "type ExoticContainerType:", "instrument InstrumentType (1..*)");
        assertDeclarationContains(generatedRune, "type ExoticContainerType:", "identifier Identifier (1..*)");
        assertDeclarationContains(generatedRune, "type Identifier:", "value string (1..1)");
        assertDeclarationContains(generatedRune, "type Passport extends Identifier:", "[rootType]");
        assertDeclarationContains(generatedRune, "type ExoticTest extends ExoticContainerType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertSubstitutionGroup(configuration, "Car", TARGET_NS + "/vehicle");
        assertSubstitutionGroup(configuration, "Equity", TARGET_NS + "/instrument");
        assertSubstitutionGroup(configuration, "Stock", TARGET_NS + "/equity");
        assertSubstitutionGroup(configuration, "Passport", TARGET_NS + "/identifier");

        JsonNode containerAttributes = configuration.path(RUNE_PKG + ".ExoticContainerType").path("attributes");
        assertFalse(containerAttributes.isMissingNode());
        assertElementReference(containerAttributes, "vehicle", TARGET_NS + "/vehicle");
        assertElementReference(containerAttributes, "instrument", TARGET_NS + "/instrument");
        assertElementReference(containerAttributes, "identifier", TARGET_NS + "/identifier");
    }

    private void assertSubstitutionGroup(JsonNode configuration, String typeName, String expectedGroup) {
        JsonNode typeConfiguration = configuration.path(RUNE_PKG + "." + typeName);
        assertEquals(expectedGroup, typeConfiguration.path("substitutionGroup").asText());
    }

    private void assertElementReference(JsonNode attributesNode, String attributeName, String expectedReference) {
        JsonNode attributeConfiguration = attributesNode.path(attributeName);
        assertEquals(expectedReference, attributeConfiguration.path("elementRef").asText());
    }
}
