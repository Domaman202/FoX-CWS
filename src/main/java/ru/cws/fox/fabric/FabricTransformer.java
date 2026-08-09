package ru.cws.fox.fabric;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinTransform;
import net.fabricmc.loader.impl.transformer.ClassStripper;
import net.fabricmc.loader.impl.transformer.EnvironmentStrippingData;
import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import ru.cws.fox.Fox;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformPhase;
import ru.cws.fox.clazz.TransformerService;

public final class FabricTransformer implements TransformerService {
    private static int modAccess(int access) {
        return (access & 0x7) != Opcodes.ACC_PRIVATE
                ? (access & (~0x7)) | Opcodes.ACC_PUBLIC
                : access;
    }

    private static boolean applyPackageAccessFix(ClassNode node) {
        boolean changed = false;

        int newAccess = modAccess(node.access);
        if (newAccess != node.access) {
            node.access = newAccess;
            changed = true;
        }

        for (FieldNode field : node.fields) {
            int newFieldAccess = modAccess(field.access);
            if (newFieldAccess != field.access) {
                field.access = newFieldAccess;
                changed = true;
            }
        }

        for (MethodNode method : node.methods) {
            int newMethodAccess = modAccess(method.access);
            if (newMethodAccess != method.access) {
                method.access = newMethodAccess;
                changed = true;
            }
        }

        for (InnerClassNode inner : node.innerClasses) {
            int newInnerAccess = modAccess(inner.access);
            if (newInnerAccess != inner.access) {
                inner.access = newInnerAccess;
                changed = true;
            }
        }

        return changed;
    }

    private static boolean applyClassStripping(ClassNode node, EnvironmentStrippingData stripData) {
        boolean changed = false;

        if (!stripData.getStripInterfaces().isEmpty()) {
            if (node.interfaces.removeIf(stripData.getStripInterfaces()::contains)) {
                changed = true;
            }
        }

        if (!stripData.getStripFields().isEmpty()) {
            if (node.fields.removeIf(field -> stripData.getStripFields().contains(field.name + field.desc))) {
                changed = true;
            }
        }

        if (!stripData.getStripMethods().isEmpty()) {
            if (node.methods.removeIf(method -> stripData.getStripMethods().contains(method.name + method.desc))) {
                changed = true;
            }
        }

        Collection<String> strippedFields = stripData.getStripFields();
        if (!strippedFields.isEmpty()) {
            for (MethodNode method : node.methods) {
                boolean isStaticInit = method.name.equals("<clinit>");
                boolean isInit = method.name.equals("<init>");
                if (!isStaticInit && !isInit) continue;

                InsnList insns = method.instructions;
                Set<AbstractInsnNode> toRemove = new HashSet<>();

                for (AbstractInsnNode insn : insns) {
                    if (insn.getType() != AbstractInsnNode.FIELD_INSN) continue;
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    int opcode = fieldInsn.getOpcode();
                    String key = fieldInsn.name + fieldInsn.desc;

                    if (!strippedFields.contains(key)) continue;

                    if (opcode == Opcodes.PUTSTATIC && isStaticInit) {
                        toRemove.add(fieldInsn);
                        AbstractInsnNode prev = fieldInsn.getPrevious();
                        if (prev != null && isLoadInsn(prev, fieldInsn.desc)) {
                            toRemove.add(prev);
                        }
                    } else if (opcode == Opcodes.PUTFIELD && isInit) {
                        toRemove.add(fieldInsn);
                        AbstractInsnNode prev = fieldInsn.getPrevious();
                        if (prev != null && isLoadInsn(prev, fieldInsn.desc)) {
                            toRemove.add(prev);
                            AbstractInsnNode prevPrev = prev.getPrevious();
                            if (prevPrev != null && prevPrev.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) prevPrev).var == 0) {
                                toRemove.add(prevPrev);
                            }
                        }
                    }
                }

                if (!toRemove.isEmpty()) {
                    for (AbstractInsnNode insn : toRemove) {
                        insns.remove(insn);
                    }
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean isLoadInsn(AbstractInsnNode insn, String descriptor) {
        int opcode = insn.getOpcode();
        Type type = Type.getType(descriptor);
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ILOAD;
            case Type.FLOAT:
                return opcode == Opcodes.FLOAD;
            case Type.LONG:
                return opcode == Opcodes.LLOAD;
            case Type.DOUBLE:
                return opcode == Opcodes.DLOAD;
            case Type.ARRAY:
            case Type.OBJECT:
                return opcode == Opcodes.ALOAD;
            default:
                return false;
        }
    }

    private static ClassNode transformWithClassTweaker(ClassNode node, String name, boolean transformAccess, boolean environmentStrip) {
        EnvironmentStrippingData stripData = null;
        if (environmentStrip) {
            stripData = new EnvironmentStrippingData(Fox.ASM_VERSION, EnvType.SERVER.toString());
            node.accept(stripData);
            if (stripData.stripEntireClass()) {
                throw new RuntimeException("Cannot load class " + name + " in environment type " + EnvType.SERVER);
            }
        }

        boolean hasChanges = false;
        ClassTweaker tweaker = Fox.FABRIC_MODS_ENGINE.getClassTweaker();

        if (!tweaker.getAllAccessWideners().isEmpty() ||
                !tweaker.getInjectedInterfaces(name).isEmpty() ||
                !tweaker.getEnumExtensions(name).isEmpty()) {
            hasChanges = true;
        }

        if (transformAccess) hasChanges = true;
        if (environmentStrip && stripData != null && !stripData.isEmpty()) hasChanges = true;

        if (!hasChanges) return null;

        ClassNode resultNode = new ClassNode(Fox.ASM_VERSION);
        ClassVisitor visitor = resultNode;
        visitor = tweaker.createClassVisitor(Fox.ASM_VERSION, visitor, null);

        if (transformAccess) {
            visitor = new PackageAccessFixer(Fox.ASM_VERSION, visitor);
        }

        if (environmentStrip && stripData != null && !stripData.isEmpty()) {
            visitor = new ClassStripper(Fox.ASM_VERSION, visitor,
                    stripData.getStripInterfaces(),
                    stripData.getStripFields(),
                    stripData.getStripMethods());
        }

        node.accept(visitor);
        return resultNode;
    }

    private static ClassNode transform(String name, ClassNode node) {
        Set<BuiltinTransform> transforms = Fox.FABRIC_MODS_ENGINE.getGameProvider().getBuiltinTransforms(name);
        boolean transformAccess = transforms.contains(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS)
                && Fox.FABRIC_MODS_ENGINE.mappingConfiguration.requiresPackageAccessHack();
        boolean environmentStrip = transforms.contains(BuiltinTransform.STRIP_ENVIRONMENT);
        boolean applyClassTweaker = transforms.contains(BuiltinTransform.CLASS_TWEAKS)
                && Fox.FABRIC_MODS_ENGINE.getClassTweaker().getTargets().contains(name.replace('.', '/'));

        if (!transformAccess && !environmentStrip && !applyClassTweaker) {
            return null;
        }

        if (applyClassTweaker) {
            return transformWithClassTweaker(node, name, transformAccess, environmentStrip);
        }

        boolean changed = false;

        if (environmentStrip) {
            EnvironmentStrippingData stripData = new EnvironmentStrippingData(Fox.ASM_VERSION, EnvType.SERVER.toString());
            node.accept(stripData);
            if (stripData.stripEntireClass()) {
                throw new RuntimeException("Cannot load class " + name + " in environment type " + EnvType.SERVER);
            }
            if (!stripData.isEmpty() && applyClassStripping(node, stripData)) {
                changed = true;
            }
        }

        if (transformAccess && applyPackageAccessFix(node)) {
            changed = true;
        }

        return changed ? node : null;
    }

    @Override
    public void prepare(@NotNull FoxTransformer transformer) {
    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        return 0;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        return true;
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) {
        return transform(node.name.replace('/', '.'), node);
    }
}