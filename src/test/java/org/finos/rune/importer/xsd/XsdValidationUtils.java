package org.finos.rune.importer.xsd;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utilities for compiling and applying secure XSD 1.0 validation in integration tests.
 */
public final class XsdValidationUtils {

    private XsdValidationUtils() {
    }

    public static Schema compileSchema(Path schemaPath) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        return factory.newSchema(schemaPath.toFile());
    }

    public static void validate(Schema schema, Path xmlPath) throws IOException, ParserConfigurationException, SAXException {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        parserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        validator.validate(new SAXSource(reader, new InputSource(xmlPath.toUri().toString())));
    }
}
