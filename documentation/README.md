# Rune XSD Model Importer

The importer converts supported XSD schema shapes into Rune DSL models and a separate `xml-config.json` serialization configuration. It is a Java API with representative XSD 1.0-oriented coverage, not a complete W3C XSD implementation.

Start with the [project's README](../README.md) for build and contribution information.

## Executive Summary & Mission

The importer provides deterministic mappings for fixture-covered XSD constructs and keeps XML-specific metadata in a separate serialization configuration. The goal is reliable interoperability for those covered shapes, not complete W3C XSD conformance.

## Guides

### Usage and integration

- [Getting started](./docs/getting-started.md) — integrate and run the Java API.

### Modeling and reference

- [Capability matrix](./docs/capability-matrix.md) — decide whether a schema shape is within the verified contract.
- [Mapping reference](./docs/mapping-reference.md) — understand qualified identity and generated outputs.
- [Test coverage](./docs/test-coverage.md) — understand what the integration suite proves.

### Architecture and runtime

- [Architecture](./docs/architecture.md) — understand the parsing, validation, and generation pipeline.
- [Advanced serialization](./docs/advanced-serialization.md) — use the generated XML configuration at runtime.

### Bug reporting and community

- [Issue reporting](./docs/issue-reporting.md) — provide a useful reproducer for a defect or feature request.

[← Back to Index](../README.md)
