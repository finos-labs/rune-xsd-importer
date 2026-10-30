package org.finos.rune.importer.xsd.mapper.mapping;

import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;

import java.util.List;
import javax.xml.namespace.QName;

/**
 * The one generation binding selected for a global XSD simple type.
 */
public enum SimpleTypeBinding {
    TYPE_ALIAS,
    EMPTY_STRING_TYPE_ALIAS,
    ENUMERATION,
    ENUM_UNION,
    MIXED_UNION;

    public static SimpleTypeBinding resolve(XsdParserInspector inspector, RegistryKey key) {
        QName name = new QName(key.namespace(), key.localName());
        boolean enumeration = inspector.isSimpleTypeEnumeration(name);
        if (isEmptyStringEnumeration(inspector, name, enumeration)) {
            return EMPTY_STRING_TYPE_ALIAS;
        }
        if (inspector.isUnionOfEnums(name)) {
            return ENUM_UNION;
        }
        if (enumeration) {
            return ENUMERATION;
        }
        return inspector.isUnionType(name) ? MIXED_UNION : TYPE_ALIAS;
    }

    public boolean isTypeAlias() {
        return this == TYPE_ALIAS || this == EMPTY_STRING_TYPE_ALIAS;
    }

    public boolean isEmptyStringEnumeration() {
        return this == EMPTY_STRING_TYPE_ALIAS;
    }

    public boolean isEnumeration() {
        return this == ENUMERATION || this == ENUM_UNION;
    }

    public boolean isEnumUnion() {
        return this == ENUM_UNION;
    }

    public boolean isMixedUnion() {
        return this == MIXED_UNION;
    }

    private static boolean isEmptyStringEnumeration(
            XsdParserInspector inspector,
            QName name,
            boolean enumeration) {
        if (!enumeration) {
            return false;
        }

        var node = inspector.getStructuredSimpleType(name);
        List<String> values = inspector.getSimpleTypeEnumValues(name);
        return node != null
                && ("{" + RuneBuiltinRegistry.XSD_NS + "}string").equals(node.baseTypeQName())
                && values.size() == 1
                && values.getFirst().isEmpty();
    }
}
