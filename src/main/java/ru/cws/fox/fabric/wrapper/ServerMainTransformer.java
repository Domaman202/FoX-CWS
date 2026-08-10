package ru.cws.fox.fabric.wrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformPhase;
import ru.cws.fox.clazz.TransformerService;

public class ServerMainTransformer implements TransformerService {
    @Override
    public void prepare(@NotNull FoxTransformer transformer) {

    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        return 0;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        return node.name.equals("net/minecraft/server/Main");
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable {
        if (phase != TransformPhase.INITIALIZE)
            return null;
        transformer.removeTransformer(ServerMainTransformer.class);
        MethodNode method = node.methods.stream().filter(it -> it.name.equals("main")).findFirst().orElseThrow();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode m && m.name.equals("validate") && m.owner.equals("net/minecraft/server/Bootstrap")) {
                method.instructions.insert(
                        insn,
                        new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "ru/cws/fox/fabric/wrapper/ServerMainWrapper",
                                "initFabricMods",
                                "()V",
                                false
                        )
                );
                break;
            }
        }
        return node;
    }
}
