package org.finos.rune.importer.xsd.integration.features.refs;

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
 * Verifies a primitive global XSD element referenced from local complex content, including native-wrapper preservation, element XML configuration, source-schema validation, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one same-namespace primitive reference and does not claim exhaustive reference, namespace, cardinality, or substitution coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/mapping-reference.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ElementRefIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/refs/element-ref.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/refs/element-ref.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/refs/element-ref.invalid-missing-reference.xml"));
    private static final String TARGET_NS = "http://example.com/rune/test/refs/element";
    private static final String XML_DSIG_NS = "http://example.com/rune/test/refs/element/dsig";
    private static final String RUNE_PKG = "com.example.rune.test.refs.element";
    private static final String XML_DSIG_PKG = "com.example.rune.test.refs.element.dsig";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_test_refs_element-type.rune";
    private static final String GENERATED_XML_DSIG_RUNE_FILE = "com_example_rune_test_refs_element_dsig-type.rune";
    private static final String TARGET_RUNE_TYPE_NAME = "TestElementRef";

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
        String originalXml = Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = state.generatedOutput().resolve("xml").resolve(VALID_XML_FILE.getFileName());
        Files.createDirectories(generatedXmlPath.getParent());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

        XsdValidationUtils.validate(schema, generatedXmlPath);
        assertXmlEquals(originalXml, generatedXml);
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
                .addNamespaceMapping(TARGET_NS, RUNE_PKG)
                .addTargetNamespace(XML_DSIG_NS)
                .addNamespaceMapping(XML_DSIG_NS, XML_DSIG_PKG);
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
        assertEquals(Set.of(GENERATED_RUNE_FILE, GENERATED_XML_DSIG_RUNE_FILE), generatedRuneFiles);

        Path generatedRuneFile = generatedOutput.resolve(GENERATED_RUNE_FILE);
        Path generatedXmlDsigRuneFile = generatedOutput.resolve(GENERATED_XML_DSIG_RUNE_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedRuneFile));
        assertTrue(Files.exists(generatedXmlDsigRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedRune = Files.readString(generatedRuneFile, StandardCharsets.UTF_8);
        String generatedXmlDsigRune = Files.readString(generatedXmlDsigRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedRune, "type GlobalRefEl:", "value string (1..1)");
        assertDeclarationContains(generatedRune, "type GlobalRefEl:", "[rootType]");
        assertDeclarationContains(generatedRune, "type ElementRefType:", "beforeRef int (1..1)");
        assertDeclarationContains(generatedRune, "type ElementRefType:", "globalRefEl GlobalRefEl (1..1)");
        assertDeclarationContains(generatedRune, "type ElementRefType:", "afterRef boolean (0..1)");
        assertDeclarationContains(generatedRune, "type TestElementRef extends ElementRefType:", "[rootType]");
        assertDeclarationContains(generatedXmlDsigRune, "type SignatureType:", "signatureValue string (1..1)");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode elementRefType = configuration.path(RUNE_PKG + ".ElementRefType");
        assertFalse(elementRefType.isMissingNode());
        assertEquals("ELEMENT", elementRefType.path("attributes").path("globalRefEl").path("xmlRepresentation").asText());
        assertFalse(configuration.path(RUNE_PKG + ".GlobalRefEl").isMissingNode());
        assertFalse(configuration.path(RUNE_PKG + ".TestElementRef").isMissingNode());
        JsonNode signatureType = configuration.path(XML_DSIG_PKG + ".SignatureType");
        assertFalse(signatureType.isMissingNode());
        boolean hasSignatureElement = false;
        var attributes = configuration.path(RUNE_PKG + ".ElementRefType").path("attributes").fields();
        while (attributes.hasNext()) {
            var attribute = attributes.next();
            JsonNode value = attribute.getValue();
            if (("signature".equals(attribute.getKey()) || "Signature".equals(value.path("xmlName").asText()))
                    && "ELEMENT".equals(value.path("xmlRepresentation").asText())) {
                hasSignatureElement = true;
                break;
            }
        }
        assertTrue(hasSignatureElement, "Expected the imported Signature reference to use ELEMENT XML representation");
    }
}
