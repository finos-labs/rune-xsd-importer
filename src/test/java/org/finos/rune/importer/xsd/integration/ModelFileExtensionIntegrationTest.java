package org.finos.rune.importer.xsd.integration;

import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelFileExtensionIntegrationTest extends AbstractRuneIntegrationTest {

    private static final Path SIMPLE_XSD = Path.of(
            "src/test/resources/org/finos/rune/xsd/integration/features/misc/simple-test.xsd");
    private static final String TARGET_NAMESPACE = "http://example.com/simple";
    private static final String RUNE_PACKAGE = "com.example.simple";

    @Test
    void generatesSavesRegistersAndDiscoversRosettaModelFiles(@TempDir Path outputRoot) throws Exception {
        XsdImporterConfig importerConfig = XsdImporterConfig.builder()
                .modelFileExtension(".ROSETTA")
                .build();

        Path generatedOutput = generateRuneModels(SIMPLE_XSD, outputRoot, generationConfig(), importerConfig);

        assertTrue(Files.exists(generatedOutput.resolve("com_example_simple-type.rosetta")));
        assertTrue(Files.exists(generatedOutput.resolve("com_example_simple-enum.rosetta")));
        assertFalse(Files.exists(generatedOutput.resolve("com_example_simple-type.rune")));

        List<String> sources = RuneTestModelUtils.readRuneSources(
                generatedOutput,
                RUNE_PACKAGE,
                importerConfig.getModelFileExtension());
        assertFalse(sources.isEmpty());
        compileRuneSources(sources);
    }

    private RuneGeneratorConfig generationConfig() {
        return new RuneGeneratorConfig()
                .addTargetNamespace(TARGET_NAMESPACE)
                .addNamespaceMapping(TARGET_NAMESPACE, RUNE_PACKAGE);
    }

    @Override
    protected Path generateScenarioModels() throws IOException {
        throw new UnsupportedOperationException("This test exercises its extension-specific flow directly");
    }

    @Override
    protected void assertGeneratedModelContract(Path generatedOutput) {
        // This class uses an explicit extension-specific assertion path.
    }
}
