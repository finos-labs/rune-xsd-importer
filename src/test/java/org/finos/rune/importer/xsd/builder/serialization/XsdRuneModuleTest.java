package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XsdRuneModuleTest {

    private static final String TYPE_NAMESPACE = "http://example.com/module/type";

    @Test
    void customPolymorphicIntrospectionTakesPrecedenceOverStandardXmlMapperBehavior() throws Exception {
        String xml = "<a:Foo xmlns:a=\"" + TYPE_NAMESPACE + "\"/>";
        RosettaXMLConfiguration configuration = configuration(
                Map.entry(XsdPolymorphicIntrospectorTest.NamespaceAType.class,
                        typeConfiguration(TYPE_NAMESPACE, "Foo")));

        Object standardValue;
        try {
            standardValue = new XmlMapper().readValue(xml, XsdPolymorphicIntrospectorTest.BaseType.class);
        } catch (JsonProcessingException exception) {
            standardValue = null;
        }

        XmlMapper configuredMapper = new XmlMapper();
        configuredMapper.registerModule(new XsdRuneModule(configuration));
        Object configuredValue = configuredMapper.readValue(
                xml,
                XsdPolymorphicIntrospectorTest.BaseType.class);

        assertTrue(standardValue == null
                        || !XsdPolymorphicIntrospectorTest.NamespaceAType.class.equals(standardValue.getClass()),
                "The standard XmlMapper must not resolve the configured XSD subtype by itself");
        assertEquals(XsdPolymorphicIntrospectorTest.NamespaceAType.class, configuredValue.getClass());
    }

    @Test
    void nullConfigurationRetainsStandardMapperBehaviorForUnconfiguredTypes() throws Exception {
        XmlMapper mapper = new XmlMapper();
        mapper.registerModule(new XsdRuneModule(null));

        String xml = mapper.writeValueAsString(new PlainXmlValue("fallback"));

        assertTrue(xml.contains("<value>fallback</value>"));
        assertFalse(xml.contains("xmlns:xsi"));
    }

    @SafeVarargs
    private final RosettaXMLConfiguration configuration(
            Map.Entry<Class<?>, TypeXMLConfiguration>... entries) {
        Map<ModelSymbolId, TypeXMLConfiguration> typeConfigs = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, TypeXMLConfiguration> entry : entries) {
            typeConfigs.put(ModelSymbolId.fromQualifiedName(entry.getKey().getName()), entry.getValue());
        }
        return new RosettaXMLConfiguration(typeConfigs);
    }

    private TypeXMLConfiguration typeConfiguration(String namespace, String localName) {
        return new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.of(localName),
                Optional.of(namespace + "/" + localName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static class PlainXmlValue {
        public String value;

        PlainXmlValue() {
        }

        PlainXmlValue(String value) {
            this.value = value;
        }
    }
}
