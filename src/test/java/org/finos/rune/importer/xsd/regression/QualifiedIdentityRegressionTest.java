package org.finos.rune.importer.xsd.regression;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
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
import java.util.List;
import javax.xml.namespace.QName;

import static org.finos.rune.importer.xsd.RuneTestModelUtils.assertDeclarationContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for qualified declaration identity when two imported namespaces define the
 * same local type name.
 *
 * <p>This is intentionally a focused regression test rather than a full public integration scenario.
 * It covers homonymous complex types and their generated members without adding the broader
 * source-schema validation and XML round-trip contract used by the documented integration suite.</p>
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QualifiedIdentityRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path XSD_FILE = Paths.get(
            "src/test/resources/org/finos/rune/xsd/regression/qualifiedidentity/entry.xsd");
    private static final String CUSTOMER_NS = "urn:example:qualified:customer";
    private static final String PORTFOLIO_NS = "urn:example:qualified:portfolio";
    private static final String CUSTOMER_PKG = "com.example.qualified.customer";
    private static final String PORTFOLIO_PKG = "com.example.qualified.portfolio";

    @Test
    void resolvesHomonymousTypesByNamespaceAndLocalName() {
        XsdParserInspector inspector = inspector();

        assertEquals(List.of("tradeId"), memberNames(
                inspector.getComplexTypeStructuredMembers(new QName(CUSTOMER_NS, "Trade"))));
        assertEquals(List.of("portfolioId"), memberNames(
                inspector.getComplexTypeStructuredMembers(new QName(PORTFOLIO_NS, "Trade"))));
    }

    @Test
    void generatesEachHomonymousTypeWithItsOwnMembers() throws Exception {
        Path generatedOutput = generateScenarioModels();
        assertGeneratedModelContract(generatedOutput);
        String customerRune = Files.readString(
                generatedOutput.resolve("com_example_qualified_customer-type.rune"), StandardCharsets.UTF_8);
        String portfolioRune = Files.readString(
                generatedOutput.resolve("com_example_qualified_portfolio-type.rune"), StandardCharsets.UTF_8);

        assertDeclarationContains(customerRune, "type Trade:", "tradeId string (1..1)");
        assertDeclarationContains(portfolioRune, "type Trade:", "portfolioId string (1..1)");
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path outputDirectory = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(CUSTOMER_NS)
                .addNamespaceMapping(CUSTOMER_NS, CUSTOMER_PKG)
                .addTargetNamespace(PORTFOLIO_NS)
                .addNamespaceMapping(PORTFOLIO_NS, PORTFOLIO_PKG);
        return generateRuneModels(XSD_FILE, outputDirectory, config);
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(Files.exists(generatedOutput.resolve("com_example_qualified_customer-type.rune")));
        assertTrue(Files.exists(generatedOutput.resolve("com_example_qualified_portfolio-type.rune")));
    }

    private XsdParserInspector inspector() {
        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(XSD_FILE);
        return new XsdParserInspector(parser);
    }

    private List<String> memberNames(List<org.finos.rune.importer.xsd.inspector.model.InspectorNode> members) {
        return members.stream()
                .filter(InspectorAttributeNode.class::isInstance)
                .map(InspectorAttributeNode.class::cast)
                .map(InspectorAttributeNode::name)
                .toList();
    }
}
