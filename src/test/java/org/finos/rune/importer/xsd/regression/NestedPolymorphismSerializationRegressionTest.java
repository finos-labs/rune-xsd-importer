package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.XmlSerializationContractAssertions;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies published nested xsi:type serialization without claiming root
 * polymorphism, whose root element and payload identity remain unsupported.
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestedPolymorphismSerializationRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/polymorphism");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("polymorphism-ext.xsd");
    private static final String BASE_NS = "http://example.com/rune/polymorphism";
    private static final String EXT_NS = "http://example.com/rune/poly-extension";
    private static final String BASE_PKG = "com.example.rune.polymorphism";
    private static final String EXT_PKG = "com.example.rune.poly.extension";

    @Test
    void publishesLocalAndCrossNamespaceXsiTypesFromRootBindings() throws Exception {
        assertPublishedType(
                RESOURCE_ROOT.resolve("nested-polymorphism.local.valid.xml"),
                BASE_NS,
                "LocalExtendedType");
        assertPublishedType(
                RESOURCE_ROOT.resolve("nested-polymorphism.cross-namespace.valid.xml"),
                EXT_NS,
                "ExtendedType");
    }

    private void assertPublishedType(Path input, String expectedTypeNamespace, String expectedTypeName) throws Exception {
        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> targetClass = state.compiledModel().getTypeJavaClass("Document");
        assertNotNull(targetClass);

        ObjectMapper mapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        Object parsed = mapper.readValue(Files.readString(input, StandardCharsets.UTF_8), targetClass);
        String generated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);

        Path output = state.generatedOutput().resolve("xml").resolve(input.getFileName());
        Files.createDirectories(output.getParent());
        Files.writeString(output, generated, StandardCharsets.UTF_8);
        Schema schema = getSourceSchema(XSD_FILE);
        XsdValidationUtils.validate(schema, output);

        XmlSerializationContractAssertions.assertRootScopedNamespaceDeclarations(generated);
        XmlSerializationContractAssertions.assertRootBoundXsiType(
                generated, "item", BASE_NS, expectedTypeNamespace, expectedTypeName);
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path output = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(BASE_NS)
                .addNamespaceMapping(BASE_NS, BASE_PKG)
                .addTargetNamespace(EXT_NS)
                .addNamespaceMapping(EXT_NS, EXT_PKG);
        return generateRuneModels(XSD_FILE, output, config);
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, BASE_PKG));
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }
}
