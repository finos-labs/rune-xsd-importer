package org.finos.rune.importer.xsd.integration.features.inheritance;

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
 * Verifies a cross-namespace XSD import and single-level complex-type extension through the public importer setup, including inherited qualified elements, a local derived element, source-schema validation, generated Rune imports, generated-model compilation, and structural XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one single-level cross-namespace extension and does not claim exhaustive extension-chain, import-graph, inherited-member, or namespace-prefix coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md, documentation/docs/test-coverage.md, and documentation/docs/mapping-reference.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeImportIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/inheritance");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("importer.xsd");
    private static final Path VALID_XML_FILE = RESOURCE_ROOT.resolve("importer.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(RESOURCE_ROOT.resolve("importer.invalid-missing-inherited-id.xml"));
    private static final String BASE_NS = "http://example.com/base";
    private static final String BASE_PKG = "com.example.base";
    private static final String IMPORTER_NS = "http://example.com/importer";
    private static final String IMPORTER_PKG = "com.example.importer";
    private static final String TARGET_RUNE_TYPE_NAME = "Employee";
    private static final String BASE_RUNE_FILE = "com_example_base-type.rune";
    private static final String IMPORTER_RUNE_FILE = "com_example_importer-type.rune";

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
    void shouldGenerateRuneModelsAndConfigurationThroughPublicSetup() throws Exception {
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
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);
        Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(VALID_XML_FILE.getFileName());
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

        assertXmlEquals(originalXml, generatedXml);
    }

    @Test
    void shouldValidateGeneratedXmlAgainstSourceSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(VALID_XML_FILE.getFileName());
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);
        XsdValidationUtils.validate(schema, generatedXmlPath);
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, IMPORTER_PKG));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(BASE_NS)
                .addTargetNamespace(IMPORTER_NS)
                .addNamespaceMapping(BASE_NS, BASE_PKG)
                .addNamespaceMapping(IMPORTER_NS, IMPORTER_PKG);
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
        assertEquals(Set.of(BASE_RUNE_FILE, IMPORTER_RUNE_FILE), generatedRuneFiles);

        Path baseRuneFile = generatedOutput.resolve(BASE_RUNE_FILE);
        Path importerRuneFile = generatedOutput.resolve(IMPORTER_RUNE_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(baseRuneFile));
        assertTrue(Files.exists(importerRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String baseRune = Files.readString(baseRuneFile, StandardCharsets.UTF_8);
        String importerRune = Files.readString(importerRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(baseRune, "type Person:", "name string (1..1)");
        assertDeclarationContains(baseRune, "type Person:", "id string (1..1)");
        assertTrue(importerRune.contains("import " + BASE_PKG + ".*"));
        assertDeclarationContains(importerRune, "type EmployeeType extends Person:", "department string (1..1)");
        assertDeclarationContains(importerRune, "type Employee extends EmployeeType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertFalse(configuration.path(BASE_PKG + ".Person").isMissingNode());
        JsonNode employeeType = configuration.path(IMPORTER_PKG + ".EmployeeType");
        assertFalse(employeeType.isMissingNode());
        assertEquals("department", employeeType.path("attributes").path("department").path("xmlName").asText());
        JsonNode employee = configuration.path(IMPORTER_PKG + ".Employee");
        assertFalse(employee.isMissingNode());
        assertEquals("base:name", employee.path("attributes").path("name").path("xmlName").asText());
        assertEquals("http://example.com/base/name", employee.path("attributes").path("name").path("elementRef").asText());
        assertEquals("base:id", employee.path("attributes").path("id").path("xmlName").asText());
        assertEquals("http://example.com/base/id", employee.path("attributes").path("id").path("elementRef").asText());
    }
}
