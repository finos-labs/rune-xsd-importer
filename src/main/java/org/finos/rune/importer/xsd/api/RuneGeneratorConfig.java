package org.finos.rune.importer.xsd.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for the Generator Pipeline.
 * <p>
 * Controls:
 * 1. Scope: Which namespaces generate files vs. which are just references.
 * 2. Mapping: How XSD URIs map to Rune (dot-notation) packages.
 * <p>
 * Values are stored using ordinary {@link Set} and {@link Map} semantics. This
 * class does not validate, trim, or normalize namespace or package values, so
 * {@code null}, empty, and whitespace-only strings are currently accepted.
 * Callers should nevertheless provide meaningful namespace URIs and Rune
 * package names. Re-adding the same target namespace is idempotent. Adding a
 * mapping for an existing namespace replaces the previous value, so mapping
 * updates are last-write-wins, including when the replacement value is
 * {@code null}.
 */
public class RuneGeneratorConfig {

    private static final String RUNE_UNKNOWN = "org.finos.rune.unknown";

    private final Set<String> targetNamespaces = new HashSet<>();

    private final Map<String, String> namespaceMappings = new HashMap<>();

    /**
     * Adds an XSD namespace to the set for which Rune files should be generated.
     *
     * <p>The value is stored without validation or normalization. In particular,
     * {@code null}, empty, and whitespace-only values are accepted and compared
     * exactly by {@link #shouldGenerate(String)}.
     *
     * @param ns the XSD namespace to generate, stored as supplied
     * @return this configuration
     */
    public RuneGeneratorConfig addTargetNamespace(String ns) {
        this.targetNamespaces.add(ns);
        return this;
    }

    /**
     * Associates an XSD namespace with a Rune package.
     *
     * <p>The key and value are stored without validation or normalization.
     * Existing mappings for the same key are replaced by the later invocation,
     * including mappings whose value is {@code null} (last-write-wins).
     *
     * @param xsdNs the XSD namespace key
     * @param runePackage the Rune package value
     * @return this configuration
     */
    public RuneGeneratorConfig addNamespaceMapping(String xsdNs, String runePackage) {
        this.namespaceMappings.put(xsdNs, runePackage);
        return this;
    }

    /**
     * Determines whether DSL code should be generated for this namespace.
     * Namespace comparison is exact; no trimming or normalization is applied.
     *
     * @param xsdNs the XSD namespace to test
     * @return {@code true} when the exact value was added as a target namespace
     */
    public boolean shouldGenerate(String xsdNs) {
        return targetNamespaces.contains(xsdNs);
    }

    /**
     * Gets the Rune package for an XSD namespace (target or external).
     *
     * <p>If a mapping exists, its value is returned as stored, including a
     * {@code null} value. If no mapping exists, this method returns the
     * compatibility fallback {@code org.finos.rune.unknown}. The fallback does
     * not validate that a target namespace was configured correctly; generated
     * target namespaces should be mapped explicitly by callers.
     *
     * @param xsdNs the XSD namespace key
     * @return the mapped Rune package, or {@code org.finos.rune.unknown} when
     *         the namespace is not mapped
     */
    public String getRunePackage(String xsdNs) {
        return namespaceMappings.getOrDefault(xsdNs, RUNE_UNKNOWN);
    }
}
