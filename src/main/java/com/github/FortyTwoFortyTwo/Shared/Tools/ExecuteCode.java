package Tools;

import appender.CaptureLogsAppender;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
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
import java.security.SecureClassLoader;
import java.util.*;
import java.util.stream.Collectors;

public class ExecuteCode implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Executes a Java Code in Bukkit Minecraft Server, don't use working directories to assist yourself.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of(
                "className", stringSchema("Name of the class to call constructor without any arguments in generated code"),
                "code", stringSchema("Full code to compile and execute it, can only have one public class.")
        ));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String className = input.has("className") ? input.get("className").getAsString() : "";
        if (className.isEmpty())
            return Map.of("error", "Missing 'className' field");

        String code = input.has("code") ? input.get("code").getAsString() : "";
        if (code.isEmpty())
            return Map.of("error", "Missing 'code' field");

        System.out.println("Compiling and executing code:\n" + code);

        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Diagnostic to capture errors
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // Create a file manager
        JavaFileManager fileManager = new ClassFileManager(
                compiler.getStandardFileManager(diagnostics, null, null),
                getClass().getClassLoader()
        );

        // Create source code file object
        List<JavaFileObject> files = List.of(
                new CharSequenceJavaFileObject(className, code)
        );

        // Resolve the paper JAR to an absolute path using the working directory
        String workingDir = System.getProperty("user.dir");
        String absolutePaperJar = workingDir + File.separator + System.getProperty("java.class.path");

        // Get all URLs from Bukkit's URLClassLoader (includes all Paper's loaded JARs)
        URLClassLoader cl = (URLClassLoader) Bukkit.class.getClassLoader();
        String urlClasspath = Arrays.stream(cl.getURLs())
                .map(URL::getFile)
                .collect(Collectors.joining(File.pathSeparator));

        // Collect ALL jars from the libraries folder recursively
        File librariesDir = new File(MinecraftTools.plugin.getDataFolder().getParentFile().getParentFile(), "libraries");

        List<String> libraryJars = new ArrayList<>();
        collectJars(librariesDir, libraryJars);

        String fullClasspath = absolutePaperJar
                + File.pathSeparator + urlClasspath
                + File.pathSeparator + String.join(File.pathSeparator, libraryJars);

        List<String> options = List.of("-classpath", fullClasspath);

        // Compile
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, files
        );

        boolean success = task.call();

        if (success) {
            // Load and execute
            ClassLoader classLoader = fileManager.getClassLoader(null);
            Class<?> clazz;
            try {
                clazz = classLoader.loadClass(className);
            } catch (ClassNotFoundException e) {
                System.out.println(e.getMessage());
                return Map.of("success", false, "error", e.getMessage());
            }

            // Execute main method or instantiate
            try {
                CaptureLogsAppender capture = new CaptureLogsAppender();
                clazz.getDeclaredConstructor().newInstance();
                capture.end();

                return Map.of("success", true, "output", (Serializable) capture.getOutput());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                return Map.of("success", false, "error", e.getMessage());
            }
        } else {
            List<String> lines = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                lines.add(
                        String.format("%s:%d: %s: %s",
                                diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown",
                                diagnostic.getLineNumber(),
                                diagnostic.getKind().toString().toLowerCase(),
                                diagnostic.getMessage(null)
                                )
                );

                if (diagnostic.getLineNumber() > 0)
                    lines.add("  at column " + diagnostic.getColumnNumber());
            }

            System.out.println(String.join("\n", lines));
            return Map.of("success", false, "error", String.join("\n", lines));
        }
    }

    private void collectJars(File dir, List<String> result) {
        if (!dir.exists()) return;
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) collectJars(f, result);
            else if (f.getName().endsWith(".jar")) result.add(f.getAbsolutePath());
        }
    }
}

// Helper classes for in-memory compilation
class CharSequenceJavaFileObject extends SimpleJavaFileObject {
    private final CharSequence content;

    public CharSequenceJavaFileObject(String className, CharSequence content) {
        super(URI.create("string:///" + className.replace('.', '/') +
                Kind.SOURCE.extension), Kind.SOURCE);
        this.content = content;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}

class ClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, JavaClassObject> classObjects = new HashMap<>();
    private final ClassLoader parentLoader;

    protected ClassFileManager(StandardJavaFileManager fileManager, ClassLoader parentLoader) {
        super(fileManager);
        this.parentLoader = parentLoader;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className, JavaFileObject.Kind kind, FileObject sibling) {
        JavaClassObject classObject = new JavaClassObject(className, kind);
        classObjects.put(className, classObject);
        return classObject;
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        return new SecureClassLoader(parentLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                JavaClassObject classObject = classObjects.get(name);
                if (classObject != null) {
                    byte[] bytes = classObject.getBytes();
                    return super.defineClass(name, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        };
    }
}

class JavaClassObject extends SimpleJavaFileObject {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public JavaClassObject(String name, Kind kind) {
        super(URI.create("string:///" + name.replace('.', '/') +
                kind.extension), kind);
    }

    public byte[] getBytes() {
        return baos.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() {
        return baos;
    }
}