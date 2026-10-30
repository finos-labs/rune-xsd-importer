# Architecture

The importer separates schema inspection, qualified-reference validation, Rune model generation, and XML serialization configuration. The implementation is under `org.finos.rune.importer.xsd`.

## Pipeline

1. `XsdParserJaxpFactory` creates the third-party parser with fixed secure XML settings.
2. `XsdParserInspector` collects declarations and members while preserving namespace URI and local name.
3. `XsdDependencyValidator` rejects unresolved, malformed, wrong-role, and unsupported cyclic required references before generation.
4. `EmfRuneGenerator` registers and populates Rune types, then writes `.rune` or `.rosetta` models and `xml-config.json`.
5. Integration fixtures compile the generated model and perform scenario-specific validation and XML round trips.

## Extension boundaries

Use `XsdParserInspector` as the public inspection facade and `XsdParserJaxpFactory` as the parser construction path. Do not use a local-name-only lookup or direct third-party parser construction. The active mapper entry points are `DataMapper`, `EnumMapper`, and `TypeAliasMapper`; mapping and serialization remain separate from Rune model mutation.

The parser factory rejects `DOCTYPE`, external entity resolution, external DTD loading, XInclude, and entity expansion. It does not impose input-size, file-count, graph-depth, or node-count limits; hosting applications own those limits.

`RuneBuiltinRegistry` maps supported XSD built-ins to Rune types, using `string` for built-ins whose restrictions are not represented separately. Unresolved user declarations do not use that fallback.

[← Back to Index](../README.md)
