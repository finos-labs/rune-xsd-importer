package org.finos.rune.importer.xsd.registry;

import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaRootElement;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.simple.Annotation;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RuneBuiltinRegistry {

    private static final Logger log = LoggerFactory.getLogger(RuneBuiltinRegistry.class);

    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String STD_LIB_NAMESPACE = "com.rosetta.model";
    private static final String RUNE_STRING = "string";
    private static final String RUNE_INT = "int";
    private static final String RUNE_NUMBER = "number";
    private static final String RUNE_BOOLEAN = "boolean";
    private static final String RUNE_DATE = "date";
    private static final String RUNE_TIME = "time";
    private static final String RUNE_ZONED_DATE_TIME = "zonedDateTime";
    private static final String RUNE_DATE_TIME_FAMILY = "dateTime";
    private static final Map<RegistryKey, BuiltinMapping> BUILTIN_MAPPINGS = createBuiltinMappings();

    private final Map<RegistryKey, RosettaType> typeMap = new HashMap<>();
    private final Map<String, Annotation> annotationMap = new HashMap<>();

    public RuneBuiltinRegistry(ResourceSet resourceSet) {
        initializeFromResourceSet(resourceSet);
    }

    public RosettaType get(RegistryKey key) {
        return typeMap.get(key);
    }

    public Annotation getAnnotation(String name) {
        return annotationMap.get(name);
    }

    /**
     * Returns the Rune primitive family associated with an exact XSD builtin
     * QName. This metadata is shared by mapper-side primitive classification
     * and builtin object registration.
     */
    public static Optional<String> primitiveFamily(RegistryKey key) {
        return Optional.ofNullable(BUILTIN_MAPPINGS.get(key))
                .map(BuiltinMapping::runePrimitiveFamily);
    }

    private void initializeFromResourceSet(ResourceSet rs) {
        log.info("Initializing Built-in Registry from ResourceSet...");

        Map<String, RosettaType> stdTypes = new HashMap<>();
        boolean foundStdLib = false;

        for (Resource res : rs.getResources()) {
            if (!res.getContents().isEmpty()) {
                EObject root = res.getContents().get(0);
                if (root instanceof RosettaModel model) {
                    if (STD_LIB_NAMESPACE.equals(model.getName())) {
                        log.debug("Found Standard Library Resource: {}", res.getURI());
                        foundStdLib = true;
                        indexElements(model, stdTypes);
                    }
                }
            }
        }

        if (!foundStdLib) {
            log.error("CRITICAL: Standard Library ('{}') not found in ResourceSet. " +
                    "Ensure loadStandardLibrary() is called before Registry initialization.", STD_LIB_NAMESPACE);
            throw new IllegalStateException(
                    "Rune standard library '" + STD_LIB_NAMESPACE
                            + "' was not found; the ResourceSet was not initialized with the standard library");
        }

        registerMappings(stdTypes);
    }

    private void indexElements(RosettaModel model, Map<String, RosettaType> typeIndex) {
        for (RosettaRootElement element : model.getElements()) {
            if (element instanceof RosettaType type) {
                typeIndex.put(type.getName(), type);
                log.trace("Indexed standard type: {}", type.getName());
            }
            else if (element instanceof Annotation ann) {
                annotationMap.put(ann.getName(), ann);
                log.trace("Indexed standard annotation: {}", ann.getName());
            }
        }
    }

    private void registerMappings(Map<String, RosettaType> stdTypes) {
        Map<RegistryKey, RosettaType> registeredTypes = new HashMap<>();
        List<String> missingMappings = new ArrayList<>();
        BUILTIN_MAPPINGS.values().forEach(mapping -> {
            if (!register(mapping.xsdName(), mapping.runeTypeName(), stdTypes, registeredTypes)) {
                missingMappings.add(mapping.xsdName() + " -> " + mapping.runeTypeName());
            }
        });

        if (!missingMappings.isEmpty()) {
            throw new IllegalStateException(
                    "Rune standard library is incomplete; missing mapped Rune types: "
                            + String.join(", ", missingMappings));
        }

        typeMap.putAll(registeredTypes);
    }

    private boolean register(
            String xsdName,
            String rosettaName,
            Map<String, RosettaType> availableTypes,
            Map<RegistryKey, RosettaType> registeredTypes) {
        RosettaType type = availableTypes.get(rosettaName);
        if (type != null) {
            registeredTypes.put(RegistryKey.of(XSD_NS, xsdName), type);
            return true;
        } else {
            log.warn("Cannot register XSD builtin mapping '{}' because Rune type '{}' is unavailable.",
                    xsdName, rosettaName);
            return false;
        }
    }

    private static Map<RegistryKey, BuiltinMapping> createBuiltinMappings() {
        Map<RegistryKey, BuiltinMapping> mappings = new LinkedHashMap<>();

        add(mappings, "string", RUNE_STRING, RUNE_STRING);
        add(mappings, "token", RUNE_STRING, RUNE_STRING);
        add(mappings, "normalizedString", RUNE_STRING, RUNE_STRING);
        add(mappings, "language", RUNE_STRING, RUNE_STRING);
        add(mappings, "anyURI", RUNE_STRING, RUNE_STRING);
        add(mappings, "duration", RUNE_STRING, RUNE_STRING);
        add(mappings, "base64Binary", RUNE_STRING, RUNE_STRING);
        add(mappings, "hexBinary", RUNE_STRING, RUNE_STRING);
        add(mappings, "anySimpleType", RUNE_STRING, RUNE_STRING);
        add(mappings, "ID", RUNE_STRING, RUNE_STRING);
        add(mappings, "QName", RUNE_STRING, RUNE_STRING);
        add(mappings, "Name", RUNE_STRING, RUNE_STRING);
        add(mappings, "NCName", RUNE_STRING, RUNE_STRING);
        add(mappings, "NMTOKEN", RUNE_STRING, RUNE_STRING);
        add(mappings, "IDREF", RUNE_STRING, RUNE_STRING);
        add(mappings, "ENTITY", RUNE_STRING, RUNE_STRING);
        add(mappings, "NMTOKENS", RUNE_STRING, RUNE_STRING);
        add(mappings, "IDREFS", RUNE_STRING, RUNE_STRING);
        add(mappings, "ENTITIES", RUNE_STRING, RUNE_STRING);
        add(mappings, "NOTATION", RUNE_STRING, RUNE_STRING);

        add(mappings, "int", RUNE_INT, RUNE_INT);
        add(mappings, "integer", RUNE_INT, RUNE_INT);
        add(mappings, "short", RUNE_INT, RUNE_INT);
        add(mappings, "long", RUNE_INT, RUNE_INT);
        add(mappings, "byte", RUNE_INT, RUNE_INT);
        add(mappings, "unsignedInt", RUNE_INT, RUNE_INT);
        add(mappings, "positiveInteger", RUNE_INT, RUNE_INT);
        add(mappings, "nonNegativeInteger", RUNE_INT, RUNE_INT);
        add(mappings, "unsignedLong", RUNE_INT, RUNE_INT);
        add(mappings, "unsignedShort", RUNE_INT, RUNE_INT);
        add(mappings, "unsignedByte", RUNE_INT, RUNE_INT);
        add(mappings, "negativeInteger", RUNE_INT, RUNE_INT);
        add(mappings, "nonPositiveInteger", RUNE_INT, RUNE_INT);

        add(mappings, "decimal", RUNE_NUMBER, RUNE_NUMBER);
        add(mappings, "float", RUNE_NUMBER, RUNE_NUMBER);
        add(mappings, "double", RUNE_NUMBER, RUNE_NUMBER);

        add(mappings, "boolean", RUNE_BOOLEAN, RUNE_BOOLEAN);

        add(mappings, "date", RUNE_DATE, RUNE_DATE);
        add(mappings, "dateTime", RUNE_ZONED_DATE_TIME, RUNE_DATE_TIME_FAMILY);
        add(mappings, "time", RUNE_TIME, RUNE_TIME);
        add(mappings, "gYear", RUNE_DATE, RUNE_DATE);
        add(mappings, "gYearMonth", RUNE_DATE, RUNE_DATE);
        add(mappings, "gMonth", RUNE_DATE, RUNE_DATE);
        add(mappings, "gMonthDay", RUNE_DATE, RUNE_DATE);
        add(mappings, "gDay", RUNE_DATE, RUNE_DATE);

        return Map.copyOf(mappings);
    }

    private static void add(
            Map<RegistryKey, BuiltinMapping> mappings,
            String xsdName,
            String runeTypeName,
            String runePrimitiveFamily) {
        BuiltinMapping mapping = new BuiltinMapping(xsdName, runeTypeName, runePrimitiveFamily);
        mappings.put(RegistryKey.of(XSD_NS, xsdName), mapping);
    }

    private record BuiltinMapping(
            String xsdName,
            String runeTypeName,
            String runePrimitiveFamily) {
    }
}
