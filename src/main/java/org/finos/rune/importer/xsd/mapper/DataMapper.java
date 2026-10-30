package org.finos.rune.importer.xsd.mapper;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Annotation;
import com.regnosys.rosetta.rosetta.simple.AnnotationRef;
import com.regnosys.rosetta.rosetta.simple.Attribute;
import com.regnosys.rosetta.rosetta.simple.Data;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorAttributeNode;
import org.finos.rune.importer.xsd.inspector.model.InspectorNode;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.generation.*;
import org.finos.rune.importer.xsd.mapper.mapping.*;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierNamingUtils;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.namespace.QName;

public class DataMapper extends AbstractRuneTypeMapper<Data> {

    public DataMapper(MappingSession session) {
        super(session);
    }

    private static final Logger log = LoggerFactory.getLogger(DataMapper.class);

    private final DataAttributeMapper attributeMapper = new DataAttributeMapper();
    private final DataContentMapper contentMapper = new DataContentMapper(attributeMapper);
    private final DataComplexTypeMapper complexTypeMapper = new DataComplexTypeMapper(contentMapper, attributeMapper);

    @Override
    public List<Data> register(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 1: Registering Data types (ComplexTypes & Groups) for namespace '{}'", namespace);
        MappingContext context = mappingContext(inspector, namespace, registry);

        List<Data> createdData = new ArrayList<>();

        inspector.getAllGlobalComplexTypes(namespace).forEach(xsdName ->
                registerShell(
                        simpleFactory::createData,
                        xsdName,
                        "Generated from XSD ComplexType",
                        context,
                        createdData));

        inspector.getAllGlobalGroups(namespace).forEach(groupName ->
                registerShell(
                        simpleFactory::createData,
                        groupName,
                        "Generated from XSD Group",
                        context,
                        createdData));

        eligibleGlobalElementNames(inspector, namespace)
                .forEach(xsdName -> {
            RegistryKey elementKey = RegistryKey.of(namespace, xsdName);
            GlobalElementBinding binding = resolveGlobalElementBinding(xsdName, context);
            mappingSession().recordGlobalElementBinding(elementKey, binding);

            if (binding.mode() == GlobalElementBinding.Mode.VIRTUAL_ALIAS) {
                log.debug("Skipping Global Element '{}' register in DataMapper (handled as Virtual TypeAlias)", xsdName);
                return;
            }

            if (binding.mode() == GlobalElementBinding.Mode.MERGED_TYPED_ELEMENT) {
                Data existingData = (Data) registry.resolve(binding.targetKey());
                log.debug("Skipping Global Element '{}' register in DataMapper (qualified complex-type target collision with '{}')",
                        xsdName, existingData.getName());
                context.typeNameIndex().register(elementKey, existingData.getName(), true);
                context.registry().register(elementKey, existingData);
                return;
            }

            if (binding.mode() == GlobalElementBinding.Mode.NATIVE_WRAPPER) {
                registerShell(
                        simpleFactory::createData,
                        xsdName,
                        "Generated from Global Element (Native Wrapper)",
                        context,
                        createdData);
            } else {
                registerShell(
                        simpleFactory::createData,
                        xsdName,
                        "Generated from XSD Element",
                        disambiguatedGlobalElementTargetName(xsdName, context),
                        context,
                        createdData);
            }

        });

        inspector.getAllGlobalSimpleTypes(namespace).forEach(xsdName -> {
            if (context.simpleTypeBinding(xsdName).isMixedUnion()) {
                registerShell(
                        simpleFactory::createData,
                        xsdName,
                        "Generated from XSD Union (Mixed)",
                        context,
                        createdData);
            }
        });

        return createdData;
    }

    private GlobalElementBinding resolveGlobalElementBinding(
            String elementName,
            MappingContext context) {
        String namespace = context.namespace();
        XsdParserInspector inspector = context.inspector();
        RegistryKey elementKey = RegistryKey.of(namespace, elementName);
        QName elementQName = new QName(namespace, elementName);
        if (inspector.isTypeAliasWrapperElement(elementQName)) {
            return new GlobalElementBinding(GlobalElementBinding.Mode.VIRTUAL_ALIAS, elementKey);
        }

        if (inspector.isNativeWrapperElement(elementQName)) {
            return new GlobalElementBinding(GlobalElementBinding.Mode.NATIVE_WRAPPER, elementKey);
        }

        RegistryKey inlineProxyType = inspector.getInlineProxyTypeKey(elementQName);
        if (inlineProxyType != null && inspector.getElementInlineAttributeMembers(elementQName).isEmpty()) {
            return new GlobalElementBinding(GlobalElementBinding.Mode.INLINE_PROXY, inlineProxyType);
        }

        RegistryKey typeKey = inspector.getElementTypeKey(elementQName);
        if (typeKey == null || !namespace.equals(typeKey.namespace())) {
            return typeKey == null
                    ? new GlobalElementBinding(GlobalElementBinding.Mode.ANONYMOUS_INLINE, elementKey)
                    : new GlobalElementBinding(GlobalElementBinding.Mode.TYPED_PROXY, typeKey);
        }
        if (!inspector.isGlobalComplexType(typeKey)) {
            return new GlobalElementBinding(GlobalElementBinding.Mode.TYPED_PROXY, typeKey);
        }

        RosettaType referencedType = context.registry().resolve(typeKey);
        if (!(referencedType instanceof Data data)) {
            return new GlobalElementBinding(GlobalElementBinding.Mode.TYPED_PROXY, typeKey);
        }

        String elementTargetName = RuneIdentifierNamingUtils.toTypeCase(elementName);
        return RuneIdentifierNamingUtils.targetTypeNamesCollide(elementTargetName, data.getName())
                ? new GlobalElementBinding(GlobalElementBinding.Mode.MERGED_TYPED_ELEMENT, typeKey)
                : new GlobalElementBinding(GlobalElementBinding.Mode.TYPED_PROXY, typeKey);
    }

    private String disambiguatedGlobalElementTargetName(
            String xsdName,
            MappingContext context) {
        String baseName = RuneIdentifierNamingUtils.toTypeCase(xsdName);
        String candidate = baseName;
        int suffix = 1;
        while (hasGeneratedTargetCollision(candidate, xsdName, context)) {
            candidate = baseName + "Element" + suffix++;
        }
        return candidate;
    }

    private boolean hasGeneratedTargetCollision(
            String targetName,
            String xsdName,
            MappingContext context) {
        return context.typeNameIndex().hasCollision(
                RegistryKey.of(context.namespace(), xsdName), targetName);
    }

    @Override
    public void populate(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 2: Populating Data types for namespace '{}'", namespace);

        MappingContext context = mappingContext(inspector, namespace, registry);

        List<String> complexTypeNames = inspector.getAllGlobalComplexTypes(namespace);
        complexTypeMapper.populateComplexTypes(complexTypeNames, context);

        inspector.getAllGlobalGroups(namespace).forEach(name -> {
            RosettaType type = context.registry().get(namespace, name);
            log.trace("Populating global Group '{{}}{}' in RosettaType '{}' named '{}'", namespace, name, type == null ? "null" : type.getClass(), type == null ? "null" : type.getName());
            if (type instanceof Data data) {
                List<InspectorNode> members = inspector.getGroupStructuredMembers(new QName(namespace, name));
                List<InspectorAttributeNode> emptyAttributes = Collections.emptyList();

                contentMapper.map(data, members, context);
                populateXMLConfiguration(data, name, members, emptyAttributes, false, false, context);
            }
        });

        eligibleGlobalElementNames(inspector, namespace).forEach(name -> {
            GlobalElementBinding binding = mappingSession().globalElementBinding(RegistryKey.of(namespace, name));
            RosettaType type = context.registry().get(namespace, name);
            log.trace("Populating global Element '{}{}' in RosettaType '{}' named '{}'", "{"+namespace+"}", name, type == null ? "null" : type.getClass().getSimpleName(), type == null ? "null" : type.getName());

            if (binding.mode() == GlobalElementBinding.Mode.VIRTUAL_ALIAS) {
                log.debug("Skipping Global Element '{}' populate in DataMapper (handled as Virtual TypeAlias)", name);
                return;
            }
            if (type instanceof Data data) {
                populateGlobalElement(data, name, binding, context);
            }
        });

        populateMixedUnions(context);
    }

    private Stream<String> eligibleGlobalElementNames(XsdParserInspector inspector, String namespace) {
        return inspector.getAllGlobalElements(namespace).stream()
                .filter(name -> inspector.isGlobalElement(namespace, name));
    }

    private void populateGlobalElement(
            Data data,
            String name,
            GlobalElementBinding binding,
            MappingContext context) {
        addRootTypeAnnotation(data, context);

        switch (binding.mode()) {
            case NATIVE_WRAPPER -> populateNativeWrapper(data, name, context);
            case INLINE_PROXY -> populateInlineProxy(data, name, binding.targetKey(), context);
            case MERGED_TYPED_ELEMENT -> populateMergedElement(data, name, binding.targetKey(), context);
            case TYPED_PROXY -> populateTypedProxy(data, name, binding.targetKey(), context);
            case ANONYMOUS_INLINE -> populateAnonymousElement(data, name, context);
            case VIRTUAL_ALIAS -> throw new IllegalStateException("Virtual aliases must not populate as data: " + name);
        }
    }

    private void addRootTypeAnnotation(Data data, MappingContext context) {
        Annotation annotation = context.registry().resolveAnnotation("rootType");
        if (annotation == null) {
            log.warn("Could not resolve 'rootType' annotation. Ensure standard library is loaded.");
            return;
        }
        boolean alreadyPresent = data.getAnnotations().stream()
                .anyMatch(reference -> reference.getAnnotation() != null
                        && "rootType".equals(reference.getAnnotation().getName()));
        if (alreadyPresent) {
            return;
        }
        AnnotationRef reference = simpleFactory.createAnnotationRef();
        reference.setAnnotation(annotation);
        data.getAnnotations().add(reference);
        log.debug("Adding [rootType] annotation to {}", data.getName());
    }

    private void populateNativeWrapper(
            Data data,
            String name,
            MappingContext context) {
        // Link substitution children to their head
        if (!mapNativeSubstitution(data, name, context)) {
            // Only the head gets the primitive string value
            RegistryKey primitiveType = context.inspector().getElementTypeKey(
                    new QName(context.namespace(), name));
            attributeMapper.map(data, RuneIdentifierNamingUtils.toAttributeCase("value"), primitiveType.toString(), 1, 1, "Synthetic field for Native Wrapper content", context, Optional.empty());
        }
        populateXMLConfiguration(data, name, Collections.emptyList(), Collections.emptyList(), true, true, context);
    }

    private void populateInlineProxy(
            Data data,
            String name,
            RegistryKey targetKey,
            MappingContext context) {
        String resolutionContext = "inline proxy target of element '" + name + "'";
        Data targetData = context.typeResolver().resolveRequiredData(targetKey, resolutionContext);
        data.setSuperType(targetData);
        log.trace("   -> Extends (Inline Proxy): {}", targetData.getName());
        List<InspectorAttributeNode> xmlAttributes = context.inspector().getElementInlineAttributeMembers(
                new QName(context.namespace(), name));
        complexTypeMapper.mapElementAttributesAndSuperType(data, name, xmlAttributes, context);
        populateXMLConfiguration(data, name, Collections.emptyList(), xmlAttributes, true, false, context);
    }

    private void populateMergedElement(
            Data data,
            String name,
            RegistryKey targetKey,
            MappingContext context) {
        QName typeName = new QName(targetKey.namespace(), targetKey.localName());
        List<InspectorNode> members = context.inspector().getComplexTypeStructuredMembers(typeName);
        List<InspectorAttributeNode> xmlAttributes = context.inspector().getComplexTypeAttributeMembers(typeName);
        populateXMLConfiguration(data, name, members, xmlAttributes, true, false, context);
        log.trace("   -> Skipped self-extension for merged type '{}'", RuneIdentifierNamingUtils.toTypeCase(name));
    }

    private void populateTypedProxy(
            Data data,
            String name,
            RegistryKey targetKey,
            MappingContext context) {
        Data targetData = context.typeResolver().resolveRequiredData(
                targetKey,
                "typed proxy target of element '" + name + "'");
        data.setSuperType(targetData);
        log.trace("   -> Extends (Typed Proxy): {}", targetData.getName());
        populateXMLConfiguration(data, name, Collections.emptyList(), Collections.emptyList(), true, false, context);
    }

    private void populateAnonymousElement(
            Data data,
            String name,
            MappingContext context) {
        QName elementName = new QName(context.namespace(), name);
        List<InspectorNode> members = context.inspector().getElementInlineStructuredMembers(elementName);
        List<InspectorAttributeNode> xmlAttributes = context.inspector().getElementInlineAttributeMembers(elementName);
        contentMapper.map(data, members, context);
        complexTypeMapper.mapElementAttributesAndSuperType(data, name, xmlAttributes, context);
        populateXMLConfiguration(data, name, members, xmlAttributes, true, false, context);
    }

    private void populateMixedUnions(MappingContext context) {
        context.inspector().getAllGlobalSimpleTypes(context.namespace()).forEach(name -> {
            if (context.simpleTypeBinding(name).isMixedUnion()) {
                RosettaType type = context.registry().get(context.namespace(), name);
                if (type instanceof Data data) {
                    mapMixedUnion(
                            data,
                            context.inspector().getUnionMemberTypes(new QName(context.namespace(), name)),
                            context);
                    populateXMLConfiguration(data, name, Collections.emptyList(), Collections.emptyList(), false, true, context);
                }
            }
        });
    }

    private boolean mapNativeSubstitution(Data data, String elementName, MappingContext context) {
        RegistryKey substitutionGroup = context.inspector().getElementSubstitutionGroupKey(
                new QName(context.namespace(), elementName));
        if (substitutionGroup == null) {
            return false;
        }

        Data superData = context.typeResolver().resolveRequiredData(
                substitutionGroup,
                "substitution-group head of element '" + elementName + "'");
        data.setSuperType(superData);
        log.trace("   -> Extends (Native Substitution): {}", superData.getName());
        return true;
    }

    private void mapMixedUnion(
            Data data,
            List<String> memberQNames,
            MappingContext context) {
        List<String> generatedAttributes = new ArrayList<>();
        for (String memberQName : memberQNames) {
            String localName = memberQName.substring(memberQName.lastIndexOf('}') + 1);
            Attribute attribute = attributeMapper.map(
                    data,
                    localName,
                    memberQName,
                    0,
                    1,
                    "Union Member",
                    context,
                    Optional.empty());
            if (attribute != null) {
                generatedAttributes.add(attribute.getName());
            }
        }
        if (!generatedAttributes.isEmpty()) {
            context.conditionMapper().addChoiceCondition(data, generatedAttributes, 1, true, true);
        }
    }

    private void populateXMLConfiguration(
            Data data,
            String xmlTag,
            List<InspectorNode> members,
            List<InspectorAttributeNode> xmlAttributes,
            boolean isGlobalElement,
            boolean isMixedUnion,
            MappingContext context) {
        context.xmlConfigurationMapper().registerData(
                data,
                xmlTag,
                members,
                xmlAttributes,
                isGlobalElement,
                isMixedUnion,
                context);
    }

}
