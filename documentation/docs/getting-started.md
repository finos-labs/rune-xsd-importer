# Getting Started

The public interface is a Java API. Use Java 21 and Maven when building this repository. The importer generates in-memory Rune models, then writes Rune files and optional XML configuration to paths you choose.

Always construct the parser with `XsdParserJaxpFactory.defaultFactory()`. Its fixed policy disables unsafe XML features such as `DOCTYPE` and external entity resolution. Do not construct the third-party `XsdParser` directly.

## Build and verify

Run component tests from the repository root:

```sh
mvn test
```

Run the complete fixture and round-trip lifecycle:

```sh
mvn verify
```

Run one integration scenario with Failsafe:

```sh
mvn -Dit.test=NestedChoiceIntegrationTest -DfailIfNoTests=false failsafe:integration-test failsafe:verify
```

`mvn verify` is the compatibility gate for the documented fixtures. See [test coverage](./test-coverage.md) for its scope.

## Generate models

Configure every namespace that should produce a model. An unmapped namespace falls back to `org.finos.rune.unknown`; this is a compatibility fallback, not a valid package mapping.

```java
import org.finos.rune.importer.xsd.api.XsdImporterDefaultSetup;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.finos.rune.importer.xsd.core.EmfRuneGenerator;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.security.XsdParserJaxpFactory;
import org.xmlet.xsdparser.core.XsdParser;

import java.nio.file.Path;

public final class CustomRuneImporter {
    public static void main(String[] args) {
        Path inputXsd = Path.of("src/test/resources/org/finos/rune/xsd/integration/features/misc/composite-product.xsd"); //input XSD schema
        Path outputDir = Path.of("target/generated-custom-model"); // output directory

        String targetNs = "http://example.com/rune/complex";
        String runePackage = "com.example.rune";

        EmfRuneGenerator generator = XsdImporterDefaultSetup.setupInfrastructure();
        XsdParser parser = XsdParserJaxpFactory.defaultFactory().createParser(inputXsd);
        XsdParserInspector inspector = new XsdParserInspector(parser);
        RuneGeneratorConfig config = new RuneGeneratorConfig()
                .addTargetNamespace(targetNs)
                .addNamespaceMapping(targetNs, runePackage);
        XsdImporterConfig importerConfig = XsdImporterConfig.builder()
                .modelFileExtension(".rune")
                .build();
        generator.generateModels(inspector, config, importerConfig);
        generator.saveRuneModels(outputDir);
        generator.saveXmlConfiguration(outputDir.resolve("xml-config.json"));
    }
}
```

`XsdImporterConfig` defaults to `.rune`; `.rosetta` is the only other supported model extension. Create a new generator for each run and do not use one generator concurrently. The parser factory does not impose schema-size or graph-size limits, so the host application must supply its own resource controls where needed.

[← Back to Index](../README.md)
