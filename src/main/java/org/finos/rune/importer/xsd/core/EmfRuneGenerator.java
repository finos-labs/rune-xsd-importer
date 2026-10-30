package org.finos.rune.importer.xsd.core;

import com.regnosys.rosetta.rosetta.RosettaEnumeration;
import com.regnosys.rosetta.rosetta.RosettaModel;
import com.regnosys.rosetta.rosetta.RosettaTypeAlias;
import com.regnosys.rosetta.rosetta.simple.Data;
import com.rosetta.util.serialisation.RosettaXMLConfiguration;
import org.finos.rune.importer.xsd.api.RuneGenerator;
import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.api.XsdImporterConfig;
import org.finos.rune.importer.xsd.inspector.XsdParserInspector;
import org.finos.rune.importer.xsd.mapper.DataMapper;
import org.finos.rune.importer.xsd.mapper.EnumMapper;
import org.finos.rune.importer.xsd.mapper.TypeAliasMapper;
import org.finos.rune.importer.xsd.mapper.context.MappingSession;
import org.finos.rune.importer.xsd.mapper.serialization.XmlConfigurationJsonWriter;
import org.finos.rune.importer.xsd.registry.RuneTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * EMF-based implementation of the Rune Generation pipeline.
 * <p>
 * This generator builds an in-memory graph of EMF Objects (Eclipse Modeling Framework)
 * representing the Rune model. It relies on the {@link RuneModelFactory} to manage
 * the underlying Resources and the {@link RuneTypeRegistry} to resolve cross-references.
 */
public class EmfRuneGenerator implements RuneGenerator {

    private static final Logger log = LoggerFactory.getLogger(EmfRuneGenerator.class);

    private final RuneImportPostProcessor importProcessor = new RuneImportPostProcessor();

    private final RuneModelFactory modelFactory;
    private final RuneTypeRegistry typeRegistry;
    private final XmlConfigurationJsonWriter configWriter;
    private XsdImporterConfig activeImporterConfig = XsdImporterConfig.defaults();

    public EmfRuneGenerator(RuneModelFactory modelFactory, RuneTypeRegistry typeRegistry) {
        this.modelFactory = modelFactory;
        this.typeRegistry = typeRegistry;
        this.configWriter = new XmlConfigurationJsonWriter();
    }

    @Override
    public void generateModels(XsdParserInspector inspector, RuneGeneratorConfig config) {
        generateModels(inspector, config, XsdImporterConfig.defaults());
    }

    @Override
    public void generateModels(
            XsdParserInspector inspector,
            RuneGeneratorConfig config,
            XsdImporterConfig importerConfig) {
        activeImporterConfig = Objects.requireNonNull(importerConfig, "importerConfig");
        log.info("Starting Rune Model Generation...");

        // Validate the reference graph before registration so strict failures cannot publish partial output.
        XsdDependencyValidator.validate(inspector, config);

        List<RosettaModel> generatedModels = new ArrayList<>();
        MappingSession session = new MappingSession();
        EnumMapper enumMapper = new EnumMapper(session);
        DataMapper dataMapper = new DataMapper(session);
        TypeAliasMapper typeAliasMapper = new TypeAliasMapper(session);

        log.info("--- Executing Phase 1: Global Registration ---");
        for (String ns : inspector.getLoadedNamespaces()) {
            if (!config.shouldGenerate(ns)) continue;

            log.debug("Registering namespace: {}", ns);

            List<RosettaEnumeration> enums = enumMapper.register(inspector, ns, typeRegistry);
            List<RosettaTypeAlias> aliases = typeAliasMapper.register(inspector, ns, typeRegistry);
            List<Data> dataTypes = dataMapper.register(inspector, ns, typeRegistry);

            if (!enums.isEmpty()) {
                RosettaModel enumModel = modelFactory.createRosettaModel(
                        config.getRunePackage(ns),
                        RuneModelCategory.ENUM,
                        activeImporterConfig
                );
                enumModel.getElements().addAll(enums);
            }

            if (!dataTypes.isEmpty() || !aliases.isEmpty()) {
                RosettaModel typeModel = modelFactory.createRosettaModel(
                        config.getRunePackage(ns),
                        RuneModelCategory.TYPE,
                        activeImporterConfig
                );

                if (!dataTypes.isEmpty()) {
                    typeModel.getElements().addAll(dataTypes);
                }

                if (!aliases.isEmpty()) {
                    typeModel.getElements().addAll(aliases);
                }

                generatedModels.add(typeModel);
            }
        }

        log.info("--- Executing Phase 2: Global Population ---");
        for (String ns : inspector.getLoadedNamespaces()) {
            if (!config.shouldGenerate(ns)) continue;

            log.debug("Populating namespace: {}", ns);

            enumMapper.populate(inspector, ns, typeRegistry);
            typeAliasMapper.populate(inspector, ns, typeRegistry);
            dataMapper.populate(inspector, ns, typeRegistry);
        }

        log.info("--- Executing Phase 3: Import Resolution ---");
        for (RosettaModel model : generatedModels) {
            importProcessor.process(model);
        }

        log.info("Model Generation Complete (In-Memory).");
    }

    @Override
    public void saveRuneModels(Path outputRoot) {
        try {
            log.info("Saving Rune models to: {}", outputRoot);
            modelFactory.saveResources(outputRoot, activeImporterConfig);
        } catch (Exception e) {
            throw new RuntimeException("Fatal error writing Rune files", e);
        }
    }

    @Override
    public RosettaXMLConfiguration generateXmlConfiguration() {
        return new RosettaXMLConfiguration(typeRegistry.getXmlConfigs());
    }

    @Override
    public void saveXmlConfiguration(Path filePath) {
        try {
            RosettaXMLConfiguration config = generateXmlConfiguration();
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            configWriter.writeConfig(config, filePath);

        } catch (Exception e) {
            throw new RuntimeException("Fatal error writing JSON Configuration", e);
        }
    }
}
