package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.Shared.appender.CaptureLogsAppender;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.SecureClassLoader;
import java.util.*;

public class ExecuteCode implements MinecraftTool {

    // Built on first use, as walking the libraries folder is slow and it doesn't change while the server runs
    private String classpath;

    @Override
    public String getDescription() {
        return "Executes a Java Code in Bukkit Minecraft Server, don't use working directories to assist yourself. " +
                "Don't create backups or save the world before making changes.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of(
                "className", stringSchema("Name of the class to call constructor without any arguments in generated code"),
                "code", stringSchema("Full code to compile and execute it, can only have one public class, of which it's constructor class will get executed.")
        ));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String className = input.has("className") ? input.get("className").getAsString() : "";
        if (className.isEmpty())
            return Map.of("error", "Missing 'className' field");

        String code = input.has("code") ? input.get("code").getAsString() : "";
        if (code.isEmpty())
            return Map.of("error", "Missing 'code' field");

        MinecraftTools.plugin.getLogger().info("Compiling and executing code:\n" + code);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // Compiled classes are kept in memory, and loaded with this plugin's classes visible
        JavaFileManager fileManager = new ClassFileManager(
                compiler.getStandardFileManager(diagnostics, null, null),
                getClass().getClassLoader()
        );

        List<JavaFileObject> files = List.of(new SourceFile(className, code));
        List<String> options = List.of("-classpath", getClasspath());

        boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, files).call();
        if (!success) {
            List<String> lines = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                lines.add(String.format("%s:%d: %s: %s",
                        diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown",
                        diagnostic.getLineNumber(),
                        diagnostic.getKind().toString().toLowerCase(),
                        diagnostic.getMessage(null)));

                if (diagnostic.getLineNumber() > 0)
                    lines.add("  at column " + diagnostic.getColumnNumber());
            }

            return Map.of("success", false, "error", String.join("\n", lines));
        }

        Class<?> clazz;
        try {
            clazz = fileManager.getClassLoader(null).loadClass(className);
        } catch (ClassNotFoundException e) {
            return Map.of("success", false, "error", String.valueOf(e));
        }

        // Execute instance
        return runTask(() -> {
            CaptureLogsAppender capture = new CaptureLogsAppender();
            try {
                clazz.getDeclaredConstructor().newInstance();
                return Map.of("success", true, "output", (Serializable) capture.getOutput());
            } catch (InvocationTargetException e) {
                // Thrown by the generated code itself, the real error is the cause, and getMessage() is usually null
                return Map.of("success", false, "error", String.valueOf(e.getCause()), "output", (Serializable) capture.getOutput());
            } catch (ReflectiveOperationException e) {
                return Map.of("success", false, "error", String.valueOf(e));
            } finally {
                capture.end();
            }
        });
    }

    /** The server's own classpath, every JAR Paper has loaded, and every JAR in the libraries folder */
    private synchronized String getClasspath() {
        if (classpath != null)
            return classpath;

        List<String> entries = new ArrayList<>();

        // Relative entries, e.g. the paper JAR, are relative to the working directory
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.isBlank())
                entries.add(Path.of(entry).toAbsolutePath().toString());
        }

        if (Bukkit.class.getClassLoader() instanceof URLClassLoader loader) {
            for (URL url : loader.getURLs())
                entries.add(url.getFile());
        }

        collectJars(new File(MinecraftTools.plugin.getDataFolder().getParentFile().getParentFile(), "libraries"), entries);

        classpath = String.join(File.pathSeparator, entries);
        return classpath;
    }

    private static void collectJars(File dir, List<String> result) {
        File[] files = dir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory())
                collectJars(file, result);
            else if (file.getName().endsWith(".jar"))
                result.add(file.getAbsolutePath());
        }
    }

    /** Source code held in memory */
    private static class SourceFile extends SimpleJavaFileObject {
        private final CharSequence content;

        SourceFile(String className, CharSequence content) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    /** Compiled class held in memory */
    private static class ClassFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ClassFile(String name, Kind kind) {
            super(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind);
        }

        byte[] getBytes() {
            return baos.toByteArray();
        }

        @Override
        public OutputStream openOutputStream() {
            return baos;
        }
    }

    /** Writes compiled classes to memory, and loads them from there */
    private static class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ClassFile> classFiles = new HashMap<>();
        private final ClassLoader parentLoader;

        ClassFileManager(StandardJavaFileManager fileManager, ClassLoader parentLoader) {
            super(fileManager);
            this.parentLoader = parentLoader;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            ClassFile classFile = new ClassFile(className, kind);
            classFiles.put(className, classFile);
            return classFile;
        }

        @Override
        public ClassLoader getClassLoader(Location location) {
            return new SecureClassLoader(parentLoader) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    ClassFile classFile = classFiles.get(name);
                    if (classFile != null) {
                        byte[] bytes = classFile.getBytes();
                        return super.defineClass(name, bytes, 0, bytes.length);
                    }
                    return super.findClass(name);
                }
            };
        }
    }
}
