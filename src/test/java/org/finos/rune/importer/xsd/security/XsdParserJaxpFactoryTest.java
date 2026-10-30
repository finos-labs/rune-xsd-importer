package org.finos.rune.importer.xsd.security;

import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;
import org.xmlet.xsdparser.core.XsdParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XsdParserJaxpFactoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultFactoryParsesAValidLocalSchemaGraph() throws IOException {
        Path imported = write("imported.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:imported"
                           xmlns:imp="urn:imported">
                    <xs:simpleType name="ImportedText">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """);
        Path entry = write("entry.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:entry"
                           xmlns:imp="urn:imported">
                    <xs:import namespace="urn:imported" schemaLocation="%s"/>
                    <xs:element name="entry" type="imp:ImportedText"/>
                </xs:schema>
                """.formatted(imported.getFileName()));

        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(entry);

        assertDoesNotThrow(() -> new XsdParserInspector(parser).getLoadedNamespaces());
    }

    @Test
    void defaultDocumentBuilderRejectsDoctypeAndExternalEntityInput() throws IOException {
        Path secret = write("secret.txt", "do not read");
        Path xxe = write("xxe.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE root [<!ENTITY secret SYSTEM "%s">]>
                <root>&secret;</root>
                """.formatted(secret.toUri()));

        assertThrows(SAXException.class,
                () -> XsdParserJaxpFactory.defaultFactory().newDocumentBuilder().parse(xxe.toFile()));
    }

    @Test
    void defaultFactoryRejectsDoctypeDeclarations() throws IOException {
        Path xml = write("doctype.xml", """
                <!DOCTYPE root [<!ELEMENT root (#PCDATA)>]>
                <root>text</root>
                """);

        assertThrows(SAXException.class,
                () -> XsdParserJaxpFactory.defaultFactory().newDocumentBuilder().parse(xml.toFile()));
    }

    private Path write(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
