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
 * Verifies imported simple types and model groups used inside a local choice, imported namespace generation, orphan global element discovery, source-schema validation, generated-model compilation, and representative XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one imported group and enum with two choice branches plus one standalone global element, and does not claim exhaustive import-graph, choice, or root-metadata coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md, documentation/docs/test-coverage.md, and documentation/docs/mapping-reference.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiImportIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/inheritance");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("multi-importer.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            RESOURCE_ROOT.resolve("multi-importer.choice1-branch.valid.xml"),
            RESOURCE_ROOT.resolve("multi-importer.choice2-branch.valid.xml"),
            RESOURCE_ROOT.resolve("multi-importer.orphan-branch.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            RESOURCE_ROOT.resolve("multi-importer.invalid-missing-choice.xml"));
    private static final String COMMON_NS = "http://example.com/common";
    private static final String COMMON_PKG = "com.example.common";
    private static final String MULTI_NS = "http://example.com/multi";
    private static final String MULTI_PKG = "com.example.multi";
    private static final String COMMON_TYPE_FILE = "com_example_common-type.rune";
    private static final String COMMON_ENUM_FILE = "com_example_common-enum.rune";
    private static final String MULTI_TYPE_FILE = "com_example_multi-type.rune";
    private static final String MULTI_ENUM_FILE = "com_example_multi-enum.rune";

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
        assertNotNull(compiledModel.getTypeJavaClass("UserRecord"));
        assertNotNull(compiledModel.getTypeJavaClass("OrphanElement"));
    }

    @Test
    void shouldRoundTripValidSamples() throws Exception {
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();

        for (Path validXmlFile : VALID_XML_FILES) {
            String targetTypeName = validXmlFile.getFileName().toString().contains("orphan") ? "OrphanElement" : "UserRecord";
            Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(targetTypeName);
            assertNotNull(targetClass);

            ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
            String originalXml = Files.readString(validXmlFile, StandardCharsets.UTF_8);
            Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
            String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);
            Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(validXmlFile.getFileName());
            Files.createDirectories(generatedXmlPath.getParent());
            Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

            assertXmlEquals(originalXml, generatedXml);
        }
    }

    @Test
    void shouldValidateGeneratedXmlAgainstSourceSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();

        for (Path validXmlFile : VALID_XML_FILES) {
            String targetTypeName = validXmlFile.getFileName().toString().contains("orphan") ? "OrphanElement" : "UserRecord";
            Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(targetTypeName);
            ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
            String originalXml = Files.readString(validXmlFile, StandardCharsets.UTF_8);
            Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
            String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

            Path generatedXmlPath = state.generatedOutput().resolve("xml-strict").resolve(validXmlFile.getFileName());
            Files.createDirectories(generatedXmlPath.getParent());
            Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);
            XsdValidationUtils.validate(schema, generatedXmlPath);
        }
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, MULTI_PKG));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(COMMON_NS)
                .addTargetNamespace(MULTI_NS)
                .addNamespaceMapping(COMMON_NS, COMMON_PKG)
                .addNamespaceMapping(MULTI_NS, MULTI_PKG);
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
        assertEquals(Set.of(COMMON_TYPE_FILE, COMMON_ENUM_FILE, MULTI_TYPE_FILE, MULTI_ENUM_FILE), generatedRuneFiles);

        Path commonTypeFile = generatedOutput.resolve(COMMON_TYPE_FILE);
        Path commonEnumFile = generatedOutput.resolve(COMMON_ENUM_FILE);
        Path multiTypeFile = generatedOutput.resolve(MULTI_TYPE_FILE);
        Path multiEnumFile = generatedOutput.resolve(MULTI_ENUM_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(commonTypeFile));
        assertTrue(Files.exists(commonEnumFile));
        assertTrue(Files.exists(multiTypeFile));
        assertTrue(Files.exists(multiEnumFile));
        assertTrue(Files.exists(xmlConfigFile));

        String commonType = Files.readString(commonTypeFile, StandardCharsets.UTF_8);
        String commonEnum = Files.readString(commonEnumFile, StandardCharsets.UTF_8);
        String multiType = Files.readString(multiTypeFile, StandardCharsets.UTF_8);
        String multiEnum = Files.readString(multiEnumFile, StandardCharsets.UTF_8);
        assertDeclarationContains(commonEnum, "enum StatusEnum:", "ACTIVE");
        assertDeclarationContains(commonEnum, "enum StatusEnum:", "INACTIVE");
        assertDeclarationContains(commonType, "type AuditGroup:", "createdAt date (1..1)");
        assertDeclarationContains(commonType, "type AuditGroup:", "createdBy string (1..1)");
        assertDeclarationContains(commonType, "type OrphanBaseType:", "reason string (1..1)");
        assertTrue(multiType.contains("import " + COMMON_PKG + ".*"));
        assertDeclarationContains(multiEnum, "enum RoleEnum:", "ADMIN");
        assertDeclarationContains(multiEnum, "enum RoleEnum:", "USER");
        assertDeclarationContains(multiType, "type UserRecord:", "id string (1..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "role RoleEnum (1..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "status StatusEnum (1..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "lastLogin zonedDateTime (0..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "auditGroup AuditGroup (0..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "auditCode string (1..1)");
        assertDeclarationContains(multiType, "type UserRecord:", "required choice lastLogin, auditGroup");
        assertDeclarationContains(multiType, "type UserRecord:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertFalse(configuration.path(COMMON_PKG + ".StatusEnum").isMissingNode());
        assertFalse(configuration.path(COMMON_PKG + ".AuditGroup").isMissingNode());
        JsonNode userRecord = configuration.path(MULTI_PKG + ".UserRecord");
        assertFalse(userRecord.isMissingNode());
        assertEquals("UserRecord", userRecord.path("xmlElementName").asText());
        JsonNode auditCode = userRecord.path("attributes").path("auditCode");
        assertEquals("ATTRIBUTE", auditCode.path("xmlRepresentation").asText());
        assertEquals(COMMON_NS + "/auditCode", auditCode.path("elementRef").asText());
        assertEquals(COMMON_NS, userRecord.path("xmlAttributes").path("xmlns:common").asText());
        assertFalse(configuration.path(MULTI_PKG + ".OrphanElement").isMissingNode());
    }
}
