package org.finos.rune.importer.xsd.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuneGeneratorConfigTest {

    private static final String UNKNOWN_PACKAGE = "org.finos.rune.unknown";

    @Test
    void acceptsNullTargetNamespaceAccordingToCurrentSetSemantics() {
        RuneGeneratorConfig config = new RuneGeneratorConfig().addTargetNamespace(null);

        assertTrue(config.shouldGenerate(null));
        assertFalse(config.shouldGenerate(""));
    }

    @Test
    void acceptsBlankTargetNamespacesWithoutNormalization() {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace("")
                .addTargetNamespace("   ");

        assertTrue(config.shouldGenerate(""));
        assertTrue(config.shouldGenerate("   "));
        assertFalse(config.shouldGenerate("  "));
    }

    @Test
    void preservesNullAndBlankNamespaceMappingKeysAndValues() {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addNamespaceMapping(null, null)
                .addNamespaceMapping("", "")
                .addNamespaceMapping("   ", "   ");

        assertNull(config.getRunePackage(null));
        assertEquals("", config.getRunePackage(""));
        assertEquals("   ", config.getRunePackage("   "));
    }

    @Test
    void returnsUnknownPackageForUnmappedNamespaces() {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addNamespaceMapping("http://example.com/mapped", "com.example.mapped");

        assertEquals("com.example.mapped", config.getRunePackage("http://example.com/mapped"));
        assertEquals(UNKNOWN_PACKAGE, config.getRunePackage("http://example.com/external"));
        assertEquals(UNKNOWN_PACKAGE, config.getRunePackage("http://example.com/target"));
    }

    @Test
    void replacesExistingNamespaceMappingWithoutValidation() {
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addNamespaceMapping("http://example.com/schema", "com.example.first")
                .addNamespaceMapping("http://example.com/schema", "com.example.second");

        assertEquals("com.example.second", config.getRunePackage("http://example.com/schema"));
    }
}
