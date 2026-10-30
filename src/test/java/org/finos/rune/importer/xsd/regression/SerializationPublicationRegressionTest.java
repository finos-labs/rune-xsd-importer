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
 * Covers the lexical publication contract for cross-namespace serialization
 * using the TypeImport integration schema.
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SerializationPublicationRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Paths.get(
            "src/test/resources/org/finos/rune/xsd/integration/features/inheritance");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("importer.xsd");
    private static final Path VALID_XML_FILE = RESOURCE_ROOT.resolve("importer.valid.xml");
    private static final String BASE_NS = "http://example.com/base";
    private static final String BASE_PKG = "com.example.base";
    private static final String IMPORTER_NS = "http://example.com/importer";
    private static final String IMPORTER_PKG = "com.example.importer";

    @Test
    void publishesImportedElementNamespacesFromTheDocumentRoot() throws Exception {
        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> targetClass = state.compiledModel().getTypeJavaClass("Employee");
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
        XmlSerializationContractAssertions.assertRootBoundQualifiedElement(generated, "name", BASE_NS);
        XmlSerializationContractAssertions.assertRootBoundQualifiedElement(generated, "id", BASE_NS);
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path output = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(BASE_NS)
                .addNamespaceMapping(BASE_NS, BASE_PKG)
                .addTargetNamespace(IMPORTER_NS)
                .addNamespaceMapping(IMPORTER_NS, IMPORTER_PKG);
        return generateRuneModels(XSD_FILE, output, config);
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, IMPORTER_PKG));
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }
}
