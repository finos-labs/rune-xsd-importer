package org.finos.rune.importer.xsd.mapper.mapping;

import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeNameIndexTest {

    @Test
    void recordsOneStableGeneratedNamePerQualifiedSource() {
        TypeNameIndex index = new TypeNameIndex();
        RegistryKey source = RegistryKey.of("http://example.com/source", "trade-header");

        assertEquals("TradeHeader", index.register(source, "TradeHeader", false));
        assertEquals("TradeHeader", index.register(source, "TradeHeader", false));
        assertThrows(IllegalStateException.class,
                () -> index.register(source, "DifferentName", false));
    }

    @Test
    void rejectsDistinctSourcesThatNormalizeToTheSameGeneratedName() {
        TypeNameIndex index = new TypeNameIndex();
        index.register(RegistryKey.of("http://example.com/source", "a-b"), "AB", false);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> index.register(RegistryKey.of("http://example.com/source", "a_b"), "Ab", false));

        assertTrue(failure.getMessage().contains("a-b"));
        assertTrue(failure.getMessage().contains("a_b"));
    }

    @Test
    void permitsAnExplicitAliasOfAnExistingGeneratedName() {
        TypeNameIndex index = new TypeNameIndex();
        index.register(RegistryKey.of("http://example.com/source", "Product"), "Product", false);

        assertEquals("Product", index.register(
                RegistryKey.of("http://example.com/source", "product"), "Product", true));
        assertTrue(index.hasCollision(
                RegistryKey.of("http://example.com/source", "product"), "Product"));
    }
}
