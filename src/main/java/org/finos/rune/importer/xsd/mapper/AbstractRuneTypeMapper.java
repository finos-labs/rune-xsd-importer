package org.finos.rune.importer.xsd.mapper;

import com.regnosys.rosetta.rosetta.RosettaFactory;
import com.regnosys.rosetta.rosetta.RosettaDefinable;
import com.regnosys.rosetta.rosetta.RosettaRootElement;
import com.regnosys.rosetta.rosetta.RosettaType;
import com.regnosys.rosetta.rosetta.expression.ExpressionFactory;
import com.regnosys.rosetta.rosetta.simple.SimpleFactory;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.context.MappingContext;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.resolution.RuneIdentifierNamingUtils;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Defines the contract and shared infrastructure for mapping XSD components to Rune Model elements.
 * <p>
 * Follows a strict two-pass lifecycle to handle circular dependencies:
 * <ol>
 * <li><b>Register:</b> Create the empty "shell" objects (names only) and register them.</li>
 * <li><b>Populate:</b> Fill in the content (fields, values) using the registry to resolve references.</li>
 * </ol>
 *
 * @param <T> The type of Rune element produced (e.g., Data, RosettaEnumeration).
 */
public abstract class AbstractRuneTypeMapper<T extends RosettaRootElement> {

    protected static final RosettaFactory runeFactory = RosettaFactory.eINSTANCE;
    protected static final SimpleFactory simpleFactory = SimpleFactory.eINSTANCE;
    protected static final ExpressionFactory expressionFactory = ExpressionFactory.eINSTANCE;

    private final MappingSession session;

    protected AbstractRuneTypeMapper(MappingSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    final MappingContext mappingContext(
            XsdParserInspector inspector,
            String namespace,
            RuneTypeRegistry registry) {
        return MappingContext.of(
                inspector,
                namespace,
                registry,
                session);
    }

    protected final MappingSession mappingSession() {
        return session;
    }

    /**
     * Creates and registers a Rune declaration shell using its generated type name.
     */
    protected final <R extends RosettaType & RosettaDefinable> void registerShell(
            Supplier<R> factory,
            String xsdName,
            String definition,
            MappingContext context,
            List<R> registered) {
        registerShell(
                factory,
                xsdName,
                definition,
                RuneIdentifierNamingUtils.toTypeCase(xsdName),
                context,
                registered);
    }

    /**
     * Creates and registers a Rune declaration shell using an explicit generated type name.
     */
    protected final <R extends RosettaType & RosettaDefinable> void registerShell(
            Supplier<R> factory,
            String xsdName,
            String definition,
            String targetName,
            MappingContext context,
            List<R> registered) {
        R shell = factory.get();
        RegistryKey sourceKey = RegistryKey.of(context.namespace(), xsdName);
        String registeredTargetName = context.typeNameIndex().register(sourceKey, targetName, false);
        shell.setName(registeredTargetName);
        shell.setDefinition(definition + ": " + xsdName);
        context.registry().register(sourceKey, shell);
        registered.add(shell);
    }

    /**
     * Phase 1: Creation & Registration.
     * Iterates XSD definitions, creates the Rune object (Name/Definition only),
     * registers it, and returns the list for immediate addition to the Model.
     */
    public abstract List<T> register(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry);

    /**
     * Phase 2: Content Population.
     * Iterates XSD definitions again, looks up the previously created Rune object in the registry,
     * and fills in its details (attributes, enum values, supertypes).
     */
    public abstract void populate(XsdParserInspector inspector, String namespace, RuneTypeRegistry registry);
}
