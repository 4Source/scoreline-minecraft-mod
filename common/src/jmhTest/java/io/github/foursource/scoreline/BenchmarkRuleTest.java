package io.github.foursource.scoreline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Checks on the compiled classes that the hot paths are benchmarked: every method of the measured classes that a hot root reaches must also be reached from a {@code @Benchmark} method. Code that only
 * a {@code @Setup} method reaches does not count, because setup is not measured.
 * <p>
 * The call graph follows direct calls, calls through lambdas and method references (the targets an {@code invokedynamic} names), and reads of a payload's {@code STREAM_CODEC} field: the codec is
 * built in the class's static initializer from method references to the payload's constructor and accessors, and Minecraft's codec code then calls them. A hot root is either {@code Class#method}
 * (every method of that name) or {@code Class#STREAM_CODEC}. The build passes the class folders, the coverage gate's exclusions and the hot roots as system properties.
 */
public class BenchmarkRuleTest {
    private static final String BENCHMARK = "Lorg/openjdk/jmh/annotations/Benchmark;";
    private static final String CODEC_FIELD = "STREAM_CODEC";
    private static final String STATIC_INIT = "<clinit>()V";

    /** Every scanned method by its key {@code owner.name(descriptor)}. */
    private static final Map<String, MethodInfo> METHODS = new HashMap<>();
    /** Every scanned class by its internal name. */
    private static final Map<String, ClassInfo> CLASSES = new HashMap<>();
    /** Classes of the main code (as opposed to benchmark classes). */
    private static final Set<String> MAIN_CLASSES = new HashSet<>();

    private static final class ClassInfo {
        String superName;
        final Set<String> fields = new HashSet<>();
        final List<String> methods = new ArrayList<>();
    }

    private static final class MethodInfo {
        /** {@code owner.name(descriptor)}, the key of this method in {@link #METHODS}. */
        final String key;
        final String owner;
        final String name;
        /** Bridge methods (compiler-made) and abstract methods (no code) have nothing to measure. */
        final boolean bridge;
        boolean benchmark;
        /** Calls as {@code owner.name(descriptor)}, before resolving to a declaring class. */
        final Set<String> calls = new LinkedHashSet<>();

        MethodInfo(String key, String owner, String name, boolean bridge) {
            this.key = key;
            this.owner = owner;
            this.name = name;
            this.bridge = bridge;
        }
    }

    @BeforeAll
    static void readClasses() {
        for (String folder : property("scoreline.mainClasses").split(File.pathSeparator)) {
            readFolder(Path.of(folder), true);
        }
        for (String folder : property("scoreline.benchmarkClasses").split(File.pathSeparator)) {
            readFolder(Path.of(folder), false);
        }
    }

    // T-153: the check demands a benchmark for every public method, or does not start from the hot roots
    // T-128, T-133, T-134, T-155 (checked with temporary changes): a hot method without a benchmark is named; setup code does not count; indirect calls and lambdas are followed
    @Test
    public void t153_everyHotMethodIsBenchmarked() {
        Set<String> hot = reach(hotRoots());
        Set<String> benchmarked = reach(METHODS.values().stream().filter(method -> method.benchmark).map(method -> method.key).toList());

        Set<String> missing = new TreeSet<>();
        for (String key : hot) {
            MethodInfo method = METHODS.get(key);
            if (MAIN_CLASSES.contains(method.owner) && measured(method.owner) && !method.bridge && !benchmarked.contains(key)) {
                missing.add(key.replace('/', '.'));
            }
        }
        // A misconfigured check that reaches nothing would always pass
        assertTrue(hot.contains("io/github/foursource/scoreline/net/RequestGate.admit(Ljava/util/UUID;Ljava/util/List;J)Ljava/util/Optional;"), "the hot paths do not reach RequestGate.admit: " + hot);
        assertEquals(Set.of(), missing, "methods on a hot path that no @Benchmark method reaches");
    }

    // T-154: a renamed root silently drops its whole path from the check
    @Test
    public void t154_everyHotRootExists() {
        List<String> unknown = new ArrayList<>();
        for (String root : property("scoreline.hotRoots").split(",")) {
            if (resolveRoot(root).isEmpty()) {
                unknown.add(root);
            }
        }
        assertEquals(List.of(), unknown, "hot roots that name no method");
    }

    private static List<String> hotRoots() {
        List<String> keys = new ArrayList<>();
        for (String root : property("scoreline.hotRoots").split(",")) {
            keys.addAll(resolveRoot(root));
        }
        return keys;
    }

    /** {@code a.b.C#name} → the keys of every method {@code name} of C; {@code a.b.C#STREAM_CODEC} → C's static initializer, if C has that field. */
    private static List<String> resolveRoot(String root) {
        int hash = root.indexOf('#');
        String owner = root.substring(0, hash).replace('.', '/');
        String name = root.substring(hash + 1);
        ClassInfo info = CLASSES.get(owner);
        if (info == null) {
            return List.of();
        }
        if (name.equals(CODEC_FIELD)) {
            return info.fields.contains(CODEC_FIELD) && METHODS.containsKey(owner + "." + STATIC_INIT) ? List.of(owner + "." + STATIC_INIT) : List.of();
        }
        return info.methods.stream().filter(key -> key.startsWith(owner + "." + name + "(")).toList();
    }

    /** Every scanned method reachable from the given ones, themselves included. */
    private static Set<String> reach(List<String> start) {
        Set<String> seen = new HashSet<>();
        Deque<String> work = new ArrayDeque<>(start);
        while (!work.isEmpty()) {
            String key = work.pop();
            if (!seen.add(key)) {
                continue;
            }
            for (String call : METHODS.get(key).calls) {
                String target = resolve(call);
                if (target != null && !seen.contains(target)) {
                    work.push(target);
                }
            }
        }
        return seen;
    }

    /** Finds the class that declares a called method, walking up the superclasses that were scanned; null for code outside Scoreline. */
    private static String resolve(String call) {
        int dot = call.indexOf('.');
        String owner = call.substring(0, dot);
        String method = call.substring(dot);
        while (owner != null && CLASSES.containsKey(owner)) {
            if (METHODS.containsKey(owner + method)) {
                return owner + method;
            }
            owner = CLASSES.get(owner).superName;
        }
        return null;
    }

    /** Not listed in the coverage gate's exclusions (a listed file excludes its nested classes too). */
    private static boolean measured(String owner) {
        String outer = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
        return Arrays.stream(property("scoreline.coverageExcludes").split(",")).noneMatch(outer::equals);
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("system property " + name + " not set; run through ./gradlew :common:jmhTest");
        }
        return value;
    }

    private static void readFolder(Path folder, boolean main) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    new ClassReader(in).accept(new Reader(main), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Records each class's superclass, fields and methods, and each method's calls. */
    private static final class Reader extends ClassVisitor {
        private final boolean main;
        private String owner;
        private ClassInfo info;

        Reader(boolean main) {
            super(Opcodes.ASM9);
            this.main = main;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            owner = name;
            info = new ClassInfo();
            info.superName = superName;
            CLASSES.put(name, info);
            if (main) {
                MAIN_CLASSES.add(name);
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            info.fields.add(name);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            String key = owner + "." + name + descriptor;
            MethodInfo method = new MethodInfo(key, owner, name, (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_ABSTRACT)) != 0);
            METHODS.put(key, method);
            info.methods.add(key);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (annotation.equals(BENCHMARK)) {
                        method.benchmark = true;
                    }
                    return null;
                }

                @Override
                public void visitMethodInsn(int opcode, String callOwner, String callName, String callDescriptor, boolean isInterface) {
                    method.calls.add(callOwner + "." + callName + callDescriptor);
                }

                @Override
                public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDescriptor) {
                    // Reading a payload's codec reaches what its static initializer wires up
                    if (opcode == Opcodes.GETSTATIC && fieldName.equals(CODEC_FIELD)) {
                        method.calls.add(fieldOwner + "." + STATIC_INIT);
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String indyName, String indyDescriptor, Handle bootstrap, Object... arguments) {
                    // Lambdas and method references: the bootstrap arguments name the method that runs
                    for (Object argument : arguments) {
                        if (argument instanceof Handle handle) {
                            method.calls.add(handle.getOwner() + "." + handle.getName() + handle.getDesc());
                        }
                    }
                }
            };
        }
    }
}
