package org.finos.rune.importer.xsd.mapper.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes {@link RosettaXMLConfiguration} instances as indented JSON files.
 *
 * <p>This writer validates its inputs and the required lowercase {@code .json}
 * suffix, but it does not create missing parent directories. Callers that own
 * output-directory preparation must create those directories before invoking
 * {@link #writeConfig(RosettaXMLConfiguration, Path)}. Existing files are
 * overwritten by Jackson's standard file-writing behavior.</p>
 *
 * <p>The mapper registers Jackson modules discovered on the classpath because
 * the configuration model may contain module-supported types such as
 * {@code Optional}.</p>
 */
public class XmlConfigurationJsonWriter {

    private static final Logger log = LoggerFactory.getLogger(XmlConfigurationJsonWriter.class);
    private final ObjectMapper mapper;

    public XmlConfigurationJsonWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.findAndRegisterModules();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    /**
     * Writes the provided configuration object to disk.
     *
     * @param config the non-null XML serialization configuration to write
     * @param filePath the non-null destination path, which must end in lowercase
     *                 {@code .json}
     * @throws NullPointerException if {@code config} or {@code filePath} is null
     * @throws IllegalArgumentException if {@code filePath} does not end in
     *                                  lowercase {@code .json}
     * @throws IOException if the destination cannot be written or serialization
     *                     fails; the failure is logged with the destination path
     */
    public void writeConfig(RosettaXMLConfiguration config, Path filePath) throws IOException {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        if (!filePath.toString().endsWith(".json")) {
            throw new IllegalArgumentException("Configuration file path must end with .json: " + filePath);
        }

        try {
            log.debug("Writing XML Configuration to: {}", filePath);
            mapper.writeValue(filePath.toFile(), config);
            log.info("Wrote XML Configuration to: {}", filePath);
        } catch (IOException exception) {
            log.error("Failed to write XML Configuration to: {}", filePath, exception);
            throw new IOException("Failed to write XML Configuration to " + filePath, exception);
        }
    }
}
