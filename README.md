[![badge-labs](https://user-images.githubusercontent.com/327285/230928932-7c75f8ed-e57b-41db-9fb7-a292a13a1e58.svg)](https://community.finos.org/docs/governance/lifecycle-stages/labs)

# Rune XSD Model Importer

The Rune XSD Model Importer converts W3C XML Schema Definitions (XSD) into Rune DSL models and XML serialization configuration files.

The public framework provides deterministic mappings for covered XSD constructs including simple and complex types, restrictions, unions, groups, choices, extensions, substitution groups, and selected polymorphism scenarios.

Public fixture coverage is representative rather than complete W3C XSD coverage. Known limitations and unsupported constructs are documented in the capability matrix.

## Installation

The project currently builds from source.

Prerequisites:

- Java 21
- Maven

The build resolves Rune, Rosetta, and XML dependencies declared in `pom.xml`. On a clean machine, Maven must have access to the repositories configured for those public dependencies.

Clone the repository and run:

```sh
mvn test
```

`mvn test` is reserved for isolated component tests. To run the complete public schema-generation and round-trip suite, use:

```sh
mvn verify
```

For a clean release or CI verification, remove previous Maven output first:

```sh
mvn clean verify
```

To compile without running tests:

```sh
mvn -DskipTests compile
```

## Usage

The importer is exposed through a programmatic Java API. A complete usage example is available in the [Getting Started guide](documentation/docs/getting-started.md).

The basic workflow is:

1. Bootstrap the Rune/EMF infrastructure.
2. Parse the XSD through `XsdParserJaxpFactory.defaultFactory()` using the default XML security policy.
3. Configure target namespaces and Rune package mappings.
4. Generate Rune models.
5. Write the generated Rune and XML configuration artifacts.

Model files use `.rune` by default. Call the compatibility-preserving three-argument `RuneGenerator.generateModels(...)` overload with `XsdImporterConfig.builder().modelFileExtension(".rosetta").build()` to emit `.rosetta` files instead. Extensions are trimmed, lower-cased, and given a leading dot before validation; only `.rune` and `.rosetta` are supported.

Application code should create parsers through `XsdParserJaxpFactory`. The factory always uses its fixed secure XML settings. It does not apply fixed schema-size or dependency-count limits, so large industry schemas remain subject to the hosting application's resource expectations.

## Development setup

The public test suite covers both model generation and round-trip XML serialization.

Useful commands:

```sh
mvn test
mvn verify
mvn clean verify
mvn -DskipTests compile
```

To run one public integration scenario during development, use Failsafe directly:

```sh
mvn -Dit.test=NestedChoiceIntegrationTest -DfailIfNoTests=false failsafe:integration-test failsafe:verify
```

`mvn test` does not claim feature compatibility; `mvn verify` is the public generation, validation, compilation, and round-trip compatibility gate.

See the [public test coverage](documentation/docs/test-coverage.md) and [architecture overview](documentation/docs/architecture.md) for details about the implementation and verification pipeline.

## Documentation

- [Documentation hub](documentation/README.md)
- [Getting Started guide](documentation/docs/getting-started.md)
- [Architecture overview](documentation/docs/architecture.md)
- [XSD-to-Rune mapping reference](documentation/docs/mapping-reference.md)
- [Capability matrix](documentation/docs/capability-matrix.md)
- [Advanced serialization](documentation/docs/advanced-serialization.md)
- [Public test coverage](documentation/docs/test-coverage.md)
- [Issue reporting guide](documentation/docs/issue-reporting.md)

## Roadmap

Current priorities include:

1. Rune DSL version bump
1. Root-level `xsi:type` polymorphism support.
2. Better handling of repeated choices and ambiguous unions.
3. Support for open content such as `xs:any` and `xs:anyAttribute`.

See the [capability matrix](documentation/docs/capability-matrix.md) for the current status and known limitations.

## Contributing

For contribution requirements, see [CONTRIBUTING.md](CONTRIBUTING.md).

Please report bugs and feature gaps through the project’s [issue tracker](https://github.com/finos-labs/rune-xsd-importer/issues), including a minimal XSD and XML reproducer where applicable.

## License

Copyright 2026 FINOS

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
