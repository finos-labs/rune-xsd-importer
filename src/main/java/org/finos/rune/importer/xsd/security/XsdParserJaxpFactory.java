package org.finos.rune.importer.xsd.security;

import org.xml.sax.SAXException;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.core.utils.ParserConfig;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.nio.file.Path;

/**
 * Creates XSD parsers and importer-owned JAXP objects with fixed security settings.
 *
 * <p>This factory does not stage schemas or impose workload limits. The
 * parser library remains responsible for loading the schema graph, while this
 * factory applies the same secure XML settings to parser construction and
 * importer code that needs a DOM builder.</p>
 */
public final class XsdParserJaxpFactory {

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final ThreadLocal<XsdParserJaxpFactory> ACTIVE_FACTORY = new ThreadLocal<>();

    private XsdParserJaxpFactory() {
    }

    public static XsdParserJaxpFactory defaultFactory() {
        return new XsdParserJaxpFactory();
    }

    /** Creates the xmlet parser with the default xmlet parser configuration. */
    public XsdParser createParser(Path entryPoint) {
        requireEntryPoint(entryPoint);
        return constructParser(entryPoint, null);
    }

    /** Creates the xmlet parser with an operation-specific parser configuration. */
    public XsdParser createParser(Path entryPoint, ParserConfig parserConfig) {
        requireEntryPoint(entryPoint);
        if (parserConfig == null) {
            return createParser(entryPoint);
        }
        return constructParser(entryPoint, parserConfig);
    }

    /** Creates a DOM builder with the importer's fixed secure XML settings. */
    public DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entity resolution is disabled");
            });
            return builder;
        } catch (ParserConfigurationException e) {
            throw new XsdSecurityException("The JDK XML parser cannot satisfy the required security settings", e);
        }
    }

    /** Creates a DOM factory with the importer's fixed secure XML settings. */
    private DocumentBuilderFactory newDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeature(factory, DISALLOW_DOCTYPE, true);
            setFeature(factory, EXTERNAL_GENERAL_ENTITIES, false);
            setFeature(factory, EXTERNAL_PARAMETER_ENTITIES, false);
            setFeature(factory, LOAD_EXTERNAL_DTD, false);
            setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            setAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory;
        } catch (ParserConfigurationException | IllegalArgumentException e) {
            throw new XsdSecurityException("The JDK XML parser cannot satisfy the required security settings", e);
        }
    }

    private static void requireEntryPoint(Path entryPoint) {
        if (entryPoint == null) {
            throw new IllegalArgumentException("entryPoint must not be null");
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value)
            throws ParserConfigurationException {
        factory.setFeature(feature, value);
    }

    private static void setAttribute(DocumentBuilderFactory factory, String name, String value) {
        factory.setAttribute(name, value);
    }

    private XsdParser constructParser(Path entryPoint, ParserConfig parserConfig) {
        ACTIVE_FACTORY.set(this);
        try {
            String path = entryPoint.toAbsolutePath().toString();
            if (parserConfig == null) {
                return new SecuredXsdParser(path);
            }
            return new SecuredXsdParser(path, parserConfig);
        } finally {
            ACTIVE_FACTORY.remove();
        }
    }

    /**
     * xmlet constructs its DOM builder internally. This adapter supplies the
     * factory settings during that eager constructor parse without changing the
     * third-party library or global JAXP provider settings.
     */
    private static final class SecuredXsdParser extends XsdParser {
        private SecuredXsdParser(String filePath) {
            super(filePath);
        }

        private SecuredXsdParser(String filePath, ParserConfig parserConfig) {
            super(filePath, parserConfig);
        }

        @Override
        protected javax.xml.parsers.DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
            XsdParserJaxpFactory factory = ACTIVE_FACTORY.get();
            if (factory == null) {
                throw new IllegalStateException("XSD parser construction requires active security settings");
            }
            return factory.newDocumentBuilder();
        }
    }
}
