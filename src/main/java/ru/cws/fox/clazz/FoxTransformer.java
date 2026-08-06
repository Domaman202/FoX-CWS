package ru.cws.fox.clazz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.tinylog.Logger;
import ru.cws.fox.Fox;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class FoxTransformer {
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

        ClassNode node = new ClassNode(Opcodes.ASM9);

        Type type = Type.getObjectType(internalName);
        if (input.length > 0) {
            new ClassReader(input).accept(node, 0);
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

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    private List<TransformerService> order(final @NotNull TransformPhase phase) {
        return this.transformers
                .values()
                .stream()
                .filter(value -> value.priority(this, phase) != -1)
                .sorted(Comparator.comparingInt(service -> service.priority(this, phase)))
                .collect(Collectors.toList());
    }
}
