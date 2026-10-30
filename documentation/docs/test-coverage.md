# Test Coverage

The test suite provides representative evidence, not complete XSD conformance coverage.

Run component tests with:

```sh
mvn test
```

Run the full verification lifecycle with:

```sh
mvn verify
```

Integration scenarios can validate source XML, generate Rune and `xml-config.json`, compile generated Rune models, validate generated XML, and round-trip representative XML. Each scenario selects the checks relevant to its schema shape.

Structural XML comparisons are scenario-specific. URI-level regression assertions cover selected qualified elements and attributes, root-only namespace declarations, and nested `xsi:type` values. They do not require exact prefix spelling. Root polymorphism round-trip and effective local `form` qualification remain pending.

For the current supported and unsupported schema shapes, see the [capability matrix](./capability-matrix.md). Active source and test code are the authority for exact test inventories and assertions.

[← Back to Index](../README.md)
