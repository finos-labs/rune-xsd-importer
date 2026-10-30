package org.finos.rune.importer.xsd.mapper.mapping;

import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierValidator;
import org.finos.rune.importer.xsd.registry.RegistryKey;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Indexes generated Rune names by qualified source identity for one session.
 */
public final class TypeNameIndex {

    private final Map<RegistryKey, String> namesBySource = new HashMap<>();
    private final Map<GeneratedNameKey, RegistryKey> sourcesByGeneratedName = new HashMap<>();

    public String register(RegistryKey sourceKey, String targetName, boolean aliasOfExisting) {
        Objects.requireNonNull(sourceKey, "source key must not be null");
        Objects.requireNonNull(targetName, "target name must not be null");
        RuneIdentifierValidator.requireValid(targetName, sourceKey.localName(), "type");

        String previous = namesBySource.putIfAbsent(sourceKey, targetName);
        if (previous != null) {
            if (!previous.equals(targetName)) {
                throw new IllegalStateException("Conflicting Rune type mappings for source key: " + sourceKey);
            }
            return previous;
        }

        if (!aliasOfExisting) {
            GeneratedNameKey generatedNameKey = generatedNameKey(sourceKey, targetName);
            RegistryKey existing = sourcesByGeneratedName.putIfAbsent(generatedNameKey, sourceKey);
            if (existing != null && !existing.equals(sourceKey)) {
                namesBySource.remove(sourceKey);
                throw new IllegalStateException(
                        "Conflicting Rune type name '" + targetName
                                + "' in generated namespace '" + sourceKey.namespace()
                                + "': XSD declarations '" + existing.localName()
                                + "' (" + existing.namespace() + ") and '"
                                + sourceKey.localName() + "' (" + sourceKey.namespace()
                                + ") normalize to the same type identity");
            }
        }
        return targetName;
    }

    public boolean hasCollision(RegistryKey sourceKey, String targetName) {
        RegistryKey existing = sourcesByGeneratedName.get(generatedNameKey(sourceKey, targetName));
        return existing != null && !existing.equals(sourceKey);
    }

    private GeneratedNameKey generatedNameKey(RegistryKey sourceKey, String targetName) {
        return new GeneratedNameKey(
                sourceKey.namespace(),
                Objects.requireNonNull(targetName, "target name must not be null").toLowerCase(Locale.ROOT));
    }

    private record GeneratedNameKey(String namespace, String normalizedTargetName) {
    }
}
