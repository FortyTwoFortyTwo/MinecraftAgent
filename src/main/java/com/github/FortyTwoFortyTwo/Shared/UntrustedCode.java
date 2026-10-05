package com.github.FortyTwoFortyTwo.Shared;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What ExecuteCode allows the agent's code to use: the game's API and core Java classes,
 * but nothing that reaches the host (files, network, processes, reflection, class loading) or plugin configs, as this one holds API keys.
 * Checked on the source once javac has resolved it, so code using anything else never runs at all.
 */
public class UntrustedCode {

    // Classes, or packages ending in '.', that can be used, unless a longer entry in BLOCKED matches
    private static final List<String> ALLOWED = List.of(
            "org.bukkit.", "io.papermc.paper.", "com.destroystokyo.paper.", "net.kyori.adventure.", "net.md_5.bungee.api.",
            "java.util.", "java.time.", "java.math.", "java.text.",
            "java.lang.Object", "java.lang.String", "java.lang.StringBuilder", "java.lang.CharSequence",
            "java.lang.Math", "java.lang.StrictMath", "java.lang.Number", "java.lang.Integer", "java.lang.Long", "java.lang.Short",
            "java.lang.Byte", "java.lang.Double", "java.lang.Float", "java.lang.Character", "java.lang.Boolean", "java.lang.Void",
            "java.lang.Enum", "java.lang.Record", "java.lang.Iterable", "java.lang.Comparable", "java.lang.Runnable",
            "java.lang.AutoCloseable", "java.lang.Cloneable", "java.lang.Throwable",
            "java.lang.Override", "java.lang.Deprecated", "java.lang.SuppressWarnings", "java.lang.FunctionalInterface", "java.lang.SafeVarargs",
            // Needed to schedule tasks and register listeners, limited to the members in MEMBERS
            "org.bukkit.plugin.Plugin", "org.bukkit.plugin.PluginManager",
            "java.util.logging.Logger", "java.util.logging.Level",
            // Returns values to the agent, limited to put
            "com.github.FortyTwoFortyTwo.Shared.Output");

    // Classes, or packages ending in '.', inside ALLOWED that can't be used
    private static final List<String> BLOCKED = List.of(
            "org.bukkit.configuration.",    // plugin configs, including this plugin's API keys
            "org.bukkit.plugin.",           // loading and managing plugins, and their files
            "org.bukkit.craftbukkit.",      // server internals, which reach everything
            "org.bukkit.util.io.",          // Java deserialization
            "org.bukkit.UnsafeValues",
            "io.papermc.paper.plugin.",
            "java.util.logging.",           // FileHandler writes files
            "java.util.prefs.", "java.util.jar.", "java.util.zip.", "java.util.spi.",
            "java.util.ServiceLoader", "java.util.Scanner", "java.util.Formatter");

    // For these classes, only these members can be used
    private static final Map<String, Set<String>> MEMBERS = Map.of(
            "org.bukkit.plugin.Plugin", Set.of("getName", "getLogger", "getServer", "isEnabled"),
            "org.bukkit.plugin.PluginManager", Set.of("getPlugin", "getPlugins", "isPluginEnabled", "registerEvents", "callEvent"),
            "com.github.FortyTwoFortyTwo.Shared.Output", Set.of("put"));

    /** Collects into blocked every class or member the code uses that isn't allowed, as javac analyses each class. Call before the task runs. */
    public static void check(JavacTask task, Set<String> blocked) {
        Trees trees = Trees.instance(task);
        Elements elements = task.getElements();

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() != TaskEvent.Kind.ANALYZE || event.getTypeElement() == null)
                    return;

                TreePath path = trees.getPath(event.getTypeElement());
                if (path != null)
                    new ReferenceScanner(trees, elements, blocked).scan(path, null);
            }
        });
    }

    /** Whether the class, by binary name, can be used */
    private static boolean isAllowed(String name) {
        // Exceptions and errors are needed to throw and catch, and do nothing on their own
        if (name.startsWith("java.lang.") && name.indexOf('.', "java.lang.".length()) == -1) {
            String simple = name.substring("java.lang.".length()).split("\\$")[0];
            if (simple.endsWith("Exception") || simple.endsWith("Error"))
                return true;
        }

        // The most specific entry decides
        return Stream.concat(ALLOWED.stream(), BLOCKED.stream())
                .filter(entry -> matches(entry, name))
                .max(Comparator.comparingInt(String::length))
                .map(ALLOWED::contains)
                .orElse(false);
    }

    private static boolean matches(String entry, String name) {
        if (entry.endsWith("."))
            return name.startsWith(entry);

        // A class entry covers its nested classes too
        return name.equals(entry) || name.startsWith(entry + "$");
    }

    /** Checks everything the code refers to by name, as calls, field accesses and types are all names in the tree */
    private static class ReferenceScanner extends TreePathScanner<Void, Void> {

        // Names that don't belong to any class, so there's nothing to check
        private static final Set<ElementKind> NOT_MEMBERS = EnumSet.of(ElementKind.PACKAGE, ElementKind.MODULE, ElementKind.LOCAL_VARIABLE,
                ElementKind.PARAMETER, ElementKind.EXCEPTION_PARAMETER, ElementKind.RESOURCE_VARIABLE, ElementKind.BINDING_VARIABLE, ElementKind.TYPE_PARAMETER);

        private final Trees trees;
        private final Elements elements;
        private final Set<String> blocked;

        ReferenceScanner(Trees trees, Elements elements, Set<String> blocked) {
            this.trees = trees;
            this.elements = elements;
            this.blocked = blocked;
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void unused) {
            checkCurrent();
            return super.visitIdentifier(tree, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            checkCurrent();
            return super.visitMemberSelect(tree, unused);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
            checkCurrent();
            return super.visitMemberReference(tree, unused);
        }

        private void checkCurrent() {
            Element element = trees.getElement(getCurrentPath());
            if (element == null || NOT_MEMBERS.contains(element.getKind()))
                return;

            // Class literals, e.g. Zombie.class or int.class, can only be passed on, as Class itself can't be used
            if (element.getKind() == ElementKind.FIELD && element.getSimpleName().contentEquals("class"))
                return;

            TypeElement type = enclosingType(element);
            // Classes declared in the code itself are checked as they're scanned
            if (type == null || trees.getPath(type) != null)
                return;

            // javac's own types for arrays and primitives, e.g. array.length or int.class, aren't in any package
            TypeElement outermost = type;
            while (outermost.getEnclosingElement() instanceof TypeElement outer)
                outermost = outer;

            if (!(outermost.getEnclosingElement() instanceof PackageElement))
                return;

            String name = elements.getBinaryName(type).toString();
            if (!isAllowed(name)) {
                blocked.add(name);
                return;
            }

            Set<String> members = MEMBERS.get(name);
            if (members != null && element != type && !members.contains(element.getSimpleName().toString()))
                blocked.add(name + "." + element.getSimpleName());
        }

        private static TypeElement enclosingType(Element element) {
            while (element != null && !(element instanceof TypeElement))
                element = element.getEnclosingElement();

            return (TypeElement) element;
        }
    }
}
