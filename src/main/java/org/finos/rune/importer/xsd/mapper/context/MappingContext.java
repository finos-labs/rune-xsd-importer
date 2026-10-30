package org.finos.rune.importer.xsd.mapper.context;

import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.generation.ConditionMapper;
import org.finos.rune.importer.xsd.mapper.mapping.SimpleTypeBinding;
import org.finos.rune.importer.xsd.mapper.mapping.TypeNameIndex;
import org.finos.rune.importer.xsd.mapper.resolution.TypeReferenceResolver;
import org.finos.rune.importer.xsd.mapper.serialization.XmlConfigurationMapper;
import org.finos.rune.importer.xsd.mapper.serialization.XmlConfigurationIndex;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;

import java.util.Objects;

/**
 * Internal context shared by one mapper operation.
 *
 * <p>The context is shared with the responsibility-oriented mapper subpackages
 * and keeps the source inspector, registry, condition mapper, and output
 * mappers for the generation pipeline.</p>
 */
public final class MappingContext {

    private final XsdParserInspector inspector;
    private final String namespace;
    private final RuneTypeRegistry registry;
    private final MappingSession session;
    private final ConditionMapper conditionMapper;
    private final TypeReferenceResolver typeResolver;
    private final XmlConfigurationMapper xmlConfigurationMapper;
    private final XmlConfigurationIndex xmlConfigurationIndex;

    private MappingContext(
            XsdParserInspector inspector,
            String namespace,
            RuneTypeRegistry registry,
            MappingSession session) {
        this.inspector = inspector;
        this.namespace = namespace;
        this.registry = registry;
        this.session = session;
        this.conditionMapper = new ConditionMapper();
        this.typeResolver = registry == null ? null : new TypeReferenceResolver(inspector, registry);
        this.xmlConfigurationMapper = new XmlConfigurationMapper();
        this.xmlConfigurationIndex = inspector == null ? null : session.xmlConfigurationIndex(inspector);
    }

    public static MappingContext of(
            XsdParserInspector inspector,
            String namespace,
            RuneTypeRegistry registry,
            MappingSession session) {
        return new MappingContext(inspector, namespace, registry, session);
    }

    public XsdParserInspector inspector() {
        return inspector;
    }

    public String namespace() {
        return namespace;
    }

    public RuneTypeRegistry registry() {
        return registry;
    }

    public TypeReferenceResolver typeResolver() {
        return Objects.requireNonNull(typeResolver, "type resolver requires a registry");
    }

    public XmlConfigurationMapper xmlConfigurationMapper() {
        return xmlConfigurationMapper;
    }

    public XmlConfigurationIndex xmlConfigurationIndex() {
        return Objects.requireNonNull(xmlConfigurationIndex, "XML configuration index requires an inspector");
    }

    public ConditionMapper conditionMapper() {
        return conditionMapper;
    }

    public SimpleTypeBinding simpleTypeBinding(String sourceName) {
        return session.simpleTypeBinding(
                org.finos.rune.importer.xsd.registry.RegistryKey.of(namespace, sourceName),
                Objects.requireNonNull(inspector, "simple type routing requires an inspector"));
    }

    public TypeNameIndex typeNameIndex() {
        return session.typeNameIndex();
    }

    public MappingSession mappingSession() {
        return session;
    }
}
