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
import java.util.Set;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies XSD simple-type facets, native wrappers, groups, references, inheritance, choices, root mappings, and XML round-trip behavior.
 *
 * <p>Status: VERIFIED</p>
 * <p>Limitation: this scenario covers one representative composite product and does not claim exhaustive inheritance, choice, namespace, or serializer coverage.</p>
 * <p>See: documentation/docs/capability-matrix.md and documentation/docs/advanced-serialization.md</p>
 */
@Tag("verified")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeProductIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/composite-product.xsd");
    private static final Path VALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/composite-product.valid.xml");
    private static final Path INVALID_XML_FILE = Paths.get("src/test/resources/org/finos/rune/xsd/integration/features/misc/composite-product.invalid-sku.xml");
    private static final String TARGET_NS = "http://example.com/rune/complex";
    private static final String RUNE_PKG = "com.example.rune.complex";
    private static final String GENERATED_RUNE_FILE = "com_example_rune_complex-type.rune";
    private static final String TARGET_RUNE_TYPE_NAME = "Catalogue";

    @Test
    void shouldCompileSourceSchema() {
        assertDoesNotThrow(() -> getSourceSchema(XSD_FILE));
    }

    @Test
    void shouldAcceptValidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertDoesNotThrow(() -> XsdValidationUtils.validate(schema, VALID_XML_FILE));
    }

    @Test
    void shouldRejectInvalidSamplesAgainstSchema() throws Exception {
        Schema schema = getSourceSchema(XSD_FILE);
        assertSamplesRejectedAgainstSchema(schema, java.util.List.of(INVALID_XML_FILE));
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

        Path generatedXmlPath = generatedOutput.resolve("xml/composite-product.valid.xml");
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
        assertEquals(Set.of(GENERATED_RUNE_FILE), generatedRuneFiles);

        Path generatedRuneFile = generatedOutput.resolve(GENERATED_RUNE_FILE);
        Path xmlConfigFile = generatedOutput.resolve("config/xml-config.json");
        assertTrue(Files.exists(generatedRuneFile));
        assertTrue(Files.exists(xmlConfigFile));

        String generatedRune = Files.readString(generatedRuneFile, StandardCharsets.UTF_8);
        assertDeclarationContains(generatedRune, "typeAlias SkuCode:", "string(pattern: \"SKU-\\\\d{5}\")");
        assertDeclarationContains(generatedRune, "type Description:", "value string (1..1)");
        assertFalse(generatedRune.contains("typeAlias Description:"));
        assertDeclarationContains(generatedRune, "type DimensionsGroup:", "width number (1..1)");
        assertDeclarationContains(generatedRune, "type BaseProduct:", "skuCode SkuCode (1..1)");
        assertDeclarationContains(generatedRune, "type BaseProduct:", "description Description (0..1)");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "dimensionsGroup DimensionsGroup (1..1)");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "emptyValue EmptyValue (1..1)");
        assertDeclarationContains(generatedRune, "typeAlias EmptyValue:", "string(maxLength: 0)");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "digitalDownloadUrl string (0..1)");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "shippingWeight number (0..1)");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "condition Choice1:");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "required choice");
        assertDeclarationContains(generatedRune, "type Product extends BaseProduct:", "[rootType]");
        assertFalse(generatedRune.contains("type ProductElement"));
        assertDeclarationContains(generatedRune, "type LegacyProduct extends Product:", "[rootType]");
        assertDeclarationContains(generatedRune, "type Catalogue:", "product Product (1..*)");
        assertDeclarationContains(generatedRune, "type Catalogue:", "legacyProduct Product (0..1)");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode configuration = mapper.readTree(xmlConfigFile.toFile());
        JsonNode legacyProduct = configuration.path(RUNE_PKG + ".LegacyProduct");
        JsonNode catalogue = configuration.path(RUNE_PKG + ".Catalogue");
        assertFalse(legacyProduct.isMissingNode());
        assertTrue(legacyProduct.path("attributes").isEmpty());
        JsonNode catalogueAttributes = catalogue.path("attributes");
        assertTrue(catalogueAttributes.path("legacyProduct").path("elementRef").asText().endsWith("/legacyProduct"));
        assertTrue(configuration.path(RUNE_PKG + ".EmptyValue").path("enumValues").isMissingNode());
    }

}
