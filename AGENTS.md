# Instructions for coding agents

## Project contract

This repository is the Rune XSD Model Importer (`org.finos.rune:xsd-rune-importer`). It is a Java 21 API that imports supported XSD 1.0-oriented schema shapes into Rune DSL models and a companion `xml-config.json` serialization configuration.

The importer has representative, fixture-backed coverage. It is not a complete W3C XSD implementation, a general XSD validator, a command-line tool, or a Maven plugin.

Treat the current source tree, `pom.xml`, and active tests as the authority for implementation behavior. Treat the user documentation as the authority for the public support boundary only after verifying that it still matches code and tests.

## Authoritative locations

| Concern | Source of truth |
| --- | --- |
| Public API | `src/main/java/org/finos/rune/importer/xsd/api/` |
| Generation pipeline | `core/EmfRuneGenerator.java`, `core/XsdDependencyValidator.java` |
| Parser construction and XML hardening | `security/XsdParserJaxpFactory.java` |
| Inspection and dependency collection | `inspector/XsdParserInspector.java`, `inspector/XsdParserInspectorHelper.java` |
| Qualified type and XML configuration registries | `registry/` |
| Rune mapping | `mapper/` |
| Runtime XML serialization | `builder/serialization/` |
| Representative integration scenarios | `src/test/java/org/finos/rune/importer/xsd/integration/features/` |
| Focused regressions | `src/test/java/org/finos/rune/importer/xsd/regression/` |
| User documentation | `README.md`, `documentation/README.md`, and `documentation/docs/` |

User documentation consists of the getting-started, architecture, mapping-reference, capability-matrix, advanced-serialization, test-coverage, and issue-reporting guides. Do not restore the removed standalone XSD 1.1 analysis; record XSD 1.1 only as an unsupported capability boundary in the capability matrix.

## Public API and generation lifecycle

The public generation entry points are:

- `XsdImporterDefaultSetup.setupInfrastructure()`
- `RuneGenerator` and `EmfRuneGenerator`
- `RuneGeneratorConfig`
- `XsdImporterConfig`
- `XsdParserJaxpFactory.defaultFactory()`
- `XsdParserInspector`

The normal lifecycle is:

1. Bootstrap the Rune/EMF infrastructure with `XsdImporterDefaultSetup`.
2. Build an `XsdParser` through `XsdParserJaxpFactory`.
3. Construct `XsdParserInspector` from that parser.
4. Configure every namespace that should generate output in `RuneGeneratorConfig`.
5. Invoke `generateModels(...)`.
6. Invoke `saveRuneModels(...)` and/or `saveXmlConfiguration(...)`.

`EmfRuneGenerator` is single-use for one generation run. It retains the type registry, model factory, and active `XsdImporterConfig`; callers must use distinct generator instances for concurrent or subsequent runs. The implementation does not enforce this lifecycle with a state machine. Do not rely on persistence or configuration calls before generation to return meaningful output.

`XsdImporterConfig` is immutable. Its model extension is normalized with `Locale.ROOT`; only `.rune` and `.rosetta` are accepted. The two-argument `generateModels(...)` overload uses the default `.rune` configuration. Preserve the three-argument overload because it carries the model-extension contract through the supported generate-then-save lifecycle.

`RuneGeneratorConfig` stores namespace values exactly as supplied. It does not validate, trim, or normalize namespace URIs or package names. An unmapped namespace resolves to `org.finos.rune.unknown`; that fallback does not mean the namespace was configured correctly.

## Parser construction and XML security

Application code and tests must not instantiate `org.xmlet.xsdparser.core.XsdParser` directly. Do not add `new XsdParser(...)` outside the private `SecuredXsdParser` implementation inside `XsdParserJaxpFactory`.

Use:

```java
XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(inputPath);
```

The factory has fixed security behavior. It must continue to enable secure processing; reject `DOCTYPE`; disable external general entities, external parameter entities, external DTD loading, XInclude, and entity expansion; set external DTD and schema JAXP access to empty strings; and reject entity resolution from importer-owned DOM builders.

The factory supports per-call xmlet `ParserConfig` through `createParser(Path, ParserConfig)`. That configuration is not a security-policy extension point and must not weaken the fixed JAXP policy. Do not introduce a custom security policy, a trusted/legacy factory profile, global JAXP system-property changes, transformer-factory APIs, schema staging, schema rewriting, or a generic dependency graph as incidental work.

The factory imposes no fixed byte, file-count, dependency-count, depth, node-count, or output-size limits. The hosting application owns workload limits. Do not add such limits without an approved design because large schemas are a supported workload.

The xmlet superclass eagerly constructs a DOM builder. `XsdParserJaxpFactory` uses a thread-local active factory only to supply its fixed builder during that constructor path. The hook must fail closed when no active factory exists; do not restore an unhardened fallback.

## Qualified identity, inspection, and dependency preflight

An XSD declaration identity is the pair `(namespace URI, local name)`. A local name alone is never a safe substitute once imports or includes are present.

Use `RegistryKey` and qualified `QName` values for identity-sensitive resolution. `RegistryKey.parse(...)` accepts only nonblank Clark notation (`{namespace}local`) or slash notation (`namespace/local`) and returns `null` for malformed or unqualified values. Do not convert an unresolved reference into a local-name search.

`XsdParserInspector` is the public inspection facade. Its declaration-oriented queries use `QName` and return qualified `RegistryKey` values where appropriate. The helper is package-private implementation detail. Do not add one-argument, bare-local-name lookup overloads to the facade or helper.

The inspector maintains separate qualified indexes for complex types, simple types, elements, groups, and attributes, plus an owning-schema identity index. Preserve this split. A same-name declaration of a different role must not be accepted by looking in another declaration category.

QName resolution rules:

- Resolve lexical prefixes from the owning schema's namespace bindings.
- Resolve unqualified, non-built-in references only when valid schema context exists.
- Canonicalize only genuine XSD built-ins—an explicit `xs:` or `xsd:` reference, a supported unprefixed built-in, or an xmlet `XsdBuiltInDataType` link—to `http://www.w3.org/2001/XMLSchema`.
- Leave blank, malformed, unknown-prefix, wrongly bound-prefix, empty-Clark-namespace, and context-free unqualified references unresolved.
- Preserve raw lexical `type`, `base`, `ref`, and `substitutionGroup` attributes when xmlet links have lost lexical prefix information.
- Resolve global attribute references and attribute groups by qualified identity before reading their declaration details.
- Keep the detached-parser group fallback narrow: it may apply only to one parser-resolved candidate for an unprefixed group reference. Duplicate local names must fail closed.

`XsdDependencyValidator` runs before mapper registration. Required dependencies include the qualified references that the inspector collects, including simple-type bases and union members. It must reject invalid QNames, unresolved required declarations, wrong declaration roles, and unsupported cycles. Wrong-role diagnostics must retain both `wrongTargetType` and `expectedType`.

The validator uses `UNSEEN`, `VISITING`, and `COMPLETE` traversal states. Preserve their semantics. A recursive component is accepted only when every declaration is a `TYPE`, every declaration shares one namespace URI, and every internal edge is explicitly `TYPE_MEMBER_REFERENCE`. Every target still must be parsed, resolved, and role-checked. Base-type, group, attribute-group, element, substitution-group, mixed-role, unresolved, and cross-namespace cycles remain fail-closed.

## Registry rules

Initialize registries in this order:

1. Load the Rune standard-library resources into the active `ResourceSet`.
2. Construct `RuneBuiltinRegistry(ResourceSet)`.
3. Construct `RuneTypeRegistry(RuneBuiltinRegistry)`.

`RuneBuiltinRegistry` requires the exact `com.rosetta.model` standard-library model and every Rune type in its mapping table. It must collect missing mappings before publishing entries and throw `IllegalStateException` when the standard library is unavailable or incomplete.

Built-in resolution is qualified. `RuneTypeRegistry.resolve(RegistryKey)` must check generated-user registrations before XSD built-ins. Built-in fallback applies only to exact XSD built-in identities and must never turn an unresolved user declaration into a built-in or `string`.

The supported `string` fallback covers XSD built-ins whose finer restrictions are not modeled separately, including `ID`, `IDREF`, `QName`, `NCName`, and list-like lexical types. It does not authorize local-name fallback or unsupported user-type recovery.

`RuneTypeRegistry.register(...)` is idempotent only for the same object under the same qualified key and must reject replacement with another object. `registerConfig(...)` must merge compatible XML configuration contributions and reject conflicting scalar fields or map entries. `getXmlConfigs()` returns the mutable internal map for compatibility; do not change that return type or mutability without an explicit compatibility decision.

## Mapper architecture and session ownership

The mapper is target-oriented. It maps inspected schema data into Rune/EMF symbols in two phases: register all declaration shells first, then populate members, inheritance, annotations, and XML configuration. Preserve this shell-first lifecycle because it enables supported recursive complex-type members.

`EmfRuneGenerator` creates one fresh `MappingSession` per generation invocation and passes that same session to `EnumMapper`, `TypeAliasMapper`, and `DataMapper`. The mappers require a session constructor. Do not introduce no-argument mapper constructors, static session stores, or independently scoped sessions that make one generation run lose global-element bindings, generated-name collisions, or XML-configuration indexes.

The mapper packages have these responsibilities:

| Package | Responsibility |
| --- | --- |
| `mapper` | Active root mappers and shared shell registration. |
| `mapper.context` | Generation-scoped `MappingSession` and per-operation `MappingContext`. |
| `mapper.mapping` | Immutable routes and generated-name registration. |
| `mapper.generation` | Data members, attributes, complex-type population, choices, and restrictions. |
| `mapper.resolution` | Qualified type resolution, identifier normalization, and mapping failures. |
| `mapper.serialization` | XML configuration assembly, indexing, and JSON writing. |

Use these terms consistently: a **mapper** is an active generation engine, a **mapping** is immutable data shared across phases, and a **resolver** selects or looks up an existing value.

`GlobalElementBinding`, `SimpleTypeBinding`, and `EnumMapping` are immutable. `TypeNameIndex` owns qualified source-name registration and generated-name collision detection. Do not add a second generated-name ledger or mutable route descriptor.

Global-element routing is decided once during registration and read from `MappingSession` during population. Its modes distinguish virtual aliases, native wrappers, inline proxies, merged typed elements, typed proxies, and anonymous inline elements. Do not recompute a route from local names during population or XML configuration.

The mapper package has no SPI. Do not reintroduce a feature-service locator, `mapper.feature` package, `ServiceLoader` integration, or claims that mapper behavior is replaceable. A future SPI requires a separate architecture decision, a documented injection point on the public generation path, and end-to-end replacement coverage.

Do not add a mapper per source XSD construct, native Rune `ChoiceMapper` output, CLI support, Maven-plugin integration, or namespace-driven package generation as incidental work.

## XML configuration and serialization runtime

The Rune model and XML configuration are separate outputs. XML configuration may consume mapping information but must not mutate the Rune model.

`XmlConfigurationIndex` is generation-scoped through `MappingSession`. It caches structured members, attributes, bases, element types, substitution groups, reference sources, and namespace-prefix decisions. Preserve that ownership; do not rebuild equivalent inspector traversals per member or use global caches across generation runs.

Namespace requirements:

- Qualified elements and attributes retain their XSD namespace URI.
- Unqualified local attributes remain unqualified.
- Global element and attribute references retain their own XML identity.
- Inherited and imported members retain the declaration namespace from which they came.
- Root namespace declarations belong only to document-root candidates.
- Nested, referenced, and substitution members must not receive duplicate root namespace maps.
- Prefix selection is deterministic and collision-free for a generation run. URI identity is contractual; lexical prefix spelling is not.

Runtime consumers load the generated `xml-config.json` into `RosettaXMLConfiguration` and register `XsdRuneModule`. The runtime supports the fixture-covered nested local and cross-namespace `xsi:type` serialization and deserialization paths. Root polymorphism remains outside the round-trip and generated-output validation contract. Do not weaken URI-level regression assertions into prefix-only or local-name-only checks.

Mixed-union serialization and deserialization use configuration order and generated builder reflection where invocation is necessary. Do not restore reflection-order-dependent member selection.

## Resource publication and output behavior

`RuneModelFactory(ResourceSet)` snapshots built-in resources only at construction time: a resource is classified as built-in when it has a non-null URI and non-empty contents at that point. `getResourceSet()` returns the original mutable resource set. Callers own resources they add, remove, reorder, or mutate.

`saveResources(...)` writes each current, eligible non-built-in resource whose URI extension matches the selected `.rune` or `.rosetta` extension and whose contents are non-empty. It directly changes eligible resource URIs to absolute output paths before saving them in current resource-set order. A later save failure can leave earlier files written, later files missing, and resource URIs mutated. Retrying does not restore logical URIs.

Filename generation is based on `namespace.replace('.', '_')` plus the model category. Collisions are possible for distinct namespaces that normalize to the same filename and follow resource-set order/overwrite behavior. Do not add transactions, staging directories, temporary `.staging` paths, or altered publication semantics without explicit scope. Keep the `saveResources` method name and the direct JSON configuration writer path.

## Verified boundaries and known gaps

Use `documentation/docs/capability-matrix.md` for the public classification of schema shapes. Preserve its distinction between verified, partial, pending, and unsupported behavior.

Current boundaries include:

- XSD 1.1 is unsupported.
- Open content (`xs:any`, `xs:anyAttribute`), identity constraints, `xs:all`, chameleon namespaces, `xs:redefine`, and `xs:override` are outside the verified contract.
- Root polymorphism, `xsi:nil`/nillable handling, exact nested prefix spelling, exact child-order fidelity, and complete invalid-instance coverage are not established.
- Effective local `elementFormDefault`, `attributeFormDefault`, and per-member `form` qualification remain pending. The disabled pending assertions in `LocalFormQualificationIntegrationTest` are intentional.
- Choice and repeated-particle behavior is fixture-scoped. Do not present a flattened Rune choice as a complete XSD content-model implementation.
- A complex type and a group with the same qualified source name can collide when they are registered as Rune types. Keep declaration-role indexes separate and do not repair that collision with local-name merging; any changed behavior needs an explicit design and focused coverage.

Do not paper over a missing behavior by silently widening a mapping, using a same-local-name declaration from another namespace, or weakening a round-trip assertion. Add focused fixtures before expanding a support claim.

## Tests and verification

The Maven lifecycle is split intentionally:

```text
mvn test
mvn verify
```

Surefire excludes `*IntegrationTest` and `*RegressionTest`. Failsafe runs public integration tests and a separate focused `*RegressionTest` execution. `mvn verify` is therefore required for the complete parser, generation, compilation, validation, and round-trip gate.

Use `mvn test` for intermediate component verification. When implementation behavior changes, run `mvn test` first and `mvn verify` afterward. If a task explicitly asks only for `mvn test`, do not run `mvn verify`.

`AbstractRuneIntegrationTest` must construct parsers through `XsdParserJaxpFactory.defaultFactory()` so integration scenarios exercise the same fixed parser policy as consumers.

Add focused tests for changes to parser construction, inspector identity resolution, required dependencies, mapper routing, XML configuration, or runtime serialization. Namespace and serialization changes require the relevant integration or regression scenario in addition to component tests. Do not weaken assertions to hide namespace, declaration-identity, or XML-publication regressions.

## Documentation and agent-guide maintenance

Use source code, `pom.xml`, tests, generated artifacts, and current integration behavior to verify public claims. Correct documentation when those sources disagree.

The documentation index is `documentation/README.md`; every document beneath `documentation/` must include a `[← Back to Index](../README.md)` link. `documentation/docs/issue-reporting.md` must link to `.github/ISSUE_TEMPLATE/bug_report.yml`. Choose the level of detail and Markdown structure that best serves the document's readers; no fixed concision or line-folding rule applies.

Agent guidance may include the technical depth, detail, and formatting needed to preserve correct implementation behavior. Keep `AGENTS.md` limited to durable, implementation-backed rules, ownership boundaries, verification requirements, known compatibility behavior, and explicit out-of-scope decisions. Do not convert speculative designs, historical reviews, or unresolved proposals into current requirements.

For documentation-only work, verify links, examples, terminology, and `git diff --check`. For implementation changes, report the test results and any remaining documented gap.

## Scope and approval boundaries

This guide records project rules; it does not authorize new features or external integrations. Get explicit task scope before changing public mapper APIs, inspection contracts, parser import resolution, workload limits, generated-output publication, capability claims, or public security claims.

Preserve unrelated working-tree changes. Use `apply_patch` for edits. Update public documentation when a change alters the user-visible API or a documented support boundary.
