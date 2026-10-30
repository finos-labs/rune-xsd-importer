package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.RosettaCardinality;
import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.TypeCall;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierNamingUtils;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierValidator;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Maps source attribute information to Rune attributes on a Data target.
 *
 * <p>This mapper preserves the existing target-oriented behavior: source
 * inspector attributes are resolved through the shared mapping context, native
 * wrapper references keep their preferred type, unresolved required types fail
 * generation, and inherited attributes are not flattened into a subtype.</p>
 */
public final class DataAttributeMapper {

    private static final Logger log = LoggerFactory.getLogger(DataAttributeMapper.class);

    private final SimpleFactory simpleFactory = SimpleFactory.eINSTANCE;
    private final RosettaFactory runeFactory = RosettaFactory.eINSTANCE;
    public Attribute map(
            Data data,
            String name,
            String typeQName,
            int min,
            int max,
            String doc,
            MappingContext context,
            Optional<String> elementRefUri) {
        String runeName = RuneIdentifierNamingUtils.toAttributeCase(name);
        RuneIdentifierValidator.requireValid(runeName, name, "attribute");

        String previousSourceName = context.mappingSession().sourceAttributeName(data, runeName);
        if (previousSourceName != null && !previousSourceName.equals(name)) {
            throw attributeCollision(data, previousSourceName, name, runeName);
        }

        boolean exists = data.getAttributes().stream().anyMatch(a -> a.getName().equals(runeName));
        if (exists) {
            if (previousSourceName == null && !name.equals(runeName)) {
                throw attributeCollision(data, "<existing declaration>", name, runeName);
            }
            log.trace("Skipping idempotent duplicate attribute '{}' in Data '{}'", runeName, data.getName());
            return null;
        }

        Attribute attr = simpleFactory.createAttribute();
        attr.setName(runeName);

        // Preserve native-wrapper references while resolving complex types through
        // their mapped base type so generated Java polymorphism remains intact.
        RegistryKey typeKey = RegistryKey.parse(typeQName);
        if (typeKey == null) {
            throw MappingResolutionException.invalidReference(typeQName, "attribute '" + runeName + "'");
        }
        Optional<RegistryKey> elementRefKey = elementRefUri == null
                ? Optional.empty()
                : elementRefUri.map(RegistryKey::parse).filter(Objects::nonNull);
        RosettaType type = context.typeResolver().resolveAttributeType(elementRefKey, typeKey, runeName);

        // Do not duplicate an attribute already supplied by a mapped supertype.
        if (isAttributeInSuperType(data, runeName)) {
            if (!name.equals(runeName)) {
                throw attributeCollision(data, "<inherited declaration>", name, runeName);
            }
            log.trace("Skipping attribute '{}' in '{}' as it is inherited from superType.", runeName, data.getName());
            return null;
        }

        attr.setDefinition(doc);

        TypeCall call = runeFactory.createTypeCall();
        call.setType(type);
        attr.setTypeCall(call);

        RosettaCardinality cardinality = runeFactory.createRosettaCardinality();
        cardinality.setInf(min);
        if (max == Integer.MAX_VALUE || max == -1) {
            cardinality.setUnbounded(true);
        } else {
            cardinality.setSup(max);
        }
        attr.setCard(cardinality);

        data.getAttributes().add(attr);
        context.mappingSession().recordSourceAttributeName(data, runeName, name);
        return attr;
    }

    private IllegalStateException attributeCollision(
            Data data,
            String previousSourceName,
            String currentSourceName,
            String runeName) {
        return new IllegalStateException(
                "Conflicting Rune attribute name '" + runeName + "' in Data '" + data.getName()
                        + "': XSD declarations '" + previousSourceName + "' and '" + currentSourceName
                        + "' normalize to the same attribute identity");
    }

    /**
     * Checks if the attribute name exists in any of the superTypes of the given Data.
     */
    private boolean isAttributeInSuperType(Data data, String attributeName) {
        if (data.getSuperType() == null) return false;

        Data current = data.getSuperType();
        Set<Data> visited = new HashSet<>();

        while (current != null) {
            if (!visited.add(current)) break;

            boolean found = current.getAttributes().stream()
                    .anyMatch(a -> Objects.equals(a.getName(), attributeName));
            if (found) return true;

            current = current.getSuperType();
        }
        return false;
    }
}
