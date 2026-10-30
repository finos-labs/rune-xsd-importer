package org.finos.rune.importer.xsd.regression;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.namespace.QName;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for QName prefix resolution in the namespace context of the schema that owns
 * a declaration.
 *
 * <p>This is intentionally a focused regression test rather than a full public integration scenario.
 * It covers an extension base with a reused lexical prefix and the affected generated artifacts
 * without adding the broader source-schema validation and XML round-trip contract used by the
 * documented integration suite.</p>
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrefixResolutionRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/regression/prefixresolution/entry.xsd");
    private static final String FIRST_NS = "urn:example:prefix:first";
    private static final String SECOND_NS = "urn:example:prefix:second";
    private static final String FIRST_PKG = "com.example.prefix.first";
    private static final String SECOND_PKG = "com.example.prefix.second";

    @Test
    void resolvesRepeatedPrefixUsingTheOwningSchemaNamespace() {
        XsdParserInspector inspector = inspector();

        assertEquals(RegistryKey.of(SECOND_NS, "Base"), inspector.getComplexTypeBaseKey(new QName(SECOND_NS, "Derived")));
    }

    @Test
    void generatedDerivedTypeUsesTheBaseFromItsOwnNamespace() throws Exception {
        Path generatedOutput = generateScenarioModels();
        assertGeneratedModelContract(generatedOutput);
        String secondRune = Files.readString(
                generatedOutput.resolve("com_example_prefix_second-type.rune"), StandardCharsets.UTF_8);

        assertDeclarationContains(secondRune, "type Base:", "secondValue string (1..1)");
        assertTrue(secondRune.contains("type Derived extends Base:"));
        assertFalse(secondRune.contains("import " + FIRST_PKG + ".*"),
                "The second namespace must not import the first namespace for tns:Base");
        assertTrue(Files.exists(generatedOutput.resolve("com_example_prefix_first-type.rune")));
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(FIRST_NS)
                .addNamespaceMapping(FIRST_NS, FIRST_PKG)
                .addTargetNamespace(SECOND_NS)
                .addNamespaceMapping(SECOND_NS, SECOND_PKG);
        return generateRuneModels(XSD_FILE, outputDirectory, config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("com_example_prefix_first-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("com_example_prefix_second-type.rune")));
    }

    private XsdParserInspector inspector() {
        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(XSD_FILE);
        return new XsdParserInspector(parser);
    }
}
