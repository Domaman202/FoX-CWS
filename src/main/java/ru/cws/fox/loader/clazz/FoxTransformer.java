package ru.cws.fox.loader.clazz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.tinylog.Logger;
import ru.cws.fox.loader.Fox;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class FoxTransformer {
    private static final Map<String, ClassNode> COMPUTING_CLASSES = new ConcurrentHashMap<>();
    private final Map<Class<? extends TransformerService>, TransformerService> transformers = new IdentityHashMap<>();
    private static final Predicate<String> RESOURCE_EXCLUSION_FILTER_DEFAULT = path -> true;
    private Predicate<String> resourceExclusionFilter;

    public FoxTransformer() {
        for(TransformerService service : ServiceLoader.load(TransformerService.class, Fox.class.getClassLoader()))
            this.transformers.put(service.getClass(), service);
        this.resourceExclusionFilter = RESOURCE_EXCLUSION_FILTER_DEFAULT;

    }

    public void addResourceExclusionFilter(@NotNull Predicate<String> filter) {
        this.resourceExclusionFilter = this.resourceExclusionFilter == RESOURCE_EXCLUSION_FILTER_DEFAULT
                ? filter
                : this.resourceExclusionFilter.and(filter);
    }

    public <T extends TransformerService> @Nullable T getTransformer(@NotNull Class<T> transformer) {
        return transformer.cast(this.transformers.get(transformer));
    }

    public @NotNull Collection<TransformerService> getTransformers() {
        return Collections.unmodifiableCollection(this.transformers.values());
    }

    public <T extends TransformerService> T removeTransformer(Class<T> transformerService) {
        return (T) transformers.remove(transformerService);
    }

    public byte[] transform(@NotNull String name, byte[] input, @NotNull TransformPhase phase) {
        String internalName = name.replace('.', '/');

        if (!this.resourceExclusionFilter.test(internalName))
            return input;

        ClassNode node = new ClassNode(Fox.ASM_VERSION);

        Type type = Type.getObjectType(internalName);
        if (input.length > 0) {
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
        } else {
            node.name = type.getInternalName();
            node.version = MixinEnvironment.getCompatibilityLevel().getClassVersion();
            node.superName = "java/lang/Object";
        }

        boolean transformed = false;
        for (TransformerService service : this.order(phase)) {
            try {
                if (!service.shouldTransform(this, type, node))
                    continue;
                ClassNode transformedNode = service.transform(this, type, node, phase);
                if (transformedNode != null) {
                    node = transformedNode;
                    transformed = true;
                }
            } catch (Throwable t) {
                Logger.error(t, "Failed to transform {} with {}", type.getClassName(), service.getClass().getName());
            }
        }

        if (!transformed)
            return input;

        try {
            FoxClassWriter writer = new FoxClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            COMPUTING_CLASSES.put(name, node);
            node.accept(writer);
            COMPUTING_CLASSES.remove(name, node);
            return writer.toByteArray();
        } catch (TypeNotPresentException e) {
            Logger.warn("Fail to compute frames for {}", node.name);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        }
    }

    private List<TransformerService> order(final @NotNull TransformPhase phase) {
        return this.transformers
                .values()
                .stream()
                .filter(value -> value.priority(this, phase) != -1)
                .sorted(Comparator.comparingInt(service -> service.priority(this, phase)))
                .collect(Collectors.toList());
    }

    public static class FoxClassWriter extends ClassWriter {
        public FoxClassWriter(int flags) {
            super(flags);
        }

        public FoxClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        private static final Map<String, ClassNode> CLASS_CACHE = new ConcurrentHashMap<>();

        protected String getCommonSuperClass(final String type1, final String type2) {
            ClassNode classNode1 = getClassNode(type1);
            ClassNode classNode2 = getClassNode(type2);

            if (type1.equals(type2)) {
                return type1;
            }

            if (isAssignableFrom(classNode2, type1)) {
                return type1;
            }
            if (isAssignableFrom(classNode1, type2)) {
                return type2;
            }

            if ((classNode1.access & Opcodes.ACC_INTERFACE) != 0
                    || (classNode2.access & Opcodes.ACC_INTERFACE) != 0) {
                return "java/lang/Object";
            }

            String currentSuper = classNode1.superName;
            while (currentSuper != null) {
                if (isAssignableFrom(classNode2, currentSuper)) {
                    return currentSuper;
                }
                ClassNode superNode = getClassNode(currentSuper);
                currentSuper = superNode.superName;
            }
            return "java/lang/Object";
        }

        private ClassNode getClassNode(String type) {
            ClassNode cached = COMPUTING_CLASSES.get(type);
            if (cached != null) {
                return cached;
            }

            cached = CLASS_CACHE.get(type);
            if (cached != null) {
                return cached;
            }

            try {
                String resourcePath = type + ".class";
                InputStream is = Fox.FOX_CLASS_LOADER.getResourceAsStream(resourcePath);
                if (is == null)
                    throw new TypeNotPresentException(type, new ClassNotFoundException("Class not found: " + type));
                try (is) {
                    ClassReader reader = new ClassReader(is);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    CLASS_CACHE.put(type, node);
                    return node;
                }
            } catch (IOException e) {
                throw new TypeNotPresentException(type, e);
            }
        }

        private boolean isAssignableFrom(ClassNode sub, String superName) {
            if (sub.name.equals(superName)) {
                return true;
            }

            if (sub.superName != null && isAssignableFrom(getClassNode(sub.superName), superName)) {
                return true;
            }

            for (String iface : sub.interfaces) {
                if (isAssignableFrom(getClassNode(iface), superName)) {
                    return true;
                }
            }

            return false;
        }
    }
}
