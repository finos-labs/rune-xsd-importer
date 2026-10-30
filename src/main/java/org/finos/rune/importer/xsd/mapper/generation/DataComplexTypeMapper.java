package org.finos.rune.importer.xsd.mapper.generation;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.QName;

/**
 * Maps complete Data complex types, including base-first inheritance and
 * target attributes.
 *
 * <p>The recursion guard is supplied by the current population operation and
 * attribute collision state belongs to the mapping session, so mapper
 * instances do not retain state between generations.</p>
 */
public final class DataComplexTypeMapper {

    private static final Logger log = LoggerFactory.getLogger(DataComplexTypeMapper.class);

    private final DataContentMapper contentMapper;
    private final DataAttributeMapper attributeMapper;

    public DataComplexTypeMapper(DataContentMapper contentMapper, DataAttributeMapper attributeMapper) {
        this.contentMapper = contentMapper;
        this.attributeMapper = attributeMapper;
    }

    public void populateComplexTypes(
            List<String> names,
            MappingContext context) {
        Set<RegistryKey> populatedComplexTypes = new HashSet<>();
        names.forEach(name -> populateComplexType(name, context, populatedComplexTypes));
    }

    private void populateComplexType(
            String name,
            MappingContext context,
            Set<RegistryKey> populatedComplexTypes) {
        String namespace = context.namespace();
        XsdParserInspector inspector = context.inspector();
        RuneTypeRegistry registry = context.registry();
        RegistryKey key = RegistryKey.of(namespace, name);
        if (!populatedComplexTypes.add(key)) {
            return;
        }

        QName typeName = new QName(namespace, name);
        RegistryKey baseKey = inspector.getComplexTypeBaseKey(typeName);
        Data baseData = null;
        if (baseKey != null && namespace.equals(baseKey.namespace())) {
            baseData = context.typeResolver().resolveRequiredData(
                        baseKey,
                        "base type of complex type '" + name + "'");
            populateComplexType(baseKey.localName(), context, populatedComplexTypes);
        }

        RosettaType type = registry.get(namespace, name);
        if (type instanceof Data data) {
            log.trace("Populating global complexType '{{}}{}'", namespace, name);
            List<InspectorNode> members = inspector.getComplexTypeStructuredMembers(typeName);
            List<InspectorAttributeNode> xmlAttributes = inspector.getComplexTypeAttributeMembers(typeName);

            contentMapper.map(data, members, context);
            if (baseKey != null && baseData == null) {
                baseData = context.typeResolver().resolveRequiredData(
                        baseKey,
                        "base type of complex type '" + name + "'");
            }
            mapAttributesAndSuperType(data, xmlAttributes, baseData, context);
            context.xmlConfigurationMapper().registerData(
                    data,
                    name,
                    members,
                    xmlAttributes,
                    false,
                    false,
                    context);
        }
    }

    public void mapElementAttributesAndSuperType(
            Data data,
            String elementName,
            List<InspectorAttributeNode> xmlAttributes,
            MappingContext context) {
        RegistryKey superTypeKey = context.inspector().getElementTypeKey(
                new QName(context.namespace(), elementName));
        Data superData = superTypeKey == null
                ? null
                : context.typeResolver().resolveRequiredData(
                        superTypeKey,
                        "supertype of '" + elementName + "'");
        mapAttributesAndSuperType(data, xmlAttributes, superData, context);
    }

    private void mapAttributesAndSuperType(
            Data data,
            List<InspectorAttributeNode> xmlAttributes,
            Data superData,
            MappingContext context) {
        if (superData != null && superData != data) {
            data.setSuperType(superData);
            log.trace("  -> Extends: {}", superData.getName());
        } else if (superData == data) {
            log.trace("  -> Skipped self-extension for merged type '{}'", data.getName());
        }
        for (InspectorAttributeNode member : xmlAttributes) {
            attributeMapper.map(data, member.name(), member.typeQName(), member.min(), member.max(), member.documentation(), context, member.elementRefUri());
        }
    }
}
