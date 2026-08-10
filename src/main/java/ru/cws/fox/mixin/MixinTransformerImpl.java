/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ru.cws.fox.mixin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.ISyntheticClassRegistry;
import org.spongepowered.asm.transformers.MixinClassReader;
import ru.cws.fox.Fox;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformPhase;
import ru.cws.fox.clazz.TransformerService;

public final class MixinTransformerImpl implements TransformerService {
    private IMixinTransformerFactory transformerFactory;
    private IMixinTransformer transformer;
    private ISyntheticClassRegistry registry;

    public void offer(final @NotNull IMixinTransformerFactory factory) {
        this.transformerFactory = factory;
    }

    @Override
    public void prepare(@NotNull FoxTransformer transformer) {
        if(this.transformerFactory == null) throw new IllegalStateException("Transformer factory is not available!");
        this.transformer = this.transformerFactory.createTransformer();
        this.registry = this.transformer.getExtensions().getSyntheticClassRegistry();
    }

    @Override
    public int priority(@NotNull FoxTransformer transformer, @NotNull TransformPhase phase) {
        if (phase == TransformPhase.MIXIN)
            return -1;
        return 50;
    }

    @Override
    public boolean shouldTransform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node) {
        return true;
    }

    @Override
    public @Nullable ClassNode transform(@NotNull FoxTransformer transformer, @NotNull Type type, @NotNull ClassNode node, @NotNull TransformPhase phase) throws Throwable {
        if (this.shouldGenerateClass(type))
            return this.generateClass(type, node) ? node : null;
        return this.transformer.transformClass(MixinEnvironment.getCurrentEnvironment(), type.getClassName(), node) ? node : null;
    }

    public @NotNull ClassNode classNode(@NotNull FoxTransformer transformer, @NotNull String canonicalName, @NotNull String internalName, byte[] input, int readerFlags) throws ClassNotFoundException {
        if (input.length != 0) {
            ClassNode node = new ClassNode(Fox.ASM_VERSION);
            new MixinClassReader(input, canonicalName).accept(node, readerFlags);
            return node;
        }

        Type type = Type.getObjectType(internalName);
        if (this.shouldGenerateClass(type)) {
            ClassNode node = new ClassNode(Fox.ASM_VERSION);
            if (this.generateClass(type, node))
                return node;
        }

        throw new ClassNotFoundException(canonicalName);
    }

    private boolean shouldGenerateClass(final @NotNull Type type) {
        return this.registry.findSyntheticClass(type.getClassName()) != null;
    }

    private boolean generateClass(final @NotNull Type type, final @NotNull ClassNode node) {
        return this.transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), type.getClassName(), node);
    }
}
