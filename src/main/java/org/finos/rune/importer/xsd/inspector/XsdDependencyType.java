package org.finos.rune.importer.xsd.inspector;

/**
 * Semantic type of an XSD declaration dependency.
 */
public enum XsdDependencyType {
    /** A complex-type content or attribute member referring to a type. */
    TYPE_MEMBER_REFERENCE,
    /** A simple/complex type base or inheritance reference. */
    BASE_TYPE_REFERENCE,
    /** A model-group reference. */
    GROUP_REFERENCE,
    /** A global element type or element reference. */
    ELEMENT_REFERENCE,
    /** A substitution-group head reference. */
    SUBSTITUTION_REFERENCE,
}
