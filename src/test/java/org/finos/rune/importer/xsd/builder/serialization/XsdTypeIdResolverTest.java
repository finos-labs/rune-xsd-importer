package org.finos.rune.importer.xsd.builder.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XsdTypeIdResolverTest {

    private static final String BASE_NAMESPACE = "http://example.com/test/base";
    private static final String EXT_NAMESPACE = "http://example.com/test/extension";

    @Test
    void resolvesAConfiguredUniqueUnqualifiedTypeId() throws Exception {
        JavaType resolved = resolve(
                ResolverBaseType.class,
                configuration(entry(ResolverAllowedType.class, BASE_NAMESPACE, "AllowedType")),
                "AllowedType");

        assertEquals(ResolverAllowedType.class, resolved.getRawClass());
    }

    @Test
    void resolvesAConfiguredPrefixedTypeIdUsingTheXmlNamespaceUri() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo"),
                entry(ResolverNamespaceBType.class, EXT_NAMESPACE, "Foo"));

        JavaType resolved = resolveFromXml(
                ResolverBaseType.class,
                configuration,
                "a:Foo",
                "<root xmlns:a=\"" + BASE_NAMESPACE + "\" xmlns:b=\"" + EXT_NAMESPACE + "\"/>");

        assertEquals(ResolverNamespaceAType.class, resolved.getRawClass());
    }

    @Test
    void rejectsAnUnknownTypeIdInsteadOfUsingCanonicalClassResolution() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolve(
                ResolverBaseType.class,
                configuration(entry(ResolverAllowedType.class, BASE_NAMESPACE, "AllowedType")),
                "MissingType"));

        assertTrue(exception.getMessage().contains("MissingType"));
        assertTrue(exception.getMessage().contains(ResolverBaseType.class.getName()));
    }

    @Test
    void rejectsAJavaCanonicalNameThatIsNotConfigured() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolve(
                ResolverBaseType.class,
                configuration(entry(ResolverAllowedType.class, BASE_NAMESPACE, "AllowedType")),
                ResolverAllowedType.class.getName()));

        assertTrue(exception.getMessage().contains(ResolverAllowedType.class.getName()));
    }

    @Test
    void treatsTypeIdsAsCaseSensitive() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolve(
                ResolverBaseType.class,
                configuration(entry(ResolverAllowedType.class, BASE_NAMESPACE, "AllowedType")),
                "allowedtype"));

        assertTrue(exception.getMessage().contains("allowedtype"));
    }

    @Test
    void rejectsAnAmbiguousUnqualifiedLocalName() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolve(
                ResolverBaseType.class,
                configuration(
                        entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo"),
                        entry(ResolverNamespaceBType.class, EXT_NAMESPACE, "Foo")),
                "Foo"));

        assertTrue(exception.getMessage().contains("ambiguous"));
    }

    @Test
    void selectsTheCorrectTypeWhenSameLocalNamesUseDifferentPrefixes() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo"),
                entry(ResolverNamespaceBType.class, EXT_NAMESPACE, "Foo"));

        JavaType resolved = resolveFromXml(
                ResolverBaseType.class,
                configuration,
                "b:Foo",
                "<root xmlns:a=\"" + BASE_NAMESPACE + "\" xmlns:b=\"" + EXT_NAMESPACE + "\"/>");

        assertEquals(ResolverNamespaceBType.class, resolved.getRawClass());
    }

    @Test
    void rejectsAnUnknownPrefix() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolveFromXml(
                ResolverBaseType.class,
                configuration(entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo")),
                "missing:Foo",
                "<root xmlns:a=\"" + BASE_NAMESPACE + "\"/>"));

        assertTrue(exception.getMessage().contains("missing:Foo"));
    }

    @Test
    void rejectsAResolvedPrefixThatDoesNotMatchTheConfiguredNamespace() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolveFromXml(
                ResolverBaseType.class,
                configuration(entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo")),
                "b:Foo",
                "<root xmlns:b=\"" + EXT_NAMESPACE + "\"/>"));

        assertTrue(exception.getMessage().contains("b:Foo"));
    }

    @Test
    void rejectsAConfiguredTypeThatIsNotAssignableToTheBaseType() {
        JsonMappingException exception = assertThrows(JsonMappingException.class, () -> resolve(
                ResolverBaseType.class,
                configuration(entry(ResolverUnrelatedType.class, BASE_NAMESPACE, "UnrelatedType")),
                "UnrelatedType"));

        assertTrue(exception.getMessage().contains("not assignable"));
    }

    @Test
    void resolvesTagNamesAgainstTheExplicitXmlNamespace() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo"),
                entry(ResolverNamespaceBType.class, EXT_NAMESPACE, "Foo"));
        ObjectMapper mapper = new ObjectMapper();

        try (JsonParser parser = mapper.createParser("{}")) {
            parser.nextToken();
            DatabindContext context = context(mapper, parser);
            JavaType resolved = new XsdTypeIdResolver(
                    mapper.getTypeFactory().constructType(ResolverBaseType.class), configuration)
                    .typeFromXmlTag(context, "Foo", EXT_NAMESPACE, false);

            assertEquals(ResolverNamespaceBType.class, resolved.getRawClass());
        }
    }

    @Test
    void rejectsAConfiguredTagFromTheWrongXmlNamespace() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(ResolverNamespaceAType.class, BASE_NAMESPACE, "Foo"));
        ObjectMapper mapper = new ObjectMapper();

        try (JsonParser parser = mapper.createParser("{}")) {
            parser.nextToken();
            DatabindContext context = context(mapper, parser);
            JsonMappingException exception = assertThrows(JsonMappingException.class, () ->
                    new XsdTypeIdResolver(
                            mapper.getTypeFactory().constructType(ResolverBaseType.class), configuration)
                            .typeFromXmlTag(context, "Foo", EXT_NAMESPACE, false));

            assertTrue(exception.getMessage().contains("Foo"));
            assertTrue(exception.getMessage().contains(ResolverBaseType.class.getName()));
        }
    }

    @Test
    void rejectsAnUnknownXmlTagInsteadOfAllowingBaseTypeFallback() throws Exception {
        RosettaXMLConfiguration configuration = configuration(
                entry(ResolverBaseType.class, BASE_NAMESPACE, "Base"));
        ObjectMapper mapper = new ObjectMapper();

        try (JsonParser parser = mapper.createParser("{}")) {
            parser.nextToken();
            DatabindContext context = context(mapper, parser);
            JsonMappingException exception = assertThrows(JsonMappingException.class, () ->
                    new XsdTypeIdResolver(
                            mapper.getTypeFactory().constructType(ResolverBaseType.class), configuration)
                            .typeFromXmlTag(context, "Unknown", BASE_NAMESPACE, false));

            assertTrue(exception.getMessage().contains("Unknown"));
            assertTrue(exception.getMessage().contains(BASE_NAMESPACE));
            assertTrue(exception.getMessage().contains(ResolverBaseType.class.getName()));
        }
    }

    private JavaType resolve(Class<?> baseType, RosettaXMLConfiguration configuration, String id) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (JsonParser parser = mapper.createParser("{}")) {
            parser.nextToken();
            return new XsdTypeIdResolver(
                    mapper.getTypeFactory().constructType(baseType), configuration)
                    .typeFromId(context(mapper, parser), id);
        }
    }

    private JavaType resolveFromXml(
            Class<?> baseType,
            RosettaXMLConfiguration configuration,
            String id,
            String xml) throws Exception {
        XmlMapper mapper = new XmlMapper();
        try (JsonParser parser = mapper.createParser(xml)) {
            parser.nextToken();
            return new XsdTypeIdResolver(
                    mapper.getTypeFactory().constructType(baseType), configuration)
                    .typeFromId(context(mapper, parser), id);
        }
    }

    private DatabindContext context(ObjectMapper mapper, JsonParser parser) {
        DefaultDeserializationContext baseContext = (DefaultDeserializationContext) mapper.getDeserializationContext();
        return baseContext.createInstance(mapper.getDeserializationConfig(), parser, null);
    }

    @SafeVarargs
    private RosettaXMLConfiguration configuration(Map.Entry<Class<?>, TypeXMLConfiguration>... entries) {
        Map<ModelSymbolId, TypeXMLConfiguration> typeConfigs = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, TypeXMLConfiguration> entry : entries) {
            typeConfigs.put(ModelSymbolId.fromQualifiedName(entry.getKey().getName()), entry.getValue());
        }
        return new RosettaXMLConfiguration(typeConfigs);
    }

    private Map.Entry<Class<?>, TypeXMLConfiguration> entry(Class<?> type, String namespace, String localName) {
        return Map.entry(type, new TypeXMLConfiguration(
                Optional.empty(),
                Optional.empty(),
                Optional.of(namespace + "/" + localName),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    }

    static class ResolverBaseType {
    }

    static class ResolverAllowedType extends ResolverBaseType {
    }

    static class ResolverNamespaceAType extends ResolverBaseType {
    }

    static class ResolverNamespaceBType extends ResolverBaseType {
    }

    static class ResolverUnrelatedType {
    }
}
