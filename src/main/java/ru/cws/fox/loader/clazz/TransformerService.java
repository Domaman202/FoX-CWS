package ru.cws.fox.loader.clazz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public interface TransformerService {
    void prepare(@NotNull FoxTransformer transformer);
    int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase);
    boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node);
    @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable;
}
