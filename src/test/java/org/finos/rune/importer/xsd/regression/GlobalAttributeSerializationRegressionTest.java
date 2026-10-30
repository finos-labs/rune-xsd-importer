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

/** Publishes qualified global attributes using bindings declared by the document root. */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalAttributeSerializationRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/attributes");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("global-attribute-reference.xsd");
    private static final Path VALID_XML_FILE = RESOURCE_ROOT.resolve("global-attribute-reference.valid.xml");
    private static final String TARGET_NS = "http://example.com/rune/attrref";
    private static final String RUNE_PKG = "example.com.rune.attrref";

    @Test
    void publishesQualifiedGlobalAttributesFromDocumentRootBindings() throws Exception {
        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> targetClass = state.compiledModel()
                .getTypeJavaClass("GlobalAttributeReference");
        assertNotNull(targetClass);

        ObjectMapper mapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        Object parsed = mapper.readValue(Files.readString(VALID_XML_FILE, StandardCharsets.UTF_8), targetClass);
        String generated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);

        Path output = state.generatedOutput().resolve("xml/publication.xml");
        Files.createDirectories(output.getParent());
        Files.writeString(output, generated, StandardCharsets.UTF_8);
        Schema schema = getSourceSchema(XSD_FILE);
        XsdValidationUtils.validate(schema, output);

        XmlSerializationContractAssertions.assertRootScopedNamespaceDeclarations(generated);
        XmlSerializationContractAssertions.assertRootBoundQualifiedAttribute(
                generated, "globalAttributeReference", TARGET_NS, "globalId", TARGET_NS);
        XmlSerializationContractAssertions.assertRootBoundQualifiedAttribute(
                generated, "globalAttributeReference", TARGET_NS, "globalCode", TARGET_NS);
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path output = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(TARGET_NS)
                .addNamespaceMapping(TARGET_NS, RUNE_PKG);
        return generateRuneModels(XSD_FILE, output, config);
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, RUNE_PKG));
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }
}
