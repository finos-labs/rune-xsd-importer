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
 * Verifies primitive fields, optional values, enumerations, generated model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario verifies one representative enumeration and does not claim exhaustive simple-type coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleModelIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/simple-test.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/simple-test.valid.xml");
    private static final Path INVALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/simple-test.invalid-side.xml");
    private static final String TARGET_NS = "http://example.com/simple";
    private static final String RUNE_PKG = "com.example.simple";
    private static final String TARGET_RUNE_TYPE_NAME = "TestObject";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() {
        assertDoesNotThrow(() -> XsdValidationUtils.validate(getSourceSchema(XSD_FILE), VALID_XML_FILE));
    }

    @Test
    void shouldRejectInvalidSamplesAgainstSchema() throws Exception {
        assertSamplesRejectedAgainstSchema(getSourceSchema(XSD_FILE), List.of(INVALID_XML_FILE));
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

        Path generatedXmlPath = generatedOutput.resolve("xml/simple-test.valid.xml");
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
        assertEquals(Set.of("com_example_simple-type.rune", "com_example_simple-enum.rune"), generatedRuneFiles);

        Path typeFile = generatedOutput.resolve("com_example_simple-type.rune");
        Path enumFile = generatedOutput.resolve("com_example_simple-enum.rune");
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(typeFile));
        assertTrue(Files.exists(enumFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedType = Files.readString(typeFile, StandardCharsets.UTF_8);
        String generatedEnum = Files.readString(enumFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedType, "type TestObjectType:", "name string (1..1)");
        assertDeclarationContains(generatedType, "type TestObjectType:", "_count int (0..1)");
        assertDeclarationContains(generatedType, "type TestObjectType:", "side SideEnum (1..1)");
        assertDeclarationContains(generatedType, "type TestObject extends TestObjectType:", "[rootType]");
        assertTrue(generatedEnum.contains("enum SideEnum:"));
        assertTrue(generatedEnum.contains("BUY displayName \"Buy\""));
        assertTrue(generatedEnum.contains("SELL displayName \"Sell\""));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode testObject = configuration.path(RUNE_PKG + ".TestObject");
        assertFalse(testObject.isMissingNode());
        assertTrue(testObject.path("attributes").isEmpty());
    }
}
