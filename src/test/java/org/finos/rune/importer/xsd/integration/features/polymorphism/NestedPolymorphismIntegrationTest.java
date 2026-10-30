package org.finos.rune.importer.xsd.integration.features.polymorphism;

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
 * Verifies nested local and cross-namespace XSD xsi:type polymorphism, inherited model types, XML configuration namespace metadata, generated-model compilation, source-schema validation, and structural XML round-trip behavior.
 *
 * <p>Status: PARTIAL</p>
 * <p>Limitation: exact xsi:type emission is not yet established, so generated XML is compared by expanded element names and values but xsi:type metadata is ignored. Root polymorphism is covered by a separate pending scenario.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/xsd-1.1-gap-analysis.md.</p>
 */
@Tag("partial")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestedPolymorphismIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/polymorphism-ext.xsd");
    private static final List<Path> VALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/nested-polymorphism.local.valid.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/nested-polymorphism.cross-namespace.valid.xml"));
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/polymorphism/nested-polymorphism.invalid-unknown-type.xml"));
    private static final String BASE_NS = "http://example.com/rune/polymorphism";
    private static final String EXT_NS = "http://example.com/rune/poly-extension";
    private static final String BASE_PKG = "com.example.rune.polymorphism";
    private static final String EXT_PKG = "com.example.rune.poly.extension";
    private static final String TARGET_RUNE_TYPE_NAME = "Document";

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
    void shouldRoundTripValidSamplesStructurally() throws Exception {
        ScenarioState state = getScenarioState();
        JavaTestModel compiledModel = state.compiledModel();
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(TARGET_RUNE_TYPE_NAME);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        for (Path validXmlFile : VALID_XML_FILES) {
            String originalXml = Files.readString(validXmlFile, StandardCharsets.UTF_8);
            Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);

            if (validXmlFile.getFileName().toString().contains("cross-namespace")) {
                Object item = parsedInstance.getClass().getMethod("getItem").invoke(parsedInstance);
                assertEquals(99, item.getClass().getMethod("getExtValue").invoke(item));
            }

            String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

            Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(validXmlFile.getFileName());
            Files.createDirectories(generatedXmlPath.getParent());
            Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

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
                "com_example_rune_polymorphism-type.rune",
                "com_example_rune_poly_extension-type.rune"), generatedRuneFiles);

        Path baseRuneFile = generatedOutput.resolve("com_example_rune_polymorphism-type.rune");
        Path extensionRuneFile = generatedOutput.resolve("com_example_rune_poly_extension-type.rune");
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(baseRuneFile));
        assertTrue(Files.exists(extensionRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String baseRune = Files.readString(baseRuneFile, StandardCharsets.UTF_8);
        String extensionRune = Files.readString(extensionRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(baseRune, "type BaseType:", "id string (1..1)");
        assertDeclarationContains(baseRune, "type LocalExtendedType extends BaseType:", "localValue string (1..1)");
        assertDeclarationContains(baseRune, "type DocumentType:", "^item BaseType (1..1)");
        assertDeclarationContains(baseRune, "type Document extends DocumentType:", "[rootType]");
        assertDeclarationContains(extensionRune, "type ExtendedType extends BaseType:", "extValue int (1..1)");
        assertDeclarationContains(extensionRune, "type OtherElement extends ExtendedType:", "[rootType]");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        assertFalse(configuration.path(BASE_PKG + ".BaseType").isMissingNode());
        assertFalse(configuration.path(EXT_PKG + ".ExtendedType").isMissingNode());

        JsonNode extendedAttributes = configuration.path(EXT_PKG + ".ExtendedType").path("attributes");
        assertEquals("extValue", extendedAttributes.path("extValue").path("xmlName").asText());

        JsonNode documentAttributes = configuration.path(BASE_PKG + ".Document").path("xmlAttributes");
        assertEquals("http://www.w3.org/2001/XMLSchema-instance", documentAttributes.path("xmlns:xsi").asText());

        JsonNode otherElementAttributes = configuration.path(EXT_PKG + ".OtherElement").path("xmlAttributes");
        assertEquals("http://www.w3.org/2001/XMLSchema-instance", otherElementAttributes.path("xmlns:xsi").asText());
        assertEquals(BASE_NS, otherElementAttributes.path("xmlns:base").asText());
    }
}
