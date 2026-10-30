package org.finos.rune.importer.xsd.integration.features.extension;

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
 * Verifies complex-content extension from a user-defined base type, inherited and derived elements and attributes, optional group composition, XML configuration, generated-model compilation, source-schema validation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one single-level complex-content extension in one namespace and does not claim exhaustive extension-chain, cross-namespace, restriction, mixed-content, or XSD 1.1 coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplexContentExtensionIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.with-group.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.invalid-missing-base-attribute.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.invalid-missing-derived-attribute.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/extension/complex-content-extension.invalid-decimal.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/extension/complex";
    private static final String RUNE_PKG = "com.example.rune.test.extension.complex";
    private static final String TARGET_RUNE_TYPE_NAME = "TestComplexExt";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_test_extension_complex-type.rune";

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
        assertDeclarationContains(generatedRune, "type BaseType:", "baseString string (1..1)");
        assertDeclarationContains(generatedRune, "type BaseType:", "baseRequiredAttr int (1..1)");
        assertDeclarationContains(generatedRune, "type BaseType:", "baseOptionalAttr boolean (0..1)");
        assertDeclarationContains(generatedRune, "type DerivedType extends BaseType:", "derivedDecimal number (1..1)");
        assertDeclarationContains(generatedRune, "type DerivedType extends BaseType:", "derivedOptionalDate date (0..1)");
        assertDeclarationContains(generatedRune, "type DerivedType extends BaseType:", "extraFields ExtraFields (0..1)");
        assertDeclarationContains(generatedRune, "type DerivedType extends BaseType:", "derivedAttr string (1..1)");
        assertDeclarationContains(generatedRune, "type ExtraFields:", "extraNote string (1..1)");
        assertDeclarationContains(generatedRune, "type TestComplexExt extends DerivedType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode baseAttributes = configuration.path(RUNE_PKG + ".BaseType").path("attributes");
        assertFalse(baseAttributes.isMissingNode());
        assertAttributeConfig(baseAttributes, "baseString", "ELEMENT");
        assertAttributeConfig(baseAttributes, "baseRequiredAttr", "ATTRIBUTE");
        assertAttributeConfig(baseAttributes, "baseOptionalAttr", "ATTRIBUTE");

        JsonNode derivedAttributes = configuration.path(RUNE_PKG + ".DerivedType").path("attributes");
        assertFalse(derivedAttributes.isMissingNode());
        assertAttributeConfig(derivedAttributes, "derivedDecimal", "ELEMENT");
        assertAttributeConfig(derivedAttributes, "derivedOptionalDate", "ELEMENT");
        assertAttributeConfig(derivedAttributes, "extraFields", "VIRTUAL");
        assertAttributeConfig(derivedAttributes, "derivedAttr", "ATTRIBUTE");

        JsonNode groupAttributes = configuration.path(RUNE_PKG + ".ExtraFields").path("attributes");
        assertFalse(groupAttributes.isMissingNode());
        assertAttributeConfig(groupAttributes, "extraNote", "ELEMENT");
    }

    private void assertAttributeConfig(JsonNode attributesNode, String attributeName, String expectedRepresentation) {
        assertTrue(attributesNode.has(attributeName), "XML Config missing entry for: " + attributeName);
        assertEquals(expectedRepresentation, attributesNode.get(attributeName).get("xmlRepresentation").asText(), "Incorrect xmlRepresentation for " + attributeName);
    }
}
