package ru.cws.fox.loader.paper.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import ru.cws.fox.loader.clazz.FoxTransformer;
import ru.cws.fox.loader.clazz.TransformPhase;
import ru.cws.fox.loader.clazz.TransformerService;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.ARETURN;

public class ReobfServerTransformer implements TransformerService {
    @Override
    public void prepare(@NotNull FoxTransformer transformer) {

    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        return 0;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        return node.name.equals("io/papermc/paper/pluginremap/ReobfServer");
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable {
        if (phase != TransformPhase.INITIALIZE)
            return null;
        transformer.removeTransformer(ReobfServerTransformer.class);
        {
            MethodNode methodVisitor = node.methods.stream().filter(it -> it.name.equals("serverJar")).findFirst().orElseThrow();
            methodVisitor.instructions.clear();
            methodVisitor.tryCatchBlocks.clear();
            methodVisitor.visitCode();
            methodVisitor.visitMethodInsn(INVOKESTATIC, "ru/cws/fox/loader/Fox", "getMinecraftJarPath", "()Ljava/nio/file/Path;", false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(1, 0);
            methodVisitor.visitEnd();
        }
        return node;
    }
}
