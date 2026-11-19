# Advanced Runtime Serialization

Generation produces Rune models and a separate `xml-config.json`. The configuration preserves XML-specific metadata that the Rune model does not express.

## Use the generated configuration

Call `EmfRuneGenerator.saveXmlConfiguration(...)` after generation. Load the resulting file into `RosettaXMLConfiguration` and register `XsdRuneModule` with an `XmlMapper`. The configuration supplies namespace-aware attribute, polymorphism, and union behavior.

Key fields include:

- `xmlName` for the XML local name.
- `elementRef` for the qualified identity of a referenced element.
- `xmlRepresentation` for element, attribute, value, or virtual composition.
- `xmlElementFullyQualifiedName` and root `xmlAttributes` for generated root metadata.

The generator creates a missing parent directory for `saveXmlConfiguration(...)`. Direct use of `XmlConfigurationJsonWriter` requires an existing parent directory, a non-null configuration, and a destination ending in lowercase `.json`; it overwrites an existing file.

## Virtual model groups

A property with `xmlRepresentation` set to `VIRTUAL` groups members in the Rune model. In XML, those members appear directly under the owning element. During deserialization, the runtime skips XML type resolution for the virtual property itself because it has no XML tag of its own.

## Namespace and polymorphism boundaries

Qualified elements and attributes retain their namespace URIs, while unqualified local attributes remain unqualified. Root namespace declarations belong only on document-root candidates. The runtime may choose different lexical prefixes; URI identity, not prefix spelling, is the covered behavior.

Nested `xsi:type` emission is verified for local and cross-namespace derived values assigned to an abstract nested base type in representative named-root schemas. Outside substitution groups, emission through a concrete base type remains unsupported.

For the verified single and repeated nested references to a same-namespace substitution group, the selected element supplies its declared type. A value of that type omits `xsi:type`, while a further-derived value emits its qualified XSD type name. The single-member regression covers generated configuration and configuration that identifies the abstract head by its type QName. The repeated-member regression covers a mixed list with generated configuration.

Nested polymorphism beneath anonymous inline document roots is not yet verified. Root polymorphism is not yet supported for round-trip or generated-output schema validation. Mixed unions are covered for representative pure-enum and mixed values, but overlapping lexical spaces are not.

[← Back to Index](../README.md)
