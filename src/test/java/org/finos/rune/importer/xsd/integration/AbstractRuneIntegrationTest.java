package org.finos.rune.importer.xsd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.inject.Injector;
import com.regnosys.rosetta.RosettaStandaloneSetup;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapperCreator;
import com.regnosys.rosetta.tests.testmodel.JavaTestModel;
import com.regnosys.rosetta.tests.testmodel.RosettaTestModelService;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.finos.rune.importer.xsd.RuneTestModelUtils;
import org.finos.rune.importer.xsd.XsdValidationUtils;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.finos.rune.importer.xsd.api.XsdImporterDefaultSetup;
import org.finos.rune.importer.xsd.builder.serialization.XsdRuneModule;
import org.finos.rune.importer.xsd.core.EmfRuneGenerator;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.*;

import javax.xml.validation.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unified abstract base class for Rune importer integration testing.
 * Provides modular infrastructure bootstrapping for EMF Rune Generation (Phase A)
 * and Dynamic Java Compilation (Phase B).
 */
public abstract class AbstractRuneIntegrationTest {

    private static final XsdParserJaxpFactory DEFAULT_XSD_PARSER_FACTORY =
            XsdParserJaxpFactory.defaultFactory();
    private static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected Injector injector;
    protected EmfRuneGenerator generator;
    protected RosettaTestModelService modelService;

    protected Path temporaryDirectory;

    @BeforeAll
    void createTemporaryDirectory() throws IOException {
        Files.createDirectories(Path.of("target"));
        temporaryDirectory = Files.createTempDirectory(Path.of("target"), "rune-integration-");
    }

    @AfterAll
    void cleanupTemporaryDirectory() throws IOException {
        if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) {
            return;
        }
        try (var paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Unable to clean integration test directory: " + path, exception);
                }
            });
        }
    }

    private Schema sourceSchema;
    private ScenarioState scenarioState;
    private Throwable scenarioStateFailure;

    protected abstract Path generateScenarioModels() throws IOException;

    protected abstract void assertGeneratedModelContract(Path generatedOutput) throws IOException;

    /**
     * Bootstraps the complete Rune importer integration infrastructure.
     * Hooks into the JUnit lifecycle to ensure all components are available.
     */
    @BeforeEach
    public void setupIntegrationInfrastructure() {
        if (integrationInfrastructureInitialized) {
            return;
        }
        log.info("Bootstrapping unified Rune importer integration infrastructure.");
        setupGuiceContext();
        setupGenerationInfrastructure();
        setupBuilderInfrastructure();
        integrationInfrastructureInitialized = true;
        log.debug("Rune importer integration infrastructure successfully initialized.");
    }

    private boolean integrationInfrastructureInitialized;

    /**
     * Initializes the core Guice dependency injection context.
     * This is required as the foundation for both Phase A and Phase B.
     */
    private void setupGuiceContext() {
        log.trace("Initializing RosettaStandaloneSetup and Guice Injector.");
        RosettaStandaloneSetup setup = new RosettaStandaloneSetup();
        this.injector = setup.createInjectorAndDoEMFRegistration();
    }

    /**
     * Phase A Infrastructure: Bootstraps the EMF Resource Sets, standard library built-ins,
     * and the dependency chain required to instantiate the EmfRuneGenerator.
     */
    private void setupGenerationInfrastructure() {
        log.trace("Bootstrapping Generation Infrastructure (Phase A).");
        this.generator = XsdImporterDefaultSetup.setupInfrastructure();
        log.debug("EmfRuneGenerator successfully bound to test context.");
    }

    /**
     * Phase B Infrastructure: Bootstraps the dynamic compiler services required
     * to translate in-memory Rune DSL strings into active Java ClassLoaders.
     */
    private void setupBuilderInfrastructure() {
        log.trace("Bootstrapping Builder Infrastructure (Phase B).");
        if (this.injector == null) {
            throw new IllegalStateException("Guice Injector must be initialized before Phase B setup.");
        }

        this.modelService = injector.getInstance(RosettaTestModelService.class);
        log.debug("RosettaTestModelService successfully bound to test context.");
    }

    /**
     * Generates Rune sources and XML serialization configuration for one integration scenario.
     *
     * @param xsdPath source XSD path
     * @param outputDirectory isolated scenario output directory
     * @param config namespace and generation configuration
     * @return the generated output directory
     */
    protected Path generateRuneModels(Path xsdPath, Path outputDirectory, RuneGeneratorConfig config) throws IOException {
        return generateRuneModels(xsdPath, outputDirectory, config, XsdImporterConfig.defaults());
    }

    protected Path generateRuneModels(
            Path xsdPath,
            Path outputDirectory,
            RuneGeneratorConfig config,
            XsdImporterConfig importerConfig) throws IOException {
        Files.createDirectories(outputDirectory);

        XsdParser parser = DEFAULT_XSD_PARSER_FACTORY.createParser(xsdPath);
        XsdParserInspector inspector = new XsdParserInspector(parser);
        generator.generateModels(inspector, config, importerConfig);
        generator.saveRuneModels(outputDirectory);
        generator.saveXmlConfiguration(outputDirectory.resolve("config/xml-config.json"));
        return outputDirectory;
    }

    /**
     * Compiles and caches the source schema used by the current integration scenario.
     */
    protected final Schema getSourceSchema(Path xsdPath) throws IOException, org.xml.sax.SAXException {
        if (sourceSchema == null) {
            sourceSchema = XsdValidationUtils.compileSchema(xsdPath);
        }
        return sourceSchema;
    }

    /**
     * Generates, verifies, and compiles the current scenario once for the test class.
     * The cached failure prevents later round-trip assertions from running after a
     * generation, contract, or compilation failure.
     */
    protected final ScenarioState getScenarioState() throws Exception {
        if (scenarioStateFailure != null) {
            rethrowScenarioFailure(scenarioStateFailure);
        }
        if (scenarioState == null) {
            try {
                Path generatedOutput = generateScenarioModels();
                assertGeneratedModelContract(generatedOutput);
                JavaTestModel compiledModel = compileGeneratedRuneModel(generatedOutput);
                scenarioState = new ScenarioState(generatedOutput, compiledModel);
            } catch (Exception | AssertionError failure) {
                scenarioStateFailure = failure;
                rethrowScenarioFailure(failure);
            }
        }
        return scenarioState;
    }

    private void rethrowScenarioFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Unable to prepare the integration scenario", failure);
    }

    protected record ScenarioState(Path generatedOutput, JavaTestModel compiledModel) {
    }

    /**
     * Compiles all generated Rune sources for an integration scenario into a dynamic Java model.
     */
    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput) throws IOException {
        List<String> runeSources = RuneTestModelUtils.readRuneSources(generatedOutput);
        if (runeSources.isEmpty()) {
            throw new IllegalArgumentException("Expected generated Rune sources in " + generatedOutput);
        }
        return compileRuneSources(runeSources);
    }

    protected JavaTestModel compileGeneratedRuneModel(Path generatedOutput, String modelFileExtension)
            throws IOException {
        List<String> runeSources = RuneTestModelUtils.readRuneSources(generatedOutput, null, modelFileExtension);
        if (runeSources.isEmpty()) {
            throw new IllegalArgumentException("Expected generated Rune sources in " + generatedOutput);
        }
        return compileRuneSources(runeSources);
    }

    /**
     * Asserts that every deliberately invalid XML sample is rejected by the source schema.
     */
    protected void assertSamplesRejectedAgainstSchema(Schema schema, List<Path> invalidSampleFiles) {
        for (Path invalidSampleFile : invalidSampleFiles) {
            assertThrows(org.xml.sax.SAXException.class,
                    () -> XsdValidationUtils.validate(schema, invalidSampleFile),
                    "Expected schema rejection for " + invalidSampleFile);
        }
    }

    /**
     * Compiles raw Rune DSL strings into a dynamic Java Test Model.
     *
     * @param runeSources A list of raw Rune DSL source code strings.
     * @return The compiled JavaTestModel.
     */
    protected JavaTestModel compileRuneSources(List<String> runeSources) {
        if (runeSources == null || runeSources.isEmpty()) {
            throw new IllegalArgumentException("Rune sources cannot be empty.");
        }

        log.debug("Compiling {} Rune source files into dynamic Java bytecode.", runeSources.size());

        String primarySource = runeSources.getFirst();
        String[] additionalSources = runeSources.stream().skip(1).toArray(String[]::new);

        JavaTestModel uncompiledModel = modelService.toJavaTestModel(primarySource, additionalSources);

        // Intercept and sanitize the internal Java source map before compilation
        removePackageInfoSources(uncompiledModel);

        return uncompiledModel.compile();
    }

    /**
     * package-info.java is generated if a namespace contains a version or documentation.
     * The InMemoryJavacCompiler crashes when trying to load these as standard classes.
     * This method uses reflection to intercept the generated Java source code and delete
     * package-info files before bytecode compilation occurs.
     */
    @SuppressWarnings("unchecked")
    private void removePackageInfoSources(JavaTestModel uncompiledModel) {
        try {
            // Access the private map in JavaTestModel
            Field sourceCodeField = JavaTestModel.class.getDeclaredField("javaSourceCode");
            sourceCodeField.setAccessible(true); // Unlock private access

            Map<String, String> javaSourceCode = (Map<String, String>) sourceCodeField.get(uncompiledModel);

            // Safely remove any entry where the class name ends with "package-info"
            javaSourceCode.keySet().removeIf(className -> className.endsWith("package-info"));

            log.debug("Successfully intercepted JavaTestModel and stripped package-info classes.");

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to intercept and modify JavaTestModel source map.", e);
        }
    }

    /**
     * Initializes the Jackson Mapper with the dynamic ClassLoader securely bound to the custom module.
     *
     * @param xmlConfigPath Path to the generated xml-config.json.
     * @param dynamicClassLoader The ClassLoader bound to the dynamically compiled POJOs.
     * @return Fully configured ObjectMapper.
     */
    protected ObjectMapper getXmlMapper(Path xmlConfigPath, ClassLoader dynamicClassLoader) {
        try {
            RosettaXMLConfiguration rosettaConfig;
            try (InputStream configStream = Files.newInputStream(xmlConfigPath)) {
                rosettaConfig = RosettaXMLConfiguration.load(configStream);
            }

            ObjectMapper mapper;
            try (InputStream mapperStream = Files.newInputStream(xmlConfigPath)) {
                mapper = RosettaObjectMapperCreator.forXML(mapperStream, dynamicClassLoader).create();
            }

            ((XmlMapper) mapper).configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
            mapper.setTypeFactory(mapper.getTypeFactory().withClassLoader(dynamicClassLoader));
            mapper.registerModule(new XsdRuneModule(rosettaConfig));

            return mapper;

        } catch (IOException e) {
            log.error("Failed to create XmlMapper from config: {}", xmlConfigPath, e);
            throw new UncheckedIOException("Failed to create XmlMapper from config: " + xmlConfigPath, e);
        }
    }

    /**
     * Performs a standard structural comparison of two XML strings using XMLUnit.
     *
     * @param originalXml The expected baseline XML.
     * @param generatedXml The actual serialized XML.
     */
    protected void assertXmlEquals(String originalXml, String generatedXml) {
        log.debug("Executing strict XMLUnit structural comparison.");

        DifferenceEvaluator customEvaluator = DifferenceEvaluators.chain(
                DifferenceEvaluators.Default,
                (comparison, outcome) -> {
                    if (comparison.getType() == ComparisonType.CHILD_NODELIST_SEQUENCE) {
                        return ComparisonResult.EQUAL;
                    }
                    if (comparison.getType() == ComparisonType.NAMESPACE_PREFIX) {
                        return ComparisonResult.EQUAL;
                    }
                    if (comparison.getType() == ComparisonType.ATTR_NAME_LOOKUP
                            && (isXsiTypeAttribute(comparison.getControlDetails().getTarget())
                            || isXsiTypeAttribute(comparison.getTestDetails().getTarget()))) {
                        return ComparisonResult.EQUAL;
                    }
                    return outcome;
                }
        );

        Diff xmlDiff = DiffBuilder.compare(originalXml)
                .withTest(generatedXml)
                .ignoreWhitespace()
                .ignoreComments()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName))
                .withDifferenceEvaluator(customEvaluator)
                .withAttributeFilter(attr -> !XMLNS_NS.equals(attr.getNamespaceURI())
                        && !XSI_NS.equals(attr.getNamespaceURI())
                        && !isXsiTypeAttribute(attr))
                .checkForSimilar()
                .build();

        if (xmlDiff.hasDifferences()) {
            throw new AssertionError("Round-trip XML does not match original. Differences:\n" + xmlDiff.toString());
        }
    }

    private boolean isXsiTypeAttribute(org.w3c.dom.Node node) {
        if (node == null || !"type".equals(node.getLocalName())) {
            return false;
        }
        String nodeName = node.getNodeName();
        return XSI_NS.equals(node.getNamespaceURI())
                || "type".equals(nodeName)
                || (nodeName != null && nodeName.endsWith(":type"));
    }

}
