package org.finos.rune.importer.xsd.regression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.rosetta.model.lib.RosettaModelObject;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.integration.AbstractRuneIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the collision between an ordinary XSD attribute named {@code type}
 * and the type-id property used by the Rune XML polymorphism introspector.
 */
@Tag("regression")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdinaryTypeAttributeRegressionTest extends AbstractRuneIntegrationTest {

    private static final Path RESOURCE_ROOT = Path.of(
            "src/test/resources/org/finos/rune/xsd/regression/ordinarytype");
    private static final Path XSD_FILE = RESOURCE_ROOT.resolve("ordinary-type-attribute.xsd");
    private static final Path XML_FILE = RESOURCE_ROOT.resolve("ordinary-type-attributes.valid.xml");
    private static final String NAMESPACE = "http://example.com/ordinary-type";
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String PACKAGE = "com.example.ordinarytype";

    @Test
    void distinguishesOrdinaryTypeFromXsiTypeInOneXmlRoundTrip() throws Exception {
        XsdValidationUtils.validate(getSourceSchema(XSD_FILE), XML_FILE);

        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> targetClass = state.compiledModel().getTypeJavaClass("Document");
        assertNotNull(targetClass, "Expected generated Document model");

        ObjectMapper mapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        Object parsed = mapper.readValue(Files.readString(XML_FILE, StandardCharsets.UTF_8), targetClass);
        Object ordinaryItem = targetClass.getMethod("getOrdinaryItem").invoke(parsed);
        assertNotNull(ordinaryItem, "Expected the ordinary type attribute element");
        Object polymorphicItem = targetClass.getMethod("getPolymorphicItem").invoke(parsed);
        Class<?> derivedType = state.compiledModel().getTypeJavaClass("DerivedPolymorphicItem");
        assertNotNull(derivedType, "Expected generated derived polymorphic type");
        assertTrue(derivedType.isInstance(polymorphicItem),
                "Expected xsi:type to select DerivedPolymorphicItem");

        String generated = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        assertAttributeDistinction(generated);

        Path roundTripXml = state.generatedOutput().resolve("xml/ordinary-type-attributes.roundtrip.xml");
        Files.createDirectories(roundTripXml.getParent());
        Files.writeString(roundTripXml, generated, StandardCharsets.UTF_8);
        XsdValidationUtils.validate(getSourceSchema(XSD_FILE), roundTripXml);
    }

    @Test
    void rejectsUnknownXsiTypeEvenWhenOrdinaryTypeAttributeIsPresent() throws Exception {
        ScenarioState state = getScenarioState();
        Class<? extends RosettaModelObject> targetClass = state.compiledModel().getTypeJavaClass("Document");
        assertNotNull(targetClass, "Expected generated Document model");
        ObjectMapper mapper = getXmlMapper(
                state.generatedOutput().resolve("config/xml-config.json"), targetClass.getClassLoader());
        String invalidXml = Files.readString(XML_FILE, StandardCharsets.UTF_8)
                .replace("tns:DerivedPolymorphicItem", "tns:UnknownPolymorphicItem");

        InvalidTypeIdException failure = assertThrows(InvalidTypeIdException.class,
                () -> mapper.readValue(invalidXml, targetClass));
        assertTrue(failure.getMessage().contains("UnknownPolymorphicItem"), failure.getMessage());
    }

    private void assertAttributeDistinction(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element ordinaryItem = element(document, "ordinaryItem");
        Element polymorphicItem = element(document, "polymorphicItem");

        assertEquals("business-value", unqualifiedTypeAttribute(ordinaryItem).getValue());
        assertNull(ordinaryItem.getAttributeNodeNS(XSI_NAMESPACE, "type"),
                "ordinaryItem must not have xsi:type");

        assertEquals("business-value", unqualifiedTypeAttribute(polymorphicItem).getValue());
        Attr xsiType = polymorphicItem.getAttributeNodeNS(XSI_NAMESPACE, "type");
        assertNotNull(xsiType, "polymorphicItem must retain xsi:type");
        int separator = xsiType.getValue().indexOf(':');
        assertTrue(separator > 0, "xsi:type must contain a namespace-qualified type name");
        assertEquals(NAMESPACE, polymorphicItem.lookupNamespaceURI(xsiType.getValue().substring(0, separator)));
        assertEquals("DerivedPolymorphicItem", xsiType.getValue().substring(separator + 1));
    }

    private Element element(Document document, String localName) {
        NodeList elements = document.getElementsByTagNameNS(NAMESPACE, localName);
        assertTrue(elements.getLength() > 0, "Expected namespaced element " + localName);
        return (Element) elements.item(0);
    }

    private Attr unqualifiedTypeAttribute(Element element) {
        Attr attribute = element.getAttributeNodeNS(null, "type");
        assertNotNull(attribute, "Expected an unqualified type attribute on " + element.getLocalName());
        return attribute;
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        Path output = temporaryDirectory.resolve("generated");
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(NAMESPACE)
                .addNamespaceMapping(NAMESPACE, PACKAGE);
        return generateRuneModels(XSD_FILE, output, config);
    }

    @Override
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        return compileRuneSources(RuneTestModelUtils.readRuneSources(generatedOutput, PACKAGE));
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        assertTrue(java.nio.file.Files.exists(generatedOutput.resolve("config/xml-config.json")));
    }
}
