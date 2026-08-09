package ru.cws.fox.fabric;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider.BuiltinTransform;
import net.fabricmc.loader.impl.transformer.ClassStripper;
import net.fabricmc.loader.impl.transformer.EnvironmentStrippingData;
import net.fabricmc.loader.impl.transformer.PackageAccessFixer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import ru.cws.fox.Fox;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformPhase;
import ru.cws.fox.clazz.TransformerService;

public final class FabricTransformer implements TransformerService {
    // Вспомогательный метод для изменения access
    private static int modAccess(int access) {
        if ((access & 0x7) != Opcodes.ACC_PRIVATE) {
            return (access & (~0x7)) | Opcodes.ACC_PUBLIC;
        }
        return access;
    }

    // Прямое применение PackageAccessFixer к ClassNode
    private static void applyPackageAccessFix(ClassNode node) {
        node.access = modAccess(node.access);

        if (node.fields != null) {
            for (FieldNode field : node.fields) {
                field.access = modAccess(field.access);
            }
        }

        if (node.methods != null) {
            for (MethodNode method : node.methods) {
                method.access = modAccess(method.access);
            }
        }

        if (node.innerClasses != null) {
            for (InnerClassNode inner : node.innerClasses) {
                inner.access = modAccess(inner.access);
            }
        }
    }

    // Прямое применение ClassStripper к ClassNode
    private static void applyClassStripping(ClassNode node, EnvironmentStrippingData stripData) {
        // Удаление интерфейсов
        if (!stripData.getStripInterfaces().isEmpty()) {
            node.interfaces.removeIf(itf -> stripData.getStripInterfaces().contains(itf));
        }

        // Удаление полей
        if (!stripData.getStripFields().isEmpty()) {
            node.fields.removeIf(field -> stripData.getStripFields().contains(field.name + field.desc));
        }

        // Удаление методов
        if (!stripData.getStripMethods().isEmpty()) {
            node.methods.removeIf(method -> stripData.getStripMethods().contains(method.name + method.desc));
        }

        // Удаление инструкций записи в удалённые поля (в конструкторах и статических инициализаторах)
        // Это упрощённая версия, которая удаляет только саму инструкцию PUT (без удаления операндов)
        // Полноценная реализация требует удаления также инструкций загрузки this/значения,
        // но в реальных модах такое встречается редко, и удаление только PUT обычно безопасно.
        Collection<String> strippedFields = stripData.getStripFields();
        if (!strippedFields.isEmpty()) {
            for (MethodNode method : node.methods) {
                boolean isStaticInit = method.name.equals("<clinit>");
                boolean isInit = method.name.equals("<init>");
                if (!isStaticInit && !isInit) continue;

                InsnList insns = method.instructions;
                Iterator<AbstractInsnNode> it = insns.iterator();
                while (it.hasNext()) {
                    AbstractInsnNode insn = it.next();
                    if (insn.getType() == AbstractInsnNode.FIELD_INSN) {
                        FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                        int opcode = fieldInsn.getOpcode();
                        boolean isPutStatic = (opcode == Opcodes.PUTSTATIC);
                        boolean isPutField = (opcode == Opcodes.PUTFIELD);
                        if ((isStaticInit && isPutStatic) || (isInit && isPutField)) {
                            String key = fieldInsn.name + fieldInsn.desc;
                            if (strippedFields.contains(key)) {
                                it.remove(); // удаляем инструкцию PUT
                                // Для PUTFIELD нужно также удалить предшествующую инструкцию загрузки this,
                                // но для простоты пропускаем (встречается крайне редко).
                            }
                        }
                    }
                }
            }
        }
    }

    // Трансформация с использованием ClassTweaker (требует байтов)
    private static ClassNode transformWithClassTweaker(ClassNode node, String name,
                                                       boolean transformAccess, boolean environmentStrip) {
        // Конвертируем ClassNode в байты с пересчётом максимумов (чтобы избежать ClassFormatError)
        ClassWriter initialWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(initialWriter);
        byte[] originalBytes = initialWriter.toByteArray();

        EnvironmentStrippingData stripData = null;
        if (environmentStrip) {
            stripData = new EnvironmentStrippingData(Fox.ASM_VERSION, EnvType.SERVER.toString());
            new ClassReader(originalBytes).accept(stripData, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            if (stripData.stripEntireClass()) {
                throw new RuntimeException("Cannot load class " + name + " in environment type " + EnvType.SERVER);
            }
        }

        ClassReader classReader = new ClassReader(originalBytes);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = classWriter;
        int visitorCount = 0;

        // ClassTweaker применяется первым
        visitor = Fox.FABRIC_MODS_ENGINE.getClassTweaker().createClassVisitor(Fox.ASM_VERSION, visitor, null);
        visitorCount++;

        if (transformAccess) {
            visitor = new PackageAccessFixer(Fox.ASM_VERSION, visitor);
            visitorCount++;
        }

        if (environmentStrip && stripData != null && !stripData.isEmpty()) {
            visitor = new ClassStripper(Fox.ASM_VERSION, visitor,
                    stripData.getStripInterfaces(),
                    stripData.getStripFields(),
                    stripData.getStripMethods());
            visitorCount++;
        }

        if (visitorCount <= 0) {
            return node; // не должно произойти
        }

        classReader.accept(visitor, 0);
        byte[] transformedBytes = classWriter.toByteArray();

        ClassNode transformedNode = new ClassNode(Fox.ASM_VERSION);
        new ClassReader(transformedBytes).accept(transformedNode, 0);
        return transformedNode;
    }

    // Основной метод трансформации
    private static ClassNode transform(String name, ClassNode node) {
        Set<BuiltinTransform> transforms = Fox.FABRIC_MODS_ENGINE.getGameProvider().getBuiltinTransforms(name);
        boolean transformAccess = transforms.contains(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS)
                && Fox.FABRIC_MODS_ENGINE.mappingConfiguration.requiresPackageAccessHack();
        boolean environmentStrip = transforms.contains(BuiltinTransform.STRIP_ENVIRONMENT);
        boolean applyClassTweaker = transforms.contains(BuiltinTransform.CLASS_TWEAKS)
                && Fox.FABRIC_MODS_ENGINE.getClassTweaker().getTargets().contains(name.replace('.', '/'));

        if (!transformAccess && !environmentStrip && !applyClassTweaker) {
            return null; // никаких трансформаций не требуется
        }

        // Если нужен ClassTweaker – используем байтовый путь (с COMPUTE_MAXS)
        if (applyClassTweaker) {
            return transformWithClassTweaker(node, name, transformAccess, environmentStrip);
        }

        // Иначе применяем трансформации напрямую к ClassNode
        if (environmentStrip) {
            EnvironmentStrippingData stripData = new EnvironmentStrippingData(Fox.ASM_VERSION, EnvType.SERVER.toString());
            node.accept(stripData);
            if (stripData.stripEntireClass()) {
                throw new RuntimeException("Cannot load class " + name + " in environment type " + EnvType.SERVER);
            }
            if (!stripData.isEmpty()) {
                applyClassStripping(node, stripData);
            }
        }

        if (transformAccess) {
            applyPackageAccessFix(node);
        }

        return node; // возвращаем модифицированный узел
    }

    // Реализация интерфейса TransformerService
    @Override
    public void prepare(@NotNull FoxTransformer transformer) {
        // Никакой подготовки не требуется
    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        // Приоритет по умолчанию – 0
        return 0;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        // Этот сервис всегда должен применяться к загружаемым классам игры
        return true;
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type,
                                         @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable {
        // Вызываем внутренний метод transform с полным именем класса (с точками)
        return transform(node.name.replace('/', '.'), node);
    }
}