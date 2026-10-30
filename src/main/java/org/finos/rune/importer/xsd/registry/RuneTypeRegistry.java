package org.finos.rune.importer.xsd.registry;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Annotation;
import com.rosetta.model.lib.ModelSymbolId;
import com.rosetta.util.serialisation.TypeXMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RuneTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuneTypeRegistry.class);

    private final Map<RegistryKey, RosettaType> userTypeCache = new HashMap<>();

    private final Map<ModelSymbolId, TypeXMLConfiguration> xmlConfigs = new LinkedHashMap<>();

    private final RuneBuiltinRegistry builtins;

    public RuneTypeRegistry(RuneBuiltinRegistry builtins) {
        this.builtins = Objects.requireNonNull(builtins, "builtins must not be null");
    }

    public void register(RegistryKey key, RosettaType type) {
        log.debug("Registering RosettaType with key '{}'", key);
        RosettaType previous = userTypeCache.putIfAbsent(key, type);
        if (previous != null && previous != type) {
            throw new IllegalStateException(
                    "Conflicting Rune type registration for source identity '" + key
                            + "'; an existing type cannot be replaced by '" + type.getName() + "'");
        }
    }

    public RosettaType resolve(RegistryKey key) {
        if (key == null) return null;

        if (userTypeCache.containsKey(key)) {
            return userTypeCache.get(key);
        }

        return builtins.get(key);
    }

    /**
     * Resolves a Standard Library annotation by name (e.g., "rootType").
     */
    public Annotation resolveAnnotation(String name) {
        return builtins.getAnnotation(name);
    }

    public RosettaType get(String namespace, String localName) {
        RegistryKey key = RegistryKey.of(namespace, localName);
        return resolve(key);
    }

    public void registerConfig(ModelSymbolId id, TypeXMLConfiguration config) {
        TypeXMLConfiguration previous = xmlConfigs.putIfAbsent(id, config);
        if (previous != null && !Objects.equals(previous, config)) {
            xmlConfigs.put(id, mergeXmlConfigurations(id, previous, config));
        }
    }

    private TypeXMLConfiguration mergeXmlConfigurations( ModelSymbolId id, TypeXMLConfiguration first, TypeXMLConfiguration second) {
        boolean firstHasExplicitElement = first.getXmlElementName().isPresent();
        boolean secondHasExplicitElement = second.getXmlElementName().isPresent();
        Optional<String> xmlElementFullyQualifiedName = firstHasExplicitElement == secondHasExplicitElement
                ? mergeOptional(id, "xmlElementFullyQualifiedName", first.getXmlElementFullyQualifiedName(), second.getXmlElementFullyQualifiedName())
                : ( firstHasExplicitElement
                ? first.getXmlElementFullyQualifiedName()
                : second.getXmlElementFullyQualifiedName());

        return new TypeXMLConfiguration(
                mergeOptional(id, "substitutionGroup", first.getSubstitutionGroup(), second.getSubstitutionGroup()),
                mergeOptional(id, "xmlElementName", first.getXmlElementName(), second.getXmlElementName()),
                xmlElementFullyQualifiedName,
                mergeOptional(id, "abstract", first.getAbstract(), second.getAbstract()),
                mergeMapOptional(id, "xmlAttributes", first.getXmlAttributes(), second.getXmlAttributes()),
                mergeMapOptional(id, "attributes", first.getAttributes(), second.getAttributes()),
                mergeMapOptional(id, "enumValues", first.getEnumValues(), second.getEnumValues()));
    }

    private <T> Optional<T> mergeOptional(
            ModelSymbolId id,
            String field,
            Optional<T> first,
            Optional<T> second) {
        if (first.isPresent() && second.isPresent() && !Objects.equals(first.get(), second.get())) {
            throw new IllegalStateException(
                    "Conflicting XML configuration for Rune type '" + id
                            + "' in field '" + field + "'");
        }
        return first.isPresent() ? first : second;
    }

    private <K, V> Optional<Map<K, V>> mergeMapOptional(
            ModelSymbolId id,
            String field,
            Optional<Map<K, V>> first,
            Optional<Map<K, V>> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }

        Map<K, V> merged = new LinkedHashMap<>(first.get());
        second.get().forEach((key, value) -> {
            V previous = merged.putIfAbsent(key, value);
            if (previous != null && !Objects.equals(previous, value)) {
                throw new IllegalStateException(
                        "Conflicting XML configuration for Rune type '" + id
                                + "' in field '" + field + "' at key '" + key + "'");
            }
        });
        return Optional.of(merged);
    }

    public Map<ModelSymbolId, TypeXMLConfiguration> getXmlConfigs() {
        return xmlConfigs;
    }
}
