# Advanced Runtime Serialization

Generation produces Rune models and a separate `xml-config.json`. The configuration preserves XML-specific metadata that the Rune model does not express.

## Use the generated configuration

Call `EmfRuneGenerator.saveXmlConfiguration(...)` after generation. Load the resulting file into `RosettaXMLConfiguration` and register `XsdRuneModule` with the XML mapper. The configuration supplies namespace-aware attribute, polymorphism, and union behavior.

Key fields include:

- `xmlName` for the XML local name.
- `elementRef` for the qualified identity of a referenced element.
- `xmlRepresentation` for element, attribute, value, or virtual composition.
- `xmlElementFullyQualifiedName` and root `xmlAttributes` for generated root metadata.

The generator creates a missing parent directory for `saveXmlConfiguration(...)`. Direct use of `XmlConfigurationJsonWriter` requires an existing parent directory, a non-null configuration, and a destination ending in lowercase `.json`; it overwrites an existing file.

## Namespace and polymorphism boundaries

Qualified elements and attributes retain their namespace URIs, while unqualified local attributes remain unqualified. Root namespace declarations belong only on document-root candidates. The runtime may choose different lexical prefixes; URI identity, not prefix spelling, is the covered behavior.

Nested `xsi:type` emission is verified for local and cross-namespace derived values assigned to an abstract nested base type in representative named-root schemas. The runtime does not emit `xsi:type` for values declared through a concrete base type; callers must not rely on that shape for schema-valid or subtype-preserving round trips. Nested polymorphism beneath anonymous inline document roots is not yet verified. Root polymorphism is not yet supported for round-trip or generated-output schema validation. Mixed unions are covered for representative pure-enum and mixed values, but overlapping lexical spaces are not.

[← Back to Index](../README.md)
