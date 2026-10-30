package org.finos.rune.importer.xsd.mapper.mapping;

import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SimpleTypeBindingTest {

    @Test
    void selectsTheExistingSimpleTypeGenerationPaths() {
        XsdParserInspector inspector = inspector("simpletype/simpletype-union.xsd");
        String namespace = "http://example.com/rune/union";

        assertEquals(SimpleTypeBinding.ENUMERATION, binding(inspector, namespace, "CodeEnum"));
        assertEquals(SimpleTypeBinding.ENUM_UNION, binding(inspector, namespace, "TradeStatusEnum"));
        assertEquals(SimpleTypeBinding.MIXED_UNION, binding(inspector, namespace, "CodeOrSmallInt"));
        assertEquals(SimpleTypeBinding.TYPE_ALIAS, binding(inspector, namespace, "SmallInt"));
    }

    @Test
    void selectsAndMemoizesTheEmptyStringAliasBinding() {
        XsdParserInspector inspector = inspector("misc/composite-product.xsd");
        String namespace = inspector.getLoadedNamespaces().stream().findFirst().orElseThrow();
        RegistryKey key = RegistryKey.of(namespace, "EmptyValue");
        MappingSession session = new MappingSession();

        SimpleTypeBinding first = session.simpleTypeBinding(key, inspector);
        SimpleTypeBinding second = session.simpleTypeBinding(key, inspector);

        assertEquals(SimpleTypeBinding.EMPTY_STRING_TYPE_ALIAS, first);
        assertSame(first, second);
    }

    private SimpleTypeBinding binding(XsdParserInspector inspector, String namespace, String localName) {
        return new MappingSession().simpleTypeBinding(RegistryKey.of(namespace, localName), inspector);
    }

    private XsdParserInspector inspector(String resource) {
        Path path = Path.of("src/test/resources/org/finos/rune/xsd/integration/features", resource);
        return new XsdParserInspector(XsdUtils.parseXsd(path));
    }
}
