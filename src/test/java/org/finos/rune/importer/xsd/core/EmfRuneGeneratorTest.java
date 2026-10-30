package org.finos.rune.importer.xsd.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.regnosys.rosetta.RosettaStandaloneSetup;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.IResourceFactory;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.XsdUtils;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmfRuneGeneratorTest {

    private static final Path SIMPLE_XSD = Path.of(
            "src/test/resources/org/finos/rune/xsd/integration/features/misc/simple-test.xsd");
    private static final String TARGET_NAMESPACE = "http://example.com/simple";

    @Test
    void generatesThenPersistsRuneModelsAndXmlConfiguration(@TempDir Path outputRoot) throws IOException {
        EmfRuneGenerator generator = newGenerator();

        generator.generateModels(inspector(), config());
        generator.saveRuneModels(outputRoot);
        generator.saveXmlConfiguration(outputRoot.resolve("config/xml-config.json"));

        assertTrue(Files.exists(outputRoot.resolve("com_example_simple-type.rune")));
        assertTrue(Files.exists(outputRoot.resolve("com_example_simple-enum.rune")));
        assertTrue(Files.exists(outputRoot.resolve("config/xml-config.json")));
    }

    @Test
    void explicitRuneExtensionMatchesTheDefaultOutput(@TempDir Path outputRoot) throws IOException {
        EmfRuneGenerator generator = newGenerator();

        generator.generateModels(
                inspector(),
                config(),
                XsdImporterConfig.builder()
                        .modelFileExtension(" RUNE ")
                        .build());
        generator.saveRuneModels(outputRoot);

        assertTrue(Files.exists(outputRoot.resolve("com_example_simple-type.rune")));
        assertTrue(Files.exists(outputRoot.resolve("com_example_simple-enum.rune")));
        try (var files = Files.walk(outputRoot)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".rosetta")));
        }
    }

    @Test
    void characterizesConfigurationAndPersistenceBeforeGeneration(@TempDir Path outputRoot) throws IOException {
        EmfRuneGenerator generator = newGenerator();

        assertNotNull(generator.generateXmlConfiguration());
        generator.saveRuneModels(outputRoot);
        generator.saveXmlConfiguration(outputRoot.resolve("config/xml-config.json"));

        try (var files = Files.walk(outputRoot)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".rune")));
        }

        JsonNode configuration = new ObjectMapper().readTree(
                outputRoot.resolve("config/xml-config.json").toFile());
        assertTrue(configuration.isObject());
        assertTrue(configuration.isEmpty());
    }

    @Test
    void treatsGeneratorAsSingleUseBecauseStateIsRetained() {
        EmfRuneGenerator generator = newGenerator();
        XsdParserInspector inspector = inspector();
        RuneGeneratorConfig config = config();

        generator.generateModels(inspector, config);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> generator.generateModels(inspector, config));

        assertTrue(failure.getMessage().contains("Conflicting Rune type registration"));
    }

    @Test
    void wrapsRuneModelPersistenceFailuresInRuntimeException(@TempDir Path temporaryDirectory) throws IOException {
        EmfRuneGenerator generator = newGenerator();
        generator.generateModels(inspector(), config());

        Path blockingFile = temporaryDirectory.resolve("blocking-file");
        Files.writeString(blockingFile, "not a directory");

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> generator.saveRuneModels(blockingFile.resolve("generated")));

        assertTrue(failure.getMessage().contains("Fatal error writing Rune files"));
        assertNotNull(failure.getCause());
    }

    @Test
    void wrapsXmlConfigurationPersistenceFailuresInRuntimeException(@TempDir Path temporaryDirectory) throws IOException {
        EmfRuneGenerator generator = newGenerator();
        Path blockingFile = temporaryDirectory.resolve("blocking-file");
        Files.writeString(blockingFile, "not a directory");

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> generator.saveXmlConfiguration(blockingFile.resolve("config.json")));

        assertTrue(failure.getMessage().contains("Fatal error writing JSON Configuration"));
        assertNotNull(failure.getCause());
    }

    private EmfRuneGenerator newGenerator() {
        RosettaStandaloneSetup setup = new RosettaStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        IResourceFactory rosettaFactory = injector.getInstance(IResourceFactory.class);
        IResourceServiceProvider serviceProvider = injector.getInstance(IResourceServiceProvider.class);

        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("rune", rosettaFactory);
        IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().put("rune", serviceProvider);
        loadStandardLibrary(resourceSet);

        RuneModelFactory modelFactory = new RuneModelFactory(resourceSet);
        RuneBuiltinRegistry builtinRegistry = new RuneBuiltinRegistry(resourceSet);
        RuneTypeRegistry typeRegistry = new RuneTypeRegistry(builtinRegistry);
        return new EmfRuneGenerator(modelFactory, typeRegistry);
    }

    private void loadStandardLibrary(XtextResourceSet resourceSet) {
        for (String path : List.of("model/basictypes.rosetta", "model/annotations.rosetta")) {
            var url = getClass().getClassLoader().getResource(path);
            if (url != null) {
                try {
                    resourceSet.createResource(URI.createURI(url.toString())).load(null);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Unable to load test Rune library: " + path, exception);
                }
            }
        }
    }

    private XsdParserInspector inspector() {
        return new XsdParserInspector(XsdUtils.parseXsd(SIMPLE_XSD));
    }

    private RuneGeneratorConfig config() {
        return new RuneGeneratorConfig()
                .addTargetNamespace(TARGET_NAMESPACE)
                .addNamespaceMapping(TARGET_NAMESPACE, "com.example.simple");
    }
}
