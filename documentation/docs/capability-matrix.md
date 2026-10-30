# Capability Matrix

This matrix describes representative fixture coverage. **Verified** means the listed shape is exercised by the integration suite; it does not mean every variation is supported. **Partial** means a mapping exists but material semantics remain unverified.

| Area | Status | Verified scope or boundary |
| --- | --- | --- |
| Qualified identity and imports | Verified | Types, elements, and groups use namespace URI plus local name; selected cross-namespace imports are covered. |
| Complex types and global elements | Verified | Global types and elements, direct qualified element/type relationships, proxy types, and native wrappers. |
| Attributes and groups | Verified | Direct attributes, global attribute references, attribute groups, nested groups, and reusable model groups. |
| Simple types | Verified | Representative string and numeric restrictions, enumerations, simple content, lists, and unions. |
| Extensions | Partial | Representative simple/complex and cross-namespace extensions; broader chains, restrictions, and mixed content are not established. |
| Choices | Partial | Direct, grouped, nested, and selected cardinalities; repeated alternative order and native Rune choices are not preserved. |
| Substitution groups | Partial | Direct, multi-level, and native-wrapper examples; broader namespace and root combinations are not established. |
| Recursive type members | Verified | Finite same-namespace self-recursive and mutually recursive complex types. |
| Nested `xsi:type` | Partial | Local and cross-namespace values use qualified URI bindings; exact prefix spelling is not contractual. |
| Runtime XML configuration | Verified | Representative imported elements, qualified global attributes, and root-only namespace declarations. |
| Local element and attribute qualification | Pending | Effective default and per-member `form` namespaces are not yet verified. |

## Known gaps

- XSD 1.1 is unsupported, including assertions, type alternatives, and its additional validation and schema-composition features.
- Open content (`xs:any`, `xs:anyAttribute`), identity constraints, `xs:all`, chameleon namespaces, `xs:redefine`, and `xs:override` are outside the verified contract.
- Root polymorphism, `xsi:nil`/nillable values, exact nested prefix and child-order fidelity, and every invalid XML edge case are not established.
- The importer does not recover unresolved `ref` targets for groups, elements, attributes, or attribute groups. Generation fails during dependency preflight rather than emitting a partial Rune model.
- The importer does not preserve an `xs:attributeGroup` referenced inside an `xs:complexContent` `xs:extension`. Its attributes are silently omitted from the generated Rune type and `xml-config.json`; this shape is not supported.
- A global element and global simple type in the same namespace whose names differ only by case (for example, `Unit` and `unit`) collide after Rune-name normalization and fail generation. This behavior is deferred; the importer does not merge their Rune targets.
- Local anonymous complex and simple types (Russian-doll style), including empty local `xs:complexType` declarations and inline simple types on local attributes, are not supported. Generation fails during dependency preflight because unnamed local types cannot be represented as qualified declarations. Use a named global type instead.

The importer may not reject every unsupported construct consistently. Add a focused fixture and define expected generation and serialization behavior before relying on a currently unverified shape.

[← Back to Index](../README.md)
