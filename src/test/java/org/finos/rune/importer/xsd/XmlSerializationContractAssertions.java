package org.finos.rune.importer.xsd;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Assertions for the published XML lexical contract that XMLUnit intentionally ignores. */
public final class XmlSerializationContractAssertions {

    private XmlSerializationContractAssertions() {
    }

    public static void assertRootScopedNamespaceDeclarations(String xml) throws Exception {
        Element root = document(xml).getDocumentElement();
        assertNoNamespaceDeclarationsBelow(root);
    }

    public static void assertRootBoundQualifiedElement(
            String xml,
            String localName,
            String namespace) throws Exception {
        Element root = document(xml).getDocumentElement();
        Element element = element(root, localName, namespace);
        assertRootBinding(root, element, namespace);
    }

    public static void assertRootBoundQualifiedAttribute(
            String xml,
            String elementLocalName,
            String elementNamespace,
            String attributeLocalName,
            String attributeNamespace) throws Exception {
        Element root = document(xml).getDocumentElement();
        Element element = element(root, elementLocalName, elementNamespace);
        Attr attribute = element.getAttributeNodeNS(attributeNamespace, attributeLocalName);
        assertNotNull(attribute, () -> "Expected attribute {" + attributeNamespace + "}" + attributeLocalName);
        String prefix = attribute.getPrefix();
        assertFalse(prefix == null || prefix.isBlank(), "Qualified XML attributes must use a prefix");
        assertEquals(attributeNamespace, root.lookupNamespaceURI(prefix));
    }

    public static void assertRootBoundXsiType(
            String xml,
            String elementLocalName,
            String elementNamespace,
            String typeNamespace,
            String typeLocalName) throws Exception {
        Element root = document(xml).getDocumentElement();
        Element element = element(root, elementLocalName, elementNamespace);
        Attr type = element.getAttributeNodeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type");
        assertNotNull(type, "Expected an xsi:type attribute");
        String value = type.getValue();
        int separator = value.indexOf(':');
        assertFalse(separator <= 0 || separator == value.length() - 1,
                "xsi:type must use a root-bound qualified QName: " + value);
        String prefix = value.substring(0, separator);
        assertEquals(typeNamespace, element.lookupNamespaceURI(prefix));
        assertEquals(typeLocalName, value.substring(separator + 1));
        assertEquals(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, root.lookupNamespaceURI("xsi"));
    }

    private static Document document(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Element element(Element root, String localName, String namespace) {
        if (localName.equals(root.getLocalName()) && namespace.equals(root.getNamespaceURI())) {
            return root;
        }
        NodeList elements = root.getElementsByTagNameNS(namespace, localName);
        assertFalse(elements.getLength() == 0, () -> "Expected element {" + namespace + "}" + localName);
        return (Element) elements.item(0);
    }

    private static void assertRootBinding(Element root, Element element, String namespace) {
        String prefix = element.getPrefix();
        assertFalse(prefix == null || prefix.isBlank(),
                () -> "Expected a root-bound prefix for element {" + namespace + "}" + element.getLocalName());
        assertEquals(namespace, root.lookupNamespaceURI(prefix));
    }

    private static void assertNoNamespaceDeclarationsBelow(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element childElement)) {
                continue;
            }
            for (int attributeIndex = 0; attributeIndex < childElement.getAttributes().getLength(); attributeIndex++) {
                Node attribute = childElement.getAttributes().item(attributeIndex);
                assertFalse(XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI()),
                        () -> "Namespace declaration must be owned by the document root: " + attribute.getNodeName());
            }
            assertNoNamespaceDeclarationsBelow(childElement);
        }
    }
}
