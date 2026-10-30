package org.finos.rune.importer.xsd.integration.features.polymorphism;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Disabled;
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
 * Verifies root-element polymorphism where a global root element has an abstract base type, a local concrete xsi:type, and a cross-namespace concrete xsi:type.
 *
 * <p>Status: PARTIAL</p>
 * <p>Limitation: generated root-wrapper serialization remains pending because the current model and XML configuration do not yet preserve the root element/type separation required by both root branches.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/xsd-1.1-gap-analysis.md.</p>
 */
@Tag("partial")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RootPolymorphismIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/root-polymorphism-ext.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/root-polymorphism.local.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/root-polymorphism.cross-namespace.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/root-polymorphism.invalid-missing-type.xml"));
    private static final String BASE_NS = "http://example.com/rune/root-polymorphism";
    private static final String EXT_NS = "http://example.com/rune/root-polymorphism/ext";
    private static final String BASE_PKG = "com.example.rune.rootpolymorphism";
    private static final String EXT_PKG = "com.example.rune.rootpolymorphism.ext";
    private static final String TARGET_RUNE_TYPE_NAME = "Root";

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
    @Tag("pending")
    @Disabled("Root polymorphism remains blocked by root element/type separation; reactivate when root wrappers preserve both root branches; see the capability matrix.")
    void shouldRoundTripRootPolymorphismStructurally() throws Exception {
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        Schema schema = getSourceSchema(XSD_FILE);
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
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, BASE_PKG));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(BASE_NS)
                .addNamespaceMapping(BASE_NS, BASE_PKG)
                .addTargetNamespace(EXT_NS)
                .addNamespaceMapping(EXT_NS, EXT_PKG);
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
        assertEquals(Set.of(
                "com_example_rune_rootpolymorphism-type.rune",
                "com_example_rune_rootpolymorphism_ext-type.rune"), generatedRuneFiles);

        Path baseRuneFile = generatedOutput.resolve("com_example_rune_rootpolymorphism-type.rune");
        Path extensionRuneFile = generatedOutput.resolve("com_example_rune_rootpolymorphism_ext-type.rune");
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(baseRuneFile));
        assertTrue(Files.exists(extensionRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String baseRune = Files.readString(baseRuneFile, StandardCharsets.UTF_8);
        String extensionRune = Files.readString(extensionRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(baseRune, "type Root:", "id string (1..1)");
        assertDeclarationContains(baseRune, "type Root:", "[rootType]");
        assertDeclarationContains(baseRune, "type LocalRoot extends Root:", "localValue string (1..1)");
        assertDeclarationContains(extensionRune, "type ExtendedRoot extends Root:", "externalValue int (1..1)");
        assertDeclarationContains(extensionRune, "type ExternalRoot extends ExtendedRoot:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertFalse(configuration.path(BASE_PKG + ".Root").isMissingNode());
        assertFalse(configuration.path(EXT_PKG + ".ExtendedRoot").isMissingNode());
        assertFalse(configuration.path(EXT_PKG + ".ExternalRoot").isMissingNode());
        assertEquals("root", configuration.path(BASE_PKG + ".Root").path("xmlElementName").asText());
        assertEquals("externalRoot", configuration.path(EXT_PKG + ".ExternalRoot").path("xmlElementName").asText());
    }
}
