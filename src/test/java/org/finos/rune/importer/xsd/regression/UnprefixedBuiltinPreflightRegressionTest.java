package org.finos.rune.importer.xsd.regression;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterDefaultSetup;
import org.finos.rune.importer.xsd.core.XsdDependencyValidator;
import org.finos.rune.importer.xsd.inspector.XsdDeclarationRole;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("regression")
class UnprefixedBuiltinPreflightRegressionTest {
    private static final Path FIXTURES = Path.of(
            "src/test/resources/org/finos/rune/xsd/regression/unprefixedbuiltin");
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    @Test
    void qualifiesBuiltinsAcrossRestrictionsMembersAttributesAndExtensions() {
        XsdParserInspector inspector = inspect("unprefixed-builtin.xsd");
        String namespace = "urn:example:unprefixed-builtin";

        assertDependency(inspector, namespace, "EncodedValue", XSD_NS, "base64Binary");
        assertDependency(inspector, namespace, "TextValue", XSD_NS, "string");
        assertDependency(inspector, namespace, "IntegerValue", XSD_NS, "integer");
        assertDependency(inspector, namespace, "DecimalValue", XSD_NS, "decimal");
        assertDependency(inspector, namespace, "BooleanValue", XSD_NS, "boolean");
        assertDependency(inspector, namespace, "DateValue", XSD_NS, "date");
        assertDependency(inspector, namespace, "Container", XSD_NS, "base64Binary");
        assertDependency(inspector, namespace, "Container", XSD_NS, "integer");
        assertDependency(inspector, namespace, "Container", XSD_NS, "ID");
        assertDependency(inspector, namespace, "EncodedContent", XSD_NS, "base64Binary");
        assertDependency(inspector, namespace, "EncodedContent", XSD_NS, "ID");
    }

    @Test
    void preflightAcceptsBuiltinsInXmlSchemaDefaultNamespace() {
        XsdParserInspector inspector = inspect("unprefixed-builtin.xsd");
        assertDoesNotThrow(() -> XsdDependencyValidator.validate(inspector,
                config("urn:example:unprefixed-builtin")));
    }

    @Test
    void preflightsEachBuiltinIndependently(@TempDir Path directory) throws IOException {
        for (String builtin : List.of("base64Binary", "string", "integer", "decimal", "boolean", "date", "ID")) {
            Path schema = directory.resolve(builtin + ".xsd");
            Files.writeString(schema, """
                    <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:single">
                      <simpleType name="Value"><restriction base="%s"/></simpleType>
                    </schema>
                    """.formatted(builtin));
            XsdParserInspector inspector = new XsdParserInspector(
                    XsdParserJaxpFactory.defaultFactory().createParser(schema));
            assertDoesNotThrow(() -> XsdDependencyValidator.validate(inspector, config("urn:example:single")), builtin);
        }
    }

    @Test
    void originalParserReportsRemainAvailable() {
        XsdParserInspector inspector = inspect("unprefixed-builtin.xsd");
        assertTrue(inspector.getUnsolvedReferences().contains("base64Binary"));
        assertFalse(inspector.getPreflightUnsolvedReferences().contains("base64Binary"));
    }

    @Test
    void validBuiltinDoesNotHideAnUnresolvedType() {
        XsdParserInspector inspector = inspect("unresolved-type.xsd");
        assertDependency(inspector, "urn:example:unresolved-type", "EncodedValue", XSD_NS, "base64Binary");
        MappingResolutionException failure = assertThrows(MappingResolutionException.class,
                () -> XsdDependencyValidator.validate(inspector, config("urn:example:unresolved-type")));
        assertTrue(failure.getMessage().contains("MissingType"));
    }

    @Test
    void sameLexicalNameInDifferentNamespacesRemainsUnresolved() {
        XsdParserInspector inspector = inspect("collision-entry.xsd");
        MappingResolutionException failure = assertThrows(MappingResolutionException.class,
                () -> XsdDependencyValidator.validate(inspector, config("urn:example:collision-entry")));
        assertTrue(failure.getMessage().contains("base64Binary"));
    }

    @Test
    void generatesBuiltinStringMappingAfterPreflight(@TempDir Path output) throws IOException {
        String namespace = "urn:example:encoded-value";
        var generator = XsdImporterDefaultSetup.setupInfrastructure();
        generator.generateModels(inspect("encoded-value.xsd"),
                config(namespace).addNamespaceMapping(namespace, "com.example.encoded"));
        generator.saveRuneModels(output);
        String model = Files.readString(output.resolve("com_example_encoded-type.rune"));
        assertTrue(model.contains("EncodedValue"));
        assertTrue(model.contains("string"));
    }

    private static XsdParserInspector inspect(String fixture) {
        return new XsdParserInspector(XsdParserJaxpFactory.defaultFactory()
                .createParser(FIXTURES.resolve(fixture)));
    }

    private static RuneGeneratorConfig config(String namespace) {
        return new RuneGeneratorConfig().addTargetNamespace(namespace);
    }

    private static void assertDependency(XsdParserInspector inspector, String sourceNamespace, String sourceName,
                                         String targetNamespace, String targetName) {
        RegistryKey source = RegistryKey.of(sourceNamespace, sourceName);
        String target = RegistryKey.of(targetNamespace, targetName).toString();
        assertTrue(inspector.getRequiredDependencies().stream().anyMatch(dependency ->
                dependency.source().key().equals(source)
                        && dependency.targetRole() == XsdDeclarationRole.TYPE
                        && dependency.targetQName().equals(target)),
                () -> "Expected " + source + " -> " + target + "; got " + inspector.getRequiredDependencies());
    }
}
