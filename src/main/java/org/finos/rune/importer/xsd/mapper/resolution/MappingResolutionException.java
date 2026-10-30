package org.finos.rune.importer.xsd.mapper.resolution;

/**
 * Signals that a required XSD reference could not be resolved safely.
 *
 * <p>This is deliberately an unchecked exception because the public generator
 * contract has no partial-result type. A failed strict generation must not be
 * reported as a successful Rune model generation.</p>
 */
public final class MappingResolutionException extends IllegalStateException {

    private final String reference;
    private final String context;

    public MappingResolutionException(String message, String reference, String context) {
        super(message);
        this.reference = reference;
        this.context = context;
    }

    public static MappingResolutionException unresolvedType(String typeQName, String context) {
        return new MappingResolutionException(
                "Unable to resolve required XSD type '" + typeQName + "' for " + context,
                typeQName,
                context);
    }

    public static MappingResolutionException invalidReference(String reference, String context) {
        return new MappingResolutionException(
                "Required XSD reference must be a qualified name: '" + reference + "' for " + context,
                reference,
                context);
    }

    public static MappingResolutionException wrongTargetType(
            String reference,
            String context,
            String expectedType) {
        return new MappingResolutionException(
                "Resolved XSD reference '" + reference + "' for " + context
                        + " does not resolve to the expected type '" + expectedType + "'",
                reference,
                context);
    }

    public String reference() {
        return reference;
    }

    public String context() {
        return context;
    }
}
