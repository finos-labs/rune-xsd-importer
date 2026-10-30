package org.finos.rune.importer.xsd.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable options that control XSD importer output.
 *
 * <p>The model-file extension is normalized by trimming surrounding
 * whitespace, converting to lower case with {@link Locale#ROOT}, and adding
 * a leading dot when it is omitted. After normalization, only {@code .rune}
 * and {@code .rosetta} are supported.</p>
 */
public final class XsdImporterConfig {

    public static final String RUNE_MODEL_FILE_EXTENSION = ".rune";
    public static final String ROSETTA_MODEL_FILE_EXTENSION = ".rosetta";
    public static final String DEFAULT_MODEL_FILE_EXTENSION = RUNE_MODEL_FILE_EXTENSION;

    private final String modelFileExtension;

    private XsdImporterConfig(String modelFileExtension) {
        this.modelFileExtension = normalizeModelFileExtension(modelFileExtension);
    }

    /**
     * Returns a configuration builder whose default extension is {@code .rune}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a default configuration using {@code .rune} model files.
     */
    public static XsdImporterConfig defaults() {
        return builder().build();
    }

    /**
     * Normalizes and validates a model-file extension using the public
     * configuration rule.
     *
     * @param extension extension with or without a leading dot
     * @return canonical lower-case extension including its leading dot
     * @throws IllegalArgumentException when the value is null, blank, or not
     *                                  one of the supported extensions
     */
    public static String normalizeModelFileExtension(String extension) {
        if (extension == null) {
            throw new IllegalArgumentException("Model-file extension must not be null");
        }

        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Model-file extension must not be blank");
        }
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }

        if (!RUNE_MODEL_FILE_EXTENSION.equals(normalized)
                && !ROSETTA_MODEL_FILE_EXTENSION.equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported model-file extension '" + extension + "'; supported extensions are "
                            + RUNE_MODEL_FILE_EXTENSION + " and " + ROSETTA_MODEL_FILE_EXTENSION);
        }
        return normalized;
    }

    public String getModelFileExtension() {
        return modelFileExtension;
    }

    /**
     * Fluent immutable-configuration builder.
     */
    public static final class Builder {

        private String modelFileExtension = DEFAULT_MODEL_FILE_EXTENSION;

        private Builder() {
        }

        public Builder modelFileExtension(String extension) {
            this.modelFileExtension = normalizeModelFileExtension(extension);
            return this;
        }

        public XsdImporterConfig build() {
            return new XsdImporterConfig(Objects.requireNonNull(modelFileExtension));
        }
    }
}
