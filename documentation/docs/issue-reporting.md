# Issue Reporting

Before opening an issue, check the [capability matrix](./capability-matrix.md). A passing fixture proves only its exercised schema shape. Use the project's [bug report form](../../.github/ISSUE_TEMPLATE/bug_report.yml) for defects.

## Include a complete reproducer

For a schema issue, provide the smallest entry-point XSD and every required import or include. Preserve the relevant `targetNamespace`, prefixes, and `type`, `base`, `ref`, or `substitutionGroup` attributes. Include a representative XML instance for a runtime serialization issue.

For a Java or build issue, include the smallest reproducible example, the command or API call, the complete exception cause chain, and relevant generated Rune or `xml-config.json` excerpts. Do not include credentials, secrets, or confidential schemas.

## State the expected result

Identify the affected stage: parsing, qualified-reference resolution, model generation, generated-model compilation, XML configuration, source-schema validation, deserialization, serialization, or round trip. State whether the expected result is generated output, successful XML handling, rejection, or a diagnostic.

Report namespace URI and local name, rather than treating a prefix as declaration identity. Note the parser factory, namespace-to-package mappings, selected model extension, and whether the XML validates against the source schema. Screenshots alone are not reproducible.

[← Back to Index](../README.md)
