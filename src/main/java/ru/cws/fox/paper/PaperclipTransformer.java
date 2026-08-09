package ru.cws.fox.paper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformPhase;
import ru.cws.fox.clazz.TransformerService;

import static org.objectweb.asm.Opcodes.*;

public class PaperclipTransformer implements TransformerService {
    @Override
    public void prepare(@NotNull FoxTransformer transformer) {

    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        return 0;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        return node.name.equals("io/papermc/paperclip/Paperclip");
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable {
        if (phase != TransformPhase.INITIALIZE)
            return null;
        transformer.removeTransformer(PaperclipTransformer.class);
        {
            MethodNode methodVisitor = node.methods.stream().filter(it -> it.name.equals("main")).findFirst().orElseThrow();
            methodVisitor.instructions.clear();
            methodVisitor.tryCatchBlocks.clear();
            methodVisitor.visitCode();
            methodVisitor.visitLdcInsn("");
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/String");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", true);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/nio/file/Path", "toAbsolutePath", "()Ljava/nio/file/Path;", true);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/nio/file/Path", "toString", "()Ljava/lang/String;", true);
            methodVisitor.visitLdcInsn("!");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
            Label label0 = new Label();
            methodVisitor.visitJumpInsn(IFEQ, label0);
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
            methodVisitor.visitLdcInsn("Paperclip may not run in a directory containing '!'. Please rename the affected folder.");
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "exit", "(I)V", false);
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLdcInsn(Type.getType("Lio/papermc/paperclip/Paperclip;"));
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
            methodVisitor.visitTypeInsn(CHECKCAST, "ru/cws/fox/clazz/FoxClassLoader");
            methodVisitor.visitVarInsn(ASTORE, 1);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "io/papermc/paperclip/Paperclip", "setupClasspath", "()[Ljava/net/URL;", false);
            methodVisitor.visitVarInsn(ASTORE, 2);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitInsn(ARRAYLENGTH);
            methodVisitor.visitVarInsn(ISTORE, 3);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitVarInsn(ISTORE, 4);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitVarInsn(ILOAD, 4);
            methodVisitor.visitVarInsn(ILOAD, 3);
            Label label2 = new Label();
            methodVisitor.visitJumpInsn(IF_ICMPGE, label2);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitVarInsn(ILOAD, 4);
            methodVisitor.visitInsn(AALOAD);
            methodVisitor.visitVarInsn(ASTORE, 5);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitVarInsn(ALOAD, 5);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "ru/cws/fox/clazz/FoxClassLoader", "addTransformationPath", "(Ljava/net/URL;)V", false);
            methodVisitor.visitIincInsn(4, 1);
            methodVisitor.visitJumpInsn(GOTO, label1);
            methodVisitor.visitLabel(label2);
            methodVisitor.visitMethodInsn(INVOKESTATIC, "io/papermc/paperclip/Paperclip", "findMainClass", "()Ljava/lang/String;", false);
            methodVisitor.visitVarInsn(ASTORE, 2);
            methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), new Object[]{"Starting \u0001"});
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            methodVisitor.visitTypeInsn(NEW, "java/lang/Thread");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInvokeDynamicInsn("run", "(Ljava/lang/String;Lru/cws/fox/clazz/FoxClassLoader;[Ljava/lang/String;)Ljava/lang/Runnable;", new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), Type.getType("()V"), new Handle(H_INVOKESTATIC, "io/papermc/paperclip/Paperclip", "lambda$main$0", "(Ljava/lang/String;Lru/cws/fox/clazz/FoxClassLoader;[Ljava/lang/String;)V", false), Type.getType("()V"));
            methodVisitor.visitLdcInsn("ServerMain");
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Thread", "<init>", "(Ljava/lang/Runnable;Ljava/lang/String;)V", false);
            methodVisitor.visitVarInsn(ASTORE, 3);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "setContextClassLoader", "(Ljava/lang/ClassLoader;)V", false);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "start", "()V", false);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(5, 6);
            methodVisitor.visitEnd();
        }
        {
            MethodNode methodVisitor = node.methods.stream().filter(it -> it.name.equals("lambda$main$0")).findFirst().orElseThrow();
            methodVisitor.desc = "(Ljava/lang/String;Lru/cws/fox/clazz/FoxClassLoader;[Ljava/lang/String;)V";
        }
        {
            MethodNode methodVisitor = node.methods.stream().filter(it -> it.name.equals("setupClasspath")).findFirst().orElseThrow();
            for (int i = 0; i < methodVisitor.instructions.size(); i++) {
                AbstractInsnNode abstractInsnNode = methodVisitor.instructions.get(i);
                if (abstractInsnNode instanceof MethodInsnNode methodInsnNode) {
                    if (methodInsnNode.name.equals("exit") && methodInsnNode.owner.equals("java/lang/System")) {
                        methodInsnNode.owner = "ru/cws/fox/paper/PaperclipWrapper";
                        methodInsnNode.name = "exit";
                        break;
                    }
                }
            }
        }
        return node;
    }
}
