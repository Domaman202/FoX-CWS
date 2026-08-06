package ru.cws.fox.mixin;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.ReEntranceLock;
import ru.cws.fox.Fox;
import ru.cws.fox.clazz.TransformPhase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

public final class FoxMixinService implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    private final ReEntranceLock lock;
    private final FoxMixinContainer container;

    public FoxMixinService() {
        this.lock = new ReEntranceLock(1);
        this.container = new FoxMixinContainer("FoX");
    }

    @Override
    public String getName() {
        return "FoX";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void prepare() {

    }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public void offer(final IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory) {
            MixinTransformerImpl transformer = Fox.FOX_TRANSFORMER.getTransformer(MixinTransformerImpl.class);
            if(transformer == null)
                return;
            transformer.offer((IMixinTransformerFactory) internal);
        }
    }

    @Override
    public void init() {

    }

    @Override
    public void beginPhase() {

    }

    @Override
    public void checkEnv(Object bootSource) {

    }

    @Override
    public String getSideName() {
        return "SERVER";
    }

    @Override
    public ILogger getLogger(String name) {
        return FoxMixinLogger.get(name);
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return this.lock;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return this.container;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return Collections.emptyList();
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Fox.FOX_CLASS_LOADER.getResourceAsStream(name);
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    public @NotNull URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public @NotNull Class<?> findClass(final @NotNull String name) throws ClassNotFoundException {
        return Class.forName(name, true, Fox.FOX_CLASS_LOADER);
    }

    @Override
    public @NotNull Class<?> findClass(final @NotNull String name, final boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Fox.FOX_CLASS_LOADER);
    }

    @Override
    public @NotNull Class<?> findAgentClass(final @NotNull String name, final boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, Fox.class.getClassLoader());
    }

    @Override
    public @NotNull ClassNode getClassNode(final @NotNull String name) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, true);
    }

    @Override
    public @NotNull ClassNode getClassNode(final @NotNull String name, final boolean runTransformers) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, runTransformers, 0);
    }

    @Override
    public @NotNull ClassNode getClassNode(final @NotNull String name, final boolean runTransformers, final int readerFlags) throws ClassNotFoundException, IOException {
        if(!runTransformers)
            throw new IllegalStateException("ClassNodes must always be provided transformed!");

        final MixinTransformerImpl mixinTransformer = Fox.FOX_TRANSFORMER.getTransformer(MixinTransformerImpl.class);
        if(mixinTransformer == null)
            throw new ClassNotFoundException("Mixin transformer is not available!");

        String canonicalName = name.replace('/', '.');
        String internalName = name.replace('.', '/');

        byte[] entry = Fox.FOX_CLASS_LOADER.getClassData(canonicalName, TransformPhase.MIXIN);
        if (entry == null)
            throw new ClassNotFoundException(canonicalName);

        return mixinTransformer.classNode(Fox.FOX_TRANSFORMER, canonicalName, internalName, entry, readerFlags);
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return Collections.emptyList();
    }

    @Override
    public void addTransformerExclusion(String name) {

    }

    @Override
    public void registerInvalidClass(String className) {

    }

    @Override
    public boolean isClassLoaded(String className) {
        return Fox.FOX_CLASS_LOADER.classIsLoaded(className);
    }

    @Override
    public String getClassRestrictions(String className) {
        return "";
    }
}
