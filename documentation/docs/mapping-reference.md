# XSD-to-Rune Mapping Reference

This reference describes fixture-backed mappings. Check the [capability matrix](./capability-matrix.md) before using a schema shape outside these examples.

## Qualified identity

Every declaration is identified by namespace URI and local name. The importer does not resolve an unknown prefix or an unresolved reference by searching another namespace for the same local name. Invalid QNames, missing required declarations, wrong declaration roles, and unsupported cycles fail generation.

Finite self-recursive and mutually recursive complex-type members are supported when every member is in the same namespace. Cross-namespace, inheritance, group, attribute-group, element, substitution-group, and mixed-role cycles are unsupported.

## Generated Rune model

- Global complex types, groups, and elements become Rune declarations.
- Direct, qualified relationships between a global element and a complex type can reuse one generated target. Primitive global elements use wrapper types; other elements can use proxy types.
- Supported string and numeric restrictions become aliases or enums. XSD built-in types without a separate Rune representation, such as `ID`, `IDREF`, `QName`, and `NCName`, use the supported `string` fallback.
- Model groups become compositional fields. Choices become Rune validation conditions; repeated alternative ordering is not preserved.
- XSD extension becomes Rune inheritance for the verified fixture shapes.

Generated identifiers are deterministic but not collision-free. Distinct source names containing punctuation or unsupported Unicode can normalize to the same Rune identifier; consumers must not rely on source spelling surviving unchanged.

## XML configuration

`xml-config.json` is the source of truth for XML names, namespace URIs, element references, attributes, and virtual group composition. Load it with `XsdRuneModule` at runtime; do not reconstruct XML identity from Rune or Java property names.

Qualified elements and attributes retain their namespace URI. Unqualified local attributes remain unqualified. Root namespace declarations are emitted only on document-root candidates. Exact prefix spelling is not part of the contract.

[← Back to Index](../README.md)
