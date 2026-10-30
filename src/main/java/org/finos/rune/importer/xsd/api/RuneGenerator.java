package org.finos.rune.importer.xsd.api;

import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;

import java.nio.file.Path;

/**
 * Defines the contract for generating Rune DSL models and their associated Runtime configurations.
 * <p>
 * This interface separates the generation lifecycle into three distinct phases:
 * <ol>
 * <li><b>Model Generation:</b> Parsing source inputs (XSD) and building the in-memory Rune model.</li>
 * <li><b>Model Persistence:</b> Serializing the in-memory models to Rune DSL files (.rune).</li>
 * <li><b>Configuration:</b> Generating the mapping metadata required by the Rosetta Runtime (XML config).</li>
 * </ol>
 * <p>
 * The supported call order is to invoke {@link #generateModels(XsdParserInspector, RuneGeneratorConfig)}
 * before invoking either persistence method or {@link #generateXmlConfiguration()}. The current
 * {@code EmfRuneGenerator} retains its model and registry state for the lifetime of the instance;
 * callers should therefore treat a generator as single-use for one generation run and create a new
 * generator for a subsequent run. Calling persistence or configuration methods before generation is
 * outside the supported pipeline. The current implementation may return an empty configuration or
 * produce no model files in that state, but callers must not rely on that behavior.
 */
public interface RuneGenerator {

    /**
     * Executes the core logic to transform XSD definitions into in-memory Rune models.
     * <p>
     * This phase populates the internal registries and builds the AST (Abstract Syntax Tree).
     * It does not perform file I/O.
     *
     * @param inspector The XSD inspector providing traversable access to the source schema.
     * @param config    Configuration defining target namespaces and XSD-to-Rune package mappings.
     * @throws MappingResolutionException when a required XSD reference cannot
     *                                    be resolved safely.
     */
    void generateModels(XsdParserInspector inspector, RuneGeneratorConfig config);

    /**
     * Executes model generation with explicit importer output options.
     *
     * <p>The default implementation preserves source compatibility for custom
     * generators written against the original API. Such implementations keep
     * their historical {@code .rune} behavior until they override this
     * overload.</p>
     *
     * @param inspector the XSD inspector providing traversable source data
     * @param config namespace and package generation configuration
     * @param importerConfig immutable importer output configuration
     */
    default void generateModels(
            XsdParserInspector inspector,
            RuneGeneratorConfig config,
            XsdImporterConfig importerConfig) {
        generateModels(inspector, config);
    }

    /**
     * Persists the generated Rune models to the file system.
     * <p>
     * Files use category suffixes such as {@code namespace-type.rune} and
     * {@code namespace-enum.rune}.
     *
     * @param outputRoot The directory where .rune files will be written.
     * @throws RuntimeException if the generated resources cannot be written. The underlying I/O
     *                          failure is retained as the cause where available.
     */
    void saveRuneModels(Path outputRoot);

    /**
     * Constructs the XML Configuration object based on the generated models.
     * <p>
     * This configuration object maps the sanitized Rune names (e.g., {@code _count})
     * back to their original XML names (e.g., {@code count}).
     *
     * @return The serializable configuration object.
     */
    RosettaXMLConfiguration generateXmlConfiguration();

    /**
     * Serializes the Rosetta XML Configuration to a JSON file.
     *
     * @param filePath The exact path where the JSON file should be written.
     * @throws RuntimeException if the configuration cannot be written. The underlying I/O
     *                          failure is retained as the cause where available.
     */
    void saveXmlConfiguration(Path filePath);
}
