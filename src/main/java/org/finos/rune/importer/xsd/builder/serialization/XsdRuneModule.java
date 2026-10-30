package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the XML introspectors required by generated XSD-backed Rune models.
 *
 * <p>A {@code null} XML configuration is accepted for compatibility. In that mode,
 * configuration-dependent namespace and union customization falls back to the standard
 * Jackson behavior where the individual introspector can do so. Rune polymorphic
 * deserialization still requires XML configuration to resolve configured XML type
 * identities and must not be assumed to work with a {@code null} configuration.</p>
 */
public class XsdRuneModule extends SimpleModule {

    private static final Logger log = LoggerFactory.getLogger(XsdRuneModule.class);
    private final RosettaXMLConfiguration xmlConfig;

    /**
     * Creates the module for the supplied generated XML configuration.
     *
     * @param xmlConfig generated XML configuration, or {@code null} to retain the
     *                 compatibility fallback behavior described on this class
     */
    public XsdRuneModule(RosettaXMLConfiguration xmlConfig) {
        super("XsdRuneSerializationModule");
        this.xmlConfig = xmlConfig;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        log.info("Registering XsdRuneModule components...");

        AnnotationIntrospector pairedIntrospector = AnnotationIntrospectorPair.pair(
                new XsdAttributeNamespaceIntrospector(xmlConfig),
                AnnotationIntrospectorPair.pair(
                        new XsdPolymorphicIntrospector(xmlConfig),
                        new XsdUnionIntrospector(xmlConfig))
        );

        context.insertAnnotationIntrospector(pairedIntrospector);
    }
}
