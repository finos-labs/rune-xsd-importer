package org.finos.rune.importer.xsd.core;

import org.finos.rune.importer.xsd.api.RuneGeneratorConfig;
import org.finos.rune.importer.xsd.inspector.*;
import org.finos.rune.importer.xsd.mapper.resolution.MappingResolutionException;
import org.finos.rune.importer.xsd.registry.RegistryKey;
import org.finos.rune.importer.xsd.registry.RuneBuiltinRegistry;

import java.util.*;

/**
 * Validates the qualified references required by an import before mapper
 * registration can publish Rune model shells.
 *
 * <p>Same-namespace recursive type-member references are valid because the
 * mapper registers all type shells before it populates their members. Other
 * dependency cycles remain fail-closed.</p>
 */
public final class XsdDependencyValidator {

    private XsdDependencyValidator() {
    }

    public static void validate(XsdParserInspector inspector, RuneGeneratorConfig config) {
        Set<XsdDeclaration> declarations = inspector.getDeclaredDeclarations();
        List<XsdDependency> dependencies = inspector.getRequiredDependencies();
        Set<String> rootNamespaces = inspector.getLoadedNamespaces().stream()
                .filter(config::shouldGenerate)
                .collect(java.util.stream.Collectors.toSet());

        List<String> errors = new ArrayList<>();
        inspector.getUnsolvedReferences().forEach(reference ->
                errors.add("unresolved required reference '" + reference + "' reported by parser"));
        errors.addAll(validateGraph(declarations, dependencies, rootNamespaces));
        if (!errors.isEmpty()) {
            throw new MappingResolutionException(
                    "Dependency preflight failed:\n - " + String.join("\n - ", errors),
                    null,
                    "dependency preflight");
        }
    }

    /**
     * Performs qualified graph validation. Kept package-visible so core tests
     * can exercise the state machine independently from parser fixture setup.
     */
    static List<String> validateGraph(
            Set<XsdDeclaration> declarations,
            List<XsdDependency> dependencies,
            Set<String> rootNamespaces) {
        Map<XsdDeclaration, List<XsdDependency>> graph = new LinkedHashMap<>();
        for (XsdDeclaration declaration : declarations) {
            graph.put(declaration, new ArrayList<>());
        }
        for (XsdDependency dependency : dependencies) {
            graph.computeIfAbsent(dependency.source(), ignored -> new ArrayList<>()).add(dependency);
        }

        Map<XsdDeclaration, VisitState> states = new HashMap<>();
        List<XsdDeclaration> path = new ArrayList<>();
        Set<String> errors = new LinkedHashSet<>();

        List<XsdDeclaration> roots = graph.keySet().stream()
                .filter(declaration -> rootNamespaces.contains(declaration.key().namespace()))
                .toList();
        for (XsdDeclaration root : roots) {
            visit(root, graph, declarations, states, path, errors);
        }

        return List.copyOf(errors);
    }

    private static void visit(
            XsdDeclaration current,
            Map<XsdDeclaration, List<XsdDependency>> graph,
            Set<XsdDeclaration> declarations,
            Map<XsdDeclaration, VisitState> states,
            List<XsdDeclaration> path,
            Set<String> errors) {
        VisitState state = states.getOrDefault(current, VisitState.UNSEEN);
        if (state == VisitState.COMPLETE) return;
        if (state == VisitState.VISITING) {
            errors.add("dependency cycle: " + formatCycle(path, current));
            return;
        }

        states.put(current, VisitState.VISITING);
        path.add(current);
        for (XsdDependency dependency : graph.getOrDefault(current, List.of())) {
            RegistryKey target = RegistryKey.parse(dependency.targetQName());
            if (target == null) {
                errors.add("invalid required reference '" + dependency.targetQName() + "' from " + dependency.context());
                continue;
            }
            if (RuneBuiltinRegistry.primitiveFamily(target).isPresent()) {
                continue;
            }
            XsdDeclaration targetDeclaration = new XsdDeclaration(target, dependency.targetRole());
            if (!declarations.contains(targetDeclaration)) {
                List<XsdDeclarationRole> matchingRoles = declarations.stream()
                        .filter(declaration -> declaration.key().equals(target))
                        .map(XsdDeclaration::role)
                        .distinct()
                        .toList();
                if (!matchingRoles.isEmpty()) {
                    errors.add("wrongTargetType: reference '" + target + "' from " + dependency.context()
                            + " resolved to " + matchingRoles + "; expectedType=" + dependency.targetRole());
                } else {
                    errors.add("unresolved required reference '" + target + "' from " + dependency.context()
                            + "; expectedType=" + dependency.targetRole());
                }
                continue;
            }

            VisitState targetState = states.getOrDefault(targetDeclaration, VisitState.UNSEEN);
            if (targetState == VisitState.VISITING) {
                if (!isSupportedRecursiveTypeCycle(
                        path, targetDeclaration, graph, declarations, errors)) {
                    errors.add("dependency cycle: " + formatCycle(path, targetDeclaration));
                }
            } else if (targetState == VisitState.UNSEEN) {
                visit(targetDeclaration, graph, declarations, states, path, errors);
            }
        }
        path.removeLast();
        states.put(current, VisitState.COMPLETE);
    }

    private static boolean isSupportedRecursiveTypeCycle(
            List<XsdDeclaration> path,
            XsdDeclaration target,
            Map<XsdDeclaration, List<XsdDependency>> graph,
            Set<XsdDeclaration> declarations,
            Set<String> errors) {
        int start = path.indexOf(target);
        if (start < 0) {
            return false;
        }

        List<XsdDeclaration> cycle = path.subList(start, path.size());
        String namespace = cycle.get(0).key().namespace();
        if (cycle.stream().anyMatch(declaration ->
                declaration.role() != XsdDeclarationRole.TYPE
                        || !namespace.equals(declaration.key().namespace()))) {
            return false;
        }

        Set<XsdDeclaration> cycleDeclarations = Set.copyOf(cycle);
        for (XsdDeclaration source : cycleDeclarations) {
            for (XsdDependency dependency : graph.getOrDefault(source, List.of())) {
                RegistryKey dependencyTarget = RegistryKey.parse(dependency.targetQName());
                if (dependencyTarget == null) {
                    errors.add("invalid required reference '" + dependency.targetQName()
                            + "' from " + dependency.context());
                    return false;
                }
                if (RuneBuiltinRegistry.primitiveFamily(dependencyTarget).isPresent()) {
                    continue;
                }
                XsdDeclaration targetDeclaration = new XsdDeclaration(
                        dependencyTarget, dependency.targetRole());
                if (!declarations.contains(targetDeclaration)) {
                    List<XsdDeclarationRole> matchingRoles = declarations.stream()
                            .filter(declaration -> declaration.key().equals(dependencyTarget))
                            .map(XsdDeclaration::role)
                            .distinct()
                            .toList();
                    if (!matchingRoles.isEmpty()) {
                        errors.add("wrongTargetType: reference '" + dependencyTarget + "' from "
                                + dependency.context() + " resolved to " + matchingRoles
                                + "; expectedType=" + dependency.targetRole());
                    } else {
                        errors.add("unresolved required reference '" + dependencyTarget + "' from "
                                + dependency.context() + "; expectedType=" + dependency.targetRole());
                    }
                    return false;
                }
                if (cycleDeclarations.contains(targetDeclaration)
                        && dependency.type() != XsdDependencyType.TYPE_MEMBER_REFERENCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String formatCycle(List<XsdDeclaration> path, XsdDeclaration target) {
        int start = path.indexOf(target);
        List<XsdDeclaration> cycle = start >= 0 ? path.subList(start, path.size()) : path;
        List<String> names = new ArrayList<>(cycle.stream().map(XsdDeclaration::toString).toList());
        names.add(target.toString());
        return String.join(" -> ", names);
    }

    private enum VisitState {
        UNSEEN,
        VISITING,
        COMPLETE
    }
}
