package org.finos.rune.importer.xsd.mapper.resolution;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import javax.xml.namespace.QName;

/**
 * Resolves XSD references through the existing Rune type registry.
 *
 * <p>The resolver retains the current native-wrapper preference while keeping
 * registry lookups out of mapper algorithms. Required references use the
 * strict methods and never recover through an unrelated fallback type.</p>
 */
public final class TypeReferenceResolver {

    private static final Logger log = LoggerFactory.getLogger(TypeReferenceResolver.class);
    private final XsdParserInspector inspector;
    private final RuneTypeRegistry registry;

    public TypeReferenceResolver(
            XsdParserInspector inspector,
            RuneTypeRegistry registry) {
        this.inspector = Objects.requireNonNull(inspector, "inspector must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    public RosettaType resolve(RegistryKey key) {
        return key == null ? null : registry.resolve(key);
    }

    public RosettaType resolveRequired(RegistryKey key, String context) {
        if (key == null) {
            throw MappingResolutionException.invalidReference(null, context);
        }

        RosettaType type = resolve(key);
        if (type == null) {
            throw MappingResolutionException.unresolvedType(key.toString(), context);
        }
        return type;
    }

    public Data resolveRequiredData(RegistryKey key, String context) {
        RosettaType type = resolveRequired(key, context);
        if (type instanceof Data data) {
            return data;
        }
        throw MappingResolutionException.wrongTargetType(key.toString(), context, "Rune Data");
    }

    public RosettaType resolveElementReference(Optional<RegistryKey> elementRefKey, RegistryKey typeKey) {
        if (elementRefKey != null && elementRefKey.isPresent()) {
            RegistryKey key = elementRefKey.get();
            if (inspector.isNativeWrapperElement(new QName(key.namespace(), key.localName()))) {
                RosettaType type = resolve(key);
                if (type != null) {
                    log.trace("Smart Reference: Preserved Native Wrapper elementRef '{}' as '{}' instead of primitive '{}'", key, type.getName(), typeKey);
                    return type;
                }
            }
        }
        return resolve(typeKey);
    }

    public RosettaType resolveAttributeType(
            Optional<RegistryKey> elementRefKey,
            RegistryKey typeKey,
            String attributeName) {
        RosettaType type = resolveElementReference(elementRefKey, typeKey);
        if (type != null) {
            return type;
        }

        throw MappingResolutionException.unresolvedType(
                typeKey == null ? null : typeKey.toString(),
                "attribute '" + attributeName + "'");
    }
}
