package org.finos.rune.importer.xsd.mapper.context;

import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.mapping.GlobalElementBinding;
import org.finos.rune.importer.xsd.mapper.mapping.SimpleTypeBinding;
import org.finos.rune.importer.xsd.mapper.mapping.TypeNameIndex;
import org.finos.rune.importer.xsd.mapper.serialization.XmlConfigurationIndex;
import org.finos.rune.importer.xsd.registry.RegistryKey;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * State shared by the target-oriented mappers for one generation scope.
 *
 * <p>It owns element and simple-type routes, generated names, attribute source
 * names, and XML-configuration indexes for the generation scope.</p>
 */
public final class MappingSession {

    private final Map<RegistryKey, GlobalElementBinding> globalElementBindings = new HashMap<>();
    private final Map<RegistryKey, SimpleTypeBinding> simpleTypeBindings = new HashMap<>();
    private final TypeNameIndex typeNameIndex = new TypeNameIndex();
    private final Map<Data, Map<String, String>> sourceAttributeNames = new IdentityHashMap<>();
    private final Map<XsdParserInspector, XmlConfigurationIndex> xmlConfigurationIndexes = new IdentityHashMap<>();

    public XmlConfigurationIndex xmlConfigurationIndex(XsdParserInspector inspector) {
        return xmlConfigurationIndexes.computeIfAbsent(inspector, XmlConfigurationIndex::new);
    }

    public void recordGlobalElementBinding(RegistryKey key, GlobalElementBinding binding) {
        GlobalElementBinding previous = globalElementBindings.putIfAbsent(
                Objects.requireNonNull(key, "global element binding key must not be null"),
                Objects.requireNonNull(binding, "global element binding must not be null"));
        if (previous != null && !previous.equals(binding)) {
            throw new IllegalStateException("Conflicting global element bindings for source key: " + key);
        }
    }

    public GlobalElementBinding globalElementBinding(RegistryKey key) {
        RegistryKey requiredKey = Objects.requireNonNull(key, "global element binding key must not be null");
        GlobalElementBinding binding = globalElementBindings.get(requiredKey);
        if (binding == null) {
            throw new IllegalStateException("No global element binding registered for source key: " + requiredKey);
        }
        return binding;
    }

    public SimpleTypeBinding simpleTypeBinding(RegistryKey key, XsdParserInspector inspector) {
        return simpleTypeBindings.computeIfAbsent(
                Objects.requireNonNull(key, "simple type binding key must not be null"),
                ignored -> SimpleTypeBinding.resolve(
                        Objects.requireNonNull(inspector, "inspector must not be null"), key));
    }

    public TypeNameIndex typeNameIndex() {
        return typeNameIndex;
    }

    public String sourceAttributeName(Data data, String runeName) {
        Map<String, String> names = sourceAttributeNames.get(
                Objects.requireNonNull(data, "data must not be null"));
        return names == null
                ? null
                : names.get(Objects.requireNonNull(runeName, "Rune attribute name must not be null"));
    }

    public void recordSourceAttributeName(Data data, String runeName, String sourceName) {
        sourceAttributeNames
                .computeIfAbsent(Objects.requireNonNull(data, "data must not be null"), ignored -> new HashMap<>())
                .put(
                        Objects.requireNonNull(runeName, "Rune attribute name must not be null"),
                        Objects.requireNonNull(sourceName, "source attribute name must not be null"));
    }
}
