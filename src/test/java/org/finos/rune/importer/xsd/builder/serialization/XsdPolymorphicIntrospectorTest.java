package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.model.lib.RosettaModelObject;
import com.rosetta.model.lib.RosettaModelObjectBuilder;
import com.rosetta.model.lib.annotations.RuneDataType;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XsdPolymorphicIntrospectorTest {

    private static final String BASE_NAMESPACE = "http://example.com/tag/base";
    private static final String NAMESPACE_A = "http://example.com/tag/a";
    private static final String NAMESPACE_B = "http://example.com/tag/b";

    @Test
    void preservesConfiguredBaseTypeTagDeserialization() throws Exception {
        Object value = read(
                "<base:Base xmlns:base=\"" + BASE_NAMESPACE + "\"/>",
                configuration(entry(BaseType.class, BASE_NAMESPACE, "Base")));

        assertInstanceOf(BaseType.class, value);
    }

    @Test
    void resolvesSameLocalNameUsingTheXmlNamespaceUri() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(NamespaceAType.class, NAMESPACE_A, "Foo"),
                entry(NamespaceBType.class, NAMESPACE_B, "Foo"));

        Object value = read(
                "<a:Foo xmlns:a=\"" + NAMESPACE_A + "\"/>",
                configuration);

        assertEquals(NamespaceAType.class, value.getClass());
    }

    @Test
    void selectsTheOtherConfiguredTypeForTheOtherNamespace() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(NamespaceAType.class, NAMESPACE_A, "Foo"),
                entry(NamespaceBType.class, NAMESPACE_B, "Foo"));

        Object value = read(
                "<b:Foo xmlns:b=\"" + NAMESPACE_B + "\"/>",
                configuration);

        assertEquals(NamespaceBType.class, value.getClass());
    }

    @Test
    void preservesNamespaceThroughAParserDelegate() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(NamespaceAType.class, NAMESPACE_A, "Foo"),
                entry(NamespaceBType.class, NAMESPACE_B, "Foo"));

        Object value = readThroughDelegate(
                "<b:Foo xmlns:b=\"" + NAMESPACE_B + "\"/>",
                configuration);

        assertEquals(NamespaceBType.class, value.getClass());
    }

    @Test
    void rejectsAnUnqualifiedAmbiguousTagInsteadOfUsingTheFirstConfiguredType() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> read(
                "<Foo/>",
                configuration(
                        entry(NamespaceAType.class, NAMESPACE_A, "Foo"),
                        entry(NamespaceBType.class, NAMESPACE_B, "Foo"))));

        assertTrue(exception.getMessage().contains("Foo"));
        assertTrue(exception.getMessage().contains("ambiguous"));
        assertTrue(exception.getMessage().contains(BaseType.class.getName()));
    }

    @Test
    void rejectsAnUnknownTagInsteadOfUsingTheBaseType() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> read(
                "<base:Unknown xmlns:base=\"" + BASE_NAMESPACE + "\"/>",
                configuration(entry(BaseType.class, BASE_NAMESPACE, "Base"))));

        assertTrue(exception.getMessage().contains("Unknown"));
        assertTrue(exception.getMessage().contains(BASE_NAMESPACE));
        assertTrue(exception.getMessage().contains(BaseType.class.getName()));
    }

    @Test
    void rejectsAConfiguredLocalNameFromTheWrongNamespace() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> read(
                "<b:Foo xmlns:b=\"" + NAMESPACE_B + "\"/>",
                configuration(entry(NamespaceAType.class, NAMESPACE_A, "Foo"))));

        assertTrue(exception.getMessage().contains("Foo"));
        assertTrue(exception.getMessage().contains(NAMESPACE_B));
        assertTrue(exception.getMessage().contains(BaseType.class.getName()));
    }

    private Object read(String xml, RosettaXMLConfiguration configuration) throws Exception {
        XmlMapper mapper = new XmlMapper();
        mapper.registerModule(new XsdRuneModule(configuration));
        return mapper.readValue(xml, BaseType.class);
    }

    private Object readThroughDelegate(String xml, RosettaXMLConfiguration configuration) throws Exception {
        XmlMapper mapper = new XmlMapper();
        mapper.registerModule(new XsdRuneModule(configuration));
        try (JsonParser parser = mapper.createParser(xml)) {
            return mapper.readerFor(BaseType.class).readValue(new JsonParserDelegate(parser));
        }
    }

    @SafeVarargs
    private RosettaXMLConfiguration configuration(Map.Entry<Class<?>, TypeXMLConfiguration>... entries) {
        Map<ModelSymbolId, TypeXMLConfiguration> typeConfigs = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, TypeXMLConfiguration> entry : entries) {
            typeConfigs.put(ModelSymbolId.fromQualifiedName(entry.getKey().getName()), entry.getValue());
        }
        return new RosettaXMLConfiguration(typeConfigs);
    }

    private Map.Entry<Class<?>, TypeXMLConfiguration> entry(
            Class<?> type,
            String namespace,
            String localName) {
        return Map.entry(type, new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.of(localName),
                Optional.of(namespace + "/" + localName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    @RuneDataType(value = "BaseType", model = "test", builder = TestBuilder.class, version = "1")
    static class BaseType implements RosettaModelObject {
        @Override
        public RosettaModelObjectBuilder toBuilder() {
            return null;
        }

        @Override
        public RosettaModelObject build() {
            return this;
        }

        @Override
        public com.rosetta.model.lib.meta.RosettaMetaData<? extends RosettaModelObject> metaData() {
            return null;
        }

        @Override
        public Class<? extends RosettaModelObject> getType() {
            return BaseType.class;
        }

        @Override
        public void process(com.rosetta.model.lib.path.RosettaPath path,
                            com.rosetta.model.lib.process.Processor processor) {
        }
    }

    static class NamespaceAType extends BaseType {
    }

    static class NamespaceBType extends BaseType {
    }

    abstract static class TestBuilder implements RosettaModelObjectBuilder {
        @Override
        public <B extends RosettaModelObjectBuilder> B prune() {
            return null;
        }

        @Override
        public void process(com.rosetta.model.lib.path.RosettaPath path,
                             com.rosetta.model.lib.process.BuilderProcessor processor) {
        }

        @Override
        public boolean hasData() {
            return false;
        }

        @Override
        public <B extends RosettaModelObjectBuilder> B merge(
                B other,
                com.rosetta.model.lib.process.BuilderMerger merger) {
            return other;
        }

        @Override
        public RosettaModelObjectBuilder toBuilder() {
            return this;
        }

        @Override
        public RosettaModelObject build() {
            return null;
        }

        @Override
        public com.rosetta.model.lib.meta.RosettaMetaData<? extends RosettaModelObject> metaData() {
            return null;
        }

        @Override
        public Class<? extends RosettaModelObject> getType() {
            return BaseType.class;
        }

        @Override
        public void process(com.rosetta.model.lib.path.RosettaPath path,
                            com.rosetta.model.lib.process.Processor processor) {
        }
    }
}
