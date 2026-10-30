package org.finos.rune.importer.xsd.mapper.serialization;

import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XmlConfigurationJsonWriterTest {

    @Test
    void rejectsNullConfigurationBeforeWriting(@TempDir Path temporaryDirectory) {
        Path output = temporaryDirectory.resolve("xml-config.json");

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new XmlConfigurationJsonWriter().writeConfig(null, output));

        assertEquals("config must not be null", failure.getMessage());
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsNullPathBeforeCheckingExtension() {
        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new XmlConfigurationJsonWriter().writeConfig(configuration(), null));

        assertEquals("filePath must not be null", failure.getMessage());
    }

    @Test
    void rejectsPathsWithoutExactLowercaseJsonSuffix(@TempDir Path temporaryDirectory) {
        XmlConfigurationJsonWriter writer = new XmlConfigurationJsonWriter();

        for (String fileName : new String[]{"xml-config.JSON", "xml-config.txt", "xml-config.json.bak"}) {
            Path output = temporaryDirectory.resolve(fileName);

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> writer.writeConfig(configuration(), output));

            assertTrue(failure.getMessage().contains(fileName));
            assertFalse(Files.exists(output));
        }
    }

    @Test
    void writesConfigurationAndLoadsItThroughRuntimeCompatibilityPath(@TempDir Path temporaryDirectory)
            throws IOException {
        Path output = temporaryDirectory.resolve("xml-config.json");
        XmlConfigurationJsonWriter writer = new XmlConfigurationJsonWriter();

        writer.writeConfig(configuration(), output);

        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);
        try (InputStream input = Files.newInputStream(output)) {
            assertNotNull(RosettaXMLConfiguration.load(input));
        }
    }

    @Test
    void doesNotCreateMissingParentDirectories(@TempDir Path temporaryDirectory) {
        Path output = temporaryDirectory.resolve("missing").resolve("xml-config.json");

        IOException failure = assertThrows(
                IOException.class,
                () -> new XmlConfigurationJsonWriter().writeConfig(configuration(), output));

        assertTrue(failure.getMessage().contains(output.toString()));
        assertNotNull(failure.getCause());
        assertFalse(Files.exists(output.getParent()));
    }

    @Test
    void producesByteForByteRepeatableOutputForPopulatedConfiguration(@TempDir Path temporaryDirectory)
            throws IOException {
        Path output = temporaryDirectory.resolve("xml-config.json");
        XmlConfigurationJsonWriter writer = new XmlConfigurationJsonWriter();
        RosettaXMLConfiguration configuration = populatedConfiguration();

        writer.writeConfig(configuration, output);
        byte[] firstWrite = Files.readAllBytes(output);
        writer.writeConfig(configuration, output);
        byte[] secondWrite = Files.readAllBytes(output);

        assertArrayEquals(firstWrite, secondWrite);
        String serialized = Files.readString(output);
        assertTrue(serialized.contains("Product"));
        assertTrue(serialized.contains("ACTIVE"));
        assertTrue(serialized.contains("xmlns:tns"));
    }

    private RosettaXMLConfiguration configuration() {
        return new RosettaXMLConfiguration(Map.of());
    }

    private RosettaXMLConfiguration populatedConfiguration() {
        Map<String, String> namespaceAttributes = new LinkedHashMap<>();
        namespaceAttributes.put("xmlns:tns", "http://example.com/product");
        namespaceAttributes.put("xmlns:common", "http://example.com/common");

        Map<String, String> enumValues = new LinkedHashMap<>();
        enumValues.put("ACTIVE", "active");
        enumValues.put("INACTIVE", "inactive");

        TypeXMLConfiguration typeConfiguration = new TypeXMLConfiguration(
                Optional.of(ModelSymbolId.fromQualifiedName("com.example.product.BaseProduct")),
                Optional.empty(),
                Optional.of("Product"),
                Optional.of("http://example.com/product/Product"),
                Optional.of(false),
                Optional.of(namespaceAttributes),
                Optional.empty(),
                Optional.of(enumValues));

        return new RosettaXMLConfiguration(Map.of(
                ModelSymbolId.fromQualifiedName("com.example.product.Product"),
                typeConfiguration));
    }
}
