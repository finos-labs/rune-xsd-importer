package org.finos.rune.importer.xsd.mapper;

import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.RosettaTypeAlias;
import com.regnosys.rosetta.rosetta.TypeCall;
import com.regnosys.rosetta.rosetta.TypeParameter;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.inspector.model.InspectorSimpleTypeNode;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.generation.RestrictionMapper;
import org.finos.rune.importer.xsd.mapper.mapping.SimpleTypeBinding;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.mapper.resolution.TypeReferenceResolver;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;

/**
 * Mapper responsible for converting XSD SimpleTypes with restrictions into Rune TypeAliases.
 * <p>
 * Supported constraints:
 * <ul>
 * <li>Pattern (Regex)</li>
 * <li>Min/Max Length (Strings)</li>
 * <li>Min/Max Inclusive/Exclusive (Numbers)</li>
 * <li>Total/FractionDigits (Decimals)</li>
 * </ul>
 */
public class TypeAliasMapper extends AbstractRuneTypeMapper<RosettaTypeAlias> {

    private static final Logger log = LoggerFactory.getLogger(TypeAliasMapper.class);

    public TypeAliasMapper(MappingSession session) {
        super(session);
    }

    private final RestrictionMapper restrictionMapper = new RestrictionMapper(expressionFactory, runeFactory);

    @Override
    public List<RosettaTypeAlias> register(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 1: Registering TypeAliases for namespace '{}'", namespace);
        MappingContext context = mappingContext(inspector, namespace, registry);
        List<RosettaTypeAlias> createdAliases = new ArrayList<>();

        inspector.getAllGlobalSimpleTypes(namespace).forEach(xsdName -> {
            if (context.simpleTypeBinding(xsdName).isTypeAlias()) {
                registerShell(
                        runeFactory::createRosettaTypeAlias,
                        xsdName,
                        "Generated from XSD SimpleType",
                        context,
                        createdAliases);
            }
        });

        inspector.getAllGlobalElements(namespace).forEach(xsdName -> {
            if (inspector.isTypeAliasWrapperElement(new QName(namespace, xsdName))) {
                registerShell(
                        runeFactory::createRosettaTypeAlias,
                        xsdName,
                        "Generated from Global Element (Virtual Alias)",
                        context,
                        createdAliases);
            }
        });

        log.debug("Registered {} TypeAliases.", createdAliases.size());
        return createdAliases;
    }

    @Override
    public void populate(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry) {
        log.debug("Phase 2: Populating TypeAliases for namespace '{}'", namespace);
        MappingContext context = mappingContext(inspector, namespace, registry);

        inspector.getAllGlobalSimpleTypes(namespace).forEach(xsdName -> {
            SimpleTypeBinding binding = context.simpleTypeBinding(xsdName);
            if (binding.isTypeAlias()) {
                RosettaType type = registry.get(namespace, xsdName);
                if (type instanceof RosettaTypeAlias alias) {
                    populateTypeAlias(alias, xsdName, inspector, context, binding.isEmptyStringEnumeration(), true);
                }
            }
        });

        inspector.getAllGlobalElements(namespace).forEach(xsdName -> {
            if (inspector.isTypeAliasWrapperElement(new QName(namespace, xsdName))) {
                RosettaType type = registry.get(namespace, xsdName);
                if (type instanceof RosettaTypeAlias alias) {
                    populateTypeAlias(alias, xsdName, inspector, context, false, false);
                }
            }
        });
    }

    private void populateTypeAlias(
            RosettaTypeAlias alias,
            String xsdName,
            XsdParserInspector inspector,
            MappingContext context,
            boolean emptyStringEnumeration,
            boolean isGlobalSimpleType) {
        log.debug("Populating TypeAlias: {}", alias.getName());

        QName qualifiedName = new QName(context.namespace(), xsdName);
        InspectorSimpleTypeNode node = isGlobalSimpleType
                ? inspector.getStructuredSimpleType(qualifiedName)
                : inspector.getStructuredGlobalElementAsSimpleTypeNode(qualifiedName);
        if (node == null) {
            log.warn("Node not found for TypeAlias: {}", xsdName);
            return;
        }

        String baseTypeQName = node.baseTypeQName();
        String resolutionContext = "base type of simple type '" + xsdName + "'";
        RegistryKey baseKey = RegistryKey.parse(baseTypeQName);
        if (baseKey == null) {
            throw MappingResolutionException.invalidReference(baseTypeQName, resolutionContext);
        }
        TypeReferenceResolver resolver = context.typeResolver();
        RosettaType baseType = resolver.resolve(baseKey);
        String targetPrimitiveName = RuneBuiltinRegistry.primitiveFamily(baseKey).orElse("string");
        String localBase = baseKey.localName();

        log.trace("Base QName: '{}', Local Base: '{}', Target Primitive: '{}', BaseType Found: {}",
                baseTypeQName, localBase, targetPrimitiveName, baseType != null);

        RosettaType finalType;
        if (baseType == null) {
            finalType = resolver.resolveRequired(baseKey, resolutionContext);
        } else {
            finalType = baseType;
        }

        log.trace("Resolved finalType: {} (Class: {})", finalType.getName(), finalType.getClass().getSimpleName());

        log.trace("Attempting enrichment for type: {}", finalType.getName());
        enrichPrimitive(finalType, targetPrimitiveName, resolver);

        TypeCall call = runeFactory.createTypeCall();
        call.setType(finalType);
        alias.setTypeCall(call);

        log.trace("Mapping restrictions for alias '{}' on base '{}'", alias.getName(), finalType.getName());
        restrictionMapper.map(alias, node, emptyStringEnumeration);
    }

    private void enrichPrimitive(RosettaType type, String basicType, TypeReferenceResolver resolver) {
        if (type == null) {
            log.debug("Cannot enrich null type for basicType: {}", basicType);
            return;
        }

        if (!(type instanceof RosettaTypeAlias alias)) {
            log.debug("Skipping enrichment: Type '{}' is NOT a RosettaTypeAlias. It is a {}", type.getName(), type.getClass().getName());
            return;
        }

        log.trace("Enriching primitive alias '{}' with standard parameters for '{}'", alias.getName(), basicType);

        if ("string".equals(basicType)) {
            addParameterIfMissing(alias, "pattern", "string", resolver);
            addParameterIfMissing(alias, "minLength", "int", resolver);
            addParameterIfMissing(alias, "maxLength", "int", resolver);
        } else if ("int".equals(basicType) || "number".equals(basicType)) {
            addParameterIfMissing(alias, "min", "int", resolver);
            addParameterIfMissing(alias, "max", "int", resolver);
            addParameterIfMissing(alias, "digits", "int", resolver);
            addParameterIfMissing(alias, "fractionalDigits", "int", resolver);
        }
    }

    /**
     * Ensures that the primitive type (string, int, etc.) has the expected TypeParameters
     * so that restrictions can link to them.
     */
    private void addParameterIfMissing(RosettaTypeAlias alias, String paramName, String paramTypeName, TypeReferenceResolver resolver) {
        boolean exists = alias.getParameters().stream().anyMatch(p -> p.getName().equals(paramName));
        if (!exists) {
            TypeParameter tp = runeFactory.createTypeParameter();
            tp.setName(paramName);

            RegistryKey paramKey = RegistryKey.parse("{http://www.w3.org/2001/XMLSchema}" + paramTypeName);
            RosettaType paramType = resolver.resolve(paramKey);

            if (paramType == null) {
                log.error("Failed to resolve parameter type '{}' for parameter '{}' on alias '{}'", paramKey, paramName, alias.getName());
            }

            TypeCall call = runeFactory.createTypeCall();
            call.setType(paramType);
            tp.setTypeCall(call);

            alias.getParameters().add(tp);
            log.trace("Added missing parameter '{}' to type '{}'", paramName, alias.getName());
        } else {
            log.trace("Parameter '{}' already exists on type '{}'", paramName, alias.getName());
        }
    }

}
