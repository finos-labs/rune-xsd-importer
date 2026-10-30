package org.finos.rune.importer.xsd.mapper;

import com.regnosys.rosetta.rosetta.RosettaEnumValue;
import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaType;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.mapping.EnumMapping;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierNamingUtils;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierValidator;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import javax.xml.namespace.QName;

public class EnumMapper extends AbstractRuneTypeMapper<RosettaEnumeration> {

    private static final Logger log = LoggerFactory.getLogger(EnumMapper.class);

    public EnumMapper(MappingSession session) {
        super(session);
    }

    @Override
    public List<RosettaEnumeration> register(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 1: Registering Enums for namespace '{}'", namespace);
        MappingContext context = mappingContext(inspector, namespace, registry);
        List<RosettaEnumeration> createdEnums = new ArrayList<>();

        inspector.getAllGlobalSimpleTypes(namespace).forEach(xsdName -> {
            if (context.simpleTypeBinding(xsdName).isEnumeration()) {
                registerShell(
                        runeFactory::createRosettaEnumeration,
                        xsdName,
                        "Generated from XSD SimpleType",
                        context,
                        createdEnums);
            }
        });

        log.debug("Registered {} Enums.", createdEnums.size());
        return createdEnums;
    }

    @Override
    public void populate(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 2: Populating Enums for namespace '{}'", namespace);
        MappingContext context = mappingContext(inspector, namespace, registry);

        inspector.getAllGlobalSimpleTypes(namespace).forEach(xsdName -> {
            if (context.simpleTypeBinding(xsdName).isEnumeration()) {
                RosettaType type = registry.get(namespace, xsdName);
                if (type instanceof RosettaEnumeration runeEnum) {
                    EnumMapping mapping = mappingFor(
                            new QName(namespace, xsdName),
                            context.simpleTypeBinding(xsdName).isEnumUnion(),
                            inspector);
                    populateValues(runeEnum, xsdName, mapping);
                    context.xmlConfigurationMapper().registerEnumeration(runeEnum, xsdName, mapping, context);
                }
            }
        });
    }

    private EnumMapping mappingFor(QName xsdName, boolean enumUnion, XsdParserInspector inspector) {
        List<String> values = enumUnion
                ? inspector.getUnionEnumValues(xsdName)
                : inspector.getSimpleTypeEnumValues(xsdName);
        return new EnumMapping(xsdName.getLocalPart(), mapValues(values));
    }

    private void populateValues(
            RosettaEnumeration runeEnum,
            String xsdName,
            EnumMapping mapping) {
        log.debug("Populating Enum: {}", runeEnum.getName());

        for (EnumMapping.Value valueMapping : mapping.values()) {
            RosettaEnumValue enumValue = runeFactory.createRosettaEnumValue();
            enumValue.setName(valueMapping.runeLabel());
            enumValue.setDefinition("Generated from XSD SimpleType: " + xsdName);

            if (!valueMapping.runeLabel().equals(valueMapping.xmlValue())) {
                enumValue.setDisplay(valueMapping.xmlValue());
                log.trace("  -> Mapping divergence: '{}' (XML) -> '{}' (Rune)",
                        valueMapping.xmlValue(), valueMapping.runeLabel());
            }

            runeEnum.getEnumValues().add(enumValue);
        }
    }

    private List<EnumMapping.Value> mapValues(List<String> xmlValues) {
        List<EnumMapping.Value> mappings = new ArrayList<>();
        Set<String> registeredLabels = new HashSet<>();

        for (String xmlValue : xmlValues) {
            String baseLabel = RuneIdentifierNamingUtils.toEnumLabel(xmlValue);
            RuneIdentifierValidator.requireValid(baseLabel, xmlValue, "enum label");
            String finalLabel = baseLabel;

            if (registeredLabels.contains(baseLabel)) {
                String fallback = "_" + xmlValue.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
                finalLabel = uniqueLabel(fallback, registeredLabels);
                log.warn("Enum label collision detected for value '{}'. Applying fallback label '{}'.",
                        xmlValue, finalLabel);
            }

            registeredLabels.add(finalLabel);
            mappings.add(new EnumMapping.Value(finalLabel, xmlValue));
        }

        return List.copyOf(mappings);
    }

    private String uniqueLabel(String candidate, Set<String> registeredLabels) {
        String unique = candidate;
        int suffix = 2;
        while (registeredLabels.contains(unique)) {
            unique = candidate + "_" + suffix++;
        }
        return unique;
    }
}
