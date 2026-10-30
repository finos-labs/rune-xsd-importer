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
 * Verifies typed global elements, inline global elements, root mappings, attributes, schema validation, generated-model compilation, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers representative single-namespace global roots and does not claim exhaustive global-element or root-polymorphism coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/test-coverage.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalElementIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.xsd");
    private static final Path GLOBAL_ROOT_VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.groot-branch.valid.xml");
    private static final Path SIMPLE_XS_TYPE_VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.simple-xs-type.valid.xml");
    private static final Path INLINE_ROOT_VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.iroot-branch.valid.xml");
    private static final List<Path> INVALID_XML_FILES = List.of(
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.invalid-global-root.xml"),
            Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/global-element.invalid-inline-root.xml"));
    private static final String TARGET_NS = "http://example.com/global";
    private static final String RUNE_PKG = "com.example.global";
    private static final String GENERATED_RUNE_FILE = "com_example_global-type.rune";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, GLOBAL_ROOT_VALID_XML_FILE));
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, SIMPLE_XS_TYPE_VALID_XML_FILE));
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, INLINE_ROOT_VALID_XML_FILE));
    }

    @Test
    void shouldRejectInvalidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertSamplesRejectedAgainstSchema(schema, INVALID_XML_FILES);
    }

    @Test
    void shouldGenerateRuneModelsAndConfiguration() throws Exception {
        assertNotNull(getScenarioState().generatedOutput());
    }

    @Test
    void shouldCompileGeneratedRuneModel() throws Exception {
        JavaTestModel compiledModel = getScenarioState().compiledModel();
        assertNotNull(compiledModel);
        assertNotNull(compiledModel.getTypeJavaClass("GlobalRoot"));
        assertNotNull(compiledModel.getTypeJavaClass("SimpleXSType"));
        assertNotNull(compiledModel.getTypeJavaClass("InlineRoot"));
    }

    @Test
    void shouldRoundTripValidSamples() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        ScenarioState state = getScenarioState();
        Path generatedOutput = state.generatedOutput();
        JavaTestModel compiledModel = state.compiledModel();
        Path generatedXmlDirectory = generatedOutput.resolve("xml");
        Files.createDirectories(generatedXmlDirectory);

        assertRoundTrip(schema, compiledModel, GLOBAL_ROOT_VALID_XML_FILE, "GlobalRoot", generatedXmlDirectory);
        assertRoundTrip(schema, compiledModel, SIMPLE_XS_TYPE_VALID_XML_FILE, "SimpleXSType", generatedXmlDirectory);
        assertRoundTrip(schema, compiledModel, INLINE_ROOT_VALID_XML_FILE, "InlineRoot", generatedXmlDirectory);
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
        assertDeclarationContains(generatedRune, "type RootType:", "id string (1..1)");
        assertDeclarationContains(generatedRune, "type SimpleXSType:", "term string (1..1)");
        assertDeclarationContains(generatedRune, "type SimpleXSType:", "[rootType]");
        assertDeclarationContains(generatedRune, "type InlineRoot:", "simpleXsType SimpleXSType (0..1)");
        assertDeclarationContains(generatedRune, "type GlobalRoot extends RootType:", "[rootType]");
        assertDeclarationContains(generatedRune, "type InlineRoot:", "[rootType]");
        assertDeclarationContains(generatedRune, "type InlineRoot:", "status string (1..1)");
        assertDeclarationContains(generatedRune, "type InlineRoot:", "_version int (0..1)");
        assertFalse(generatedRune.contains("type SimpleXsType"));
        assertEquals(1, countOccurrences(generatedRune, "type SimpleXSType:"));
        assertEquals(1, countOccurrences(simpleXsTypeDeclaration(generatedRune), "[rootType]"));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode globalRoot = configuration.path(RUNE_PKG + ".GlobalRoot");
        JsonNode inlineRoot = configuration.path(RUNE_PKG + ".InlineRoot");
        assertFalse(globalRoot.isMissingNode());
        JsonNode simpleXsType = configuration.path(RUNE_PKG + ".SimpleXSType");
        assertFalse(simpleXsType.isMissingNode());
        assertEquals("simpleXsType", simpleXsType.path("xmlElementName").asText());
        assertEquals(TARGET_NS + "/simpleXsType", simpleXsType.path("xmlElementFullyQualifiedName").asText());
        assertFalse(inlineRoot.isMissingNode());
        assertTrue(inlineRoot.path("attributes").path("_version").isObject());
    }

    private String simpleXsTypeDeclaration(String generatedRune) {
        int start = generatedRune.indexOf("type SimpleXSType:");
        assertTrue(start >= 0);
        int end = generatedRune.indexOf("\ntype ", start + 1);
        return generatedRune.substring(start, end >= 0 ? end : generatedRune.length());
    }

    private int countOccurrences(String value, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private void assertRoundTrip(Schema schema, JavaTestModel compiledModel, Path xmlFile, String typeName, Path generatedXmlDirectory) throws Exception {
        Class<? extends RosettaModelObject> targetClass = compiledModel.getTypeJavaClass(typeName);
        assertNotNull(targetClass);

        ObjectMapper xmlMapper = getXmlMapper(generatedXmlDirectory.getParent().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String originalXml = Files.readString(xmlFile, StandardCharsets.UTF_8);
        Object parsedInstance = xmlMapper.readValue(originalXml, targetClass);
        String generatedXml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedInstance);

        Path generatedXmlPath = generatedXmlDirectory.resolve(xmlFile.getFileName());
        Files.writeString(generatedXmlPath, generatedXml, StandardCharsets.UTF_8);

        XsdValidationUtils.validate(schema, generatedXmlPath);
        assertXmlEquals(originalXml, generatedXml);
    }
}
