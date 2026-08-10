package ru.cws.fox.loader.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.*;
import net.fabricmc.loader.impl.discovery.*;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.jetbrains.annotations.VisibleForTesting;
import ru.cws.fox.loader.Fox;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FabricLoaderImpl implements FabricLoader {
    private final Map<String, ModContainerImpl> modMap = new HashMap<>();
    private List<ModCandidateImpl> modCandidates;
    private List<ModContainerImpl> mods = new ArrayList<>();

    private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
    private final EntrypointStorage entrypointStorage = new EntrypointStorage();
    private final ClassTweaker classTweaker = ClassTweaker.newInstance();
    public final MappingConfiguration mappingConfiguration = new MappingConfiguration();
    private MappingResolver mappingResolver;

    private final ObjectShare objectShare = new ObjectShareImpl();

    public boolean hasEntrypoints(String key) {
        return this.entrypointStorage.hasEntrypoints(key);
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return this.entrypointStorage.getEntrypoints(key, type);
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return this.entrypointStorage.getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        if (!hasEntrypoints(key)) {
            Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", key);
            return;
        }

        RuntimeException exception = null;
        Collection<EntrypointContainer<T>> entrypoints = this.getEntrypointContainers(key, type);

        Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", key);

        for (EntrypointContainer<T> container : entrypoints) {
            try {
                invoker.accept(container.getEntrypoint());
            } catch (Throwable t) {
                exception = ExceptionUtil.gatherExceptions(t,
                        exception,
                        exc -> new RuntimeException(String.format("Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
                                key, container.getProvider().getMetadata().getId(), container.getDefinition()),
                                exc));
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public ObjectShare getObjectShare() {
        return this.objectShare;
    }

    public ClassTweaker getClassTweaker() {
        return this.classTweaker;
    }

    @Override
    public MappingResolver getMappingResolver() {
        if (this.mappingResolver == null) {
            MappingConfiguration config = this.mappingConfiguration;
            String runtimeNamespace = config.getRuntimeNamespace();

            this.mappingResolver = new LazyMappingResolver(() -> new MappingResolverImpl(config.getMappings(), runtimeNamespace), runtimeNamespace);
        }

        return this.mappingResolver;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(this.modMap.get(id));
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return Collections.unmodifiableList(this.mods);
    }

    public List<ModContainerImpl> getModsInternal() {
        return this.mods;
    }

    @Override
    public boolean isModLoaded(String id) {
        return this.modMap.containsKey(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return Fox.IS_DEBUG;
    }

    @Override
    public EnvType getEnvironmentType() {
        return EnvType.SERVER;
    }

    @Override
    public String getRawGameVersion() {
        return Fox.MINECRAFT_VERSION;
    }

    @Override
    public Object getGameInstance() {
        return null;
    }

    public GameProvider getGameProvider() {
        return FoxGameProvider.INSTANCE;
    }


    @Override
    public Path getGameDir() {
        return Path.of(".");
    }

    @Override
    public File getGameDirectory() {
        return new File(".");
    }

    @Override
    public Path getConfigDir() {
        return Path.of("./config");
    }

    @Override
    public File getConfigDirectory() {
        return new File("./config");
    }

    private Path getModsDirectory0() {
        return Path.of("./mods/fabric");
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return new String[0];
    }

    public void load() {
        try {
            setup();
        } catch (ModResolutionException exception) {
            if (exception.getCause() == null) {
                throw FormattedException.ofLocalized("exception.incompatible", exception.getMessage());
            } else {
                throw FormattedException.ofLocalized("exception.incompatible", exception);
            }
        }
    }

    private void setup() throws ModResolutionException {
        VersionOverrides versionOverrides = new VersionOverrides();
        DependencyOverrides depOverrides = new DependencyOverrides(this.getConfigDir());

        // discover mods

        ModDiscoverer discoverer = new ModDiscoverer(versionOverrides, depOverrides);
        discoverer.addCandidateFinder(new ClasspathModCandidateFinder());
        discoverer.addCandidateFinder(new DirectoryModCandidateFinder(this.getModsDirectory0(), true));
        discoverer.addCandidateFinder(new ArgumentModCandidateFinder(true));

        Map<String, Set<ModCandidateImpl>> envDisabledMods = new HashMap<>();
        modCandidates = discoverer.discoverMods(this, envDisabledMods);

        // dump version and dependency overrides info

        if (!versionOverrides.getAffectedModIds().isEmpty()) {
            Log.info(LogCategory.GENERAL, "Versions overridden for %s", String.join(", ", versionOverrides.getAffectedModIds()));
        }

        if (!depOverrides.getAffectedModIds().isEmpty()) {
            Log.info(LogCategory.GENERAL, "Dependencies overridden for %s", String.join(", ", depOverrides.getAffectedModIds()));
        }

        // resolve mods

        modCandidates = ModResolver.resolve(modCandidates, getEnvironmentType(), envDisabledMods);

        dumpModList(modCandidates);
        dumpNonFabricMods(discoverer.getNonFabricMods());

        Path cacheDir = this.getGameDir().resolve(".fabric");
        Path outputdir = cacheDir.resolve("processedMods");

        // runtime mod remapping

        FoxRuntimeModRemapper.remap(modCandidates, cacheDir.resolve("tmp"), outputdir);

        // shuffle mods in-dev to reduce the risk of false order reliance, apply late load requests

        if (isDevelopmentEnvironment() && !SystemProperties.isSet(SystemProperties.DEBUG_DISABLE_MOD_SHUFFLE)) {
            Collections.shuffle(modCandidates);
        }

        String modsToLoadLate = System.getProperty(SystemProperties.DEBUG_LOAD_LATE);

        if (modsToLoadLate != null) {
            for (String modId : modsToLoadLate.split(",")) {
                for (Iterator<ModCandidateImpl> it = modCandidates.iterator(); it.hasNext(); ) {
                    ModCandidateImpl mod = it.next();

                    if (mod.getId().equals(modId)) {
                        it.remove();
                        modCandidates.add(mod);
                        break;
                    }
                }
            }
        }

        // add mods

        for (ModCandidateImpl mod : modCandidates) {
            if (!mod.hasPath() && !mod.isBuiltin()) {
                try {
                    mod.setPaths(Collections.singletonList(mod.copyToDir(outputdir, false)));
                } catch (IOException e) {
                    throw new RuntimeException("Error extracting mod "+mod, e);
                }
            }

            addMod(mod);
        }

        modCandidates = null;
    }

    @VisibleForTesting
    public void dumpNonFabricMods(List<Path> nonFabricMods) {
        if (nonFabricMods.isEmpty()) return;
        StringBuilder outputText = new StringBuilder();

        for (Path nonFabricMod : nonFabricMods) {
            outputText.append("\n\t- ").append(nonFabricMod.getFileName());
        }

        int modsCount = nonFabricMods.size();
        Log.warn(LogCategory.GENERAL, "Found %d non-fabric mod%s:%s", modsCount, modsCount != 1 ? "s" : "", outputText);
    }

    private void dumpModList(List<ModCandidateImpl> mods) {
        StringBuilder modListText = new StringBuilder();

        boolean[] lastItemOfNestLevel = new boolean[mods.size()];
        List<ModCandidateImpl> topLevelMods = mods.stream()
                .filter(mod -> mod.getParentMods().isEmpty())
                .collect(Collectors.toList());
        int topLevelModsCount = topLevelMods.size();

        for (int i = 0; i < topLevelModsCount; i++) {
            boolean lastItem = i == topLevelModsCount - 1;

            if (lastItem) lastItemOfNestLevel[0] = true;

            dumpModList0(topLevelMods.get(i), modListText, 0, lastItemOfNestLevel);
        }

        int modsCount = mods.size();
        Log.info(LogCategory.GENERAL, "Loading %d mod%s:%n%s", modsCount, modsCount != 1 ? "s" : "", modListText);
    }

    private void dumpModList0(ModCandidateImpl mod, StringBuilder log, int nestLevel, boolean[] lastItemOfNestLevel) {
        if (log.length() > 0) log.append('\n');

        for (int depth = 0; depth < nestLevel; depth++) {
            log.append(depth == 0 ? "\t" : lastItemOfNestLevel[depth] ? "     " : "   | ");
        }

        log.append(nestLevel == 0 ? "\t" : "  ");
        log.append(nestLevel == 0 ? "-" : lastItemOfNestLevel[nestLevel] ? " \\--" : " |--");
        log.append(' ');
        log.append(mod.getId());
        log.append(' ');
        log.append(mod.getVersion().getFriendlyString());

        List<ModCandidateImpl> nestedMods = new ArrayList<>(mod.getNestedMods());
        nestedMods.sort(Comparator.comparing(nestedMod -> nestedMod.getMetadata().getId()));

        if (!nestedMods.isEmpty()) {
            Iterator<ModCandidateImpl> iterator = nestedMods.iterator();
            ModCandidateImpl nestedMod;
            boolean lastItem;

            while (iterator.hasNext()) {
                nestedMod = iterator.next();
                lastItem = !iterator.hasNext();

                if (lastItem) lastItemOfNestLevel[nestLevel+1] = true;

                dumpModList0(nestedMod, log, nestLevel + 1, lastItemOfNestLevel);

                if (lastItem) lastItemOfNestLevel[nestLevel+1] = false;
            }
        }
    }

    public void finishModLoading() {
        // add mods to classpath
        // TODO: This can probably be made safer, but that's a long-term goal
        for (ModContainerImpl mod : mods) {
            if (!mod.getMetadata().getId().equals("fabricloader") && !mod.getMetadata().getType().equals("builtin")) {
                for (Path path : mod.getCodeSourcePaths()) {
                    Fox.FOX_CLASS_LOADER.addTransformationPath(path);
                }
            }
        }

        setupLanguageAdapters();
        setupMods();
    }

    public void loadClassTweakers() {
        ClassTweakerReader ctReader = ClassTweakerReader.create(classTweaker);

        for (net.fabricmc.loader.api.ModContainer modContainer : getAllMods()) {
            LoaderModMetadata modMetadata = (LoaderModMetadata) modContainer.getMetadata();
            String location = modMetadata.getClassTweaker();
            if (location == null) continue;

            Path path = modContainer.findPath(location).orElse(null);
            if (path == null) throw new RuntimeException(String.format("Missing classTweaker file %s from mod %s", location, modContainer.getMetadata().getId()));

            try (BufferedReader reader = Files.newBufferedReader(path)) {
                ctReader.read(reader, this.mappingConfiguration.getRuntimeNamespace());
            } catch (Exception e) {
                throw new RuntimeException("Failed to read classTweaker file from mod " + modMetadata.getId(), e);
            }
        }
    }

    private void setupLanguageAdapters() {
        adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

        for (ModContainerImpl mod : mods) {
            // add language adapters
            for (Map.Entry<String, String> laEntry : mod.getInfo().getLanguageAdapterDefinitions().entrySet()) {
                if (adapterMap.containsKey(laEntry.getKey())) {
                    throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
                }

                try {
                    adapterMap.put(laEntry.getKey(), (LanguageAdapter) Class.forName(laEntry.getValue(), true, Fox.FOX_CLASS_LOADER).getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
                }
            }
        }
    }

    private void setupMods() {
        for (ModContainerImpl mod : mods) {
            try {
                for (String in : mod.getInfo().getOldInitializers()) {
                    String adapter = mod.getInfo().getOldStyleLanguageAdapter();
                    entrypointStorage.addDeprecated(mod, adapter, in);
                }

                for (String key : mod.getInfo().getEntrypointKeys()) {
                    for (EntrypointMetadata in : mod.getInfo().getEntrypoints(key)) {
                        entrypointStorage.add(mod, key, in, adapterMap);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.getInfo().getName(), mod.getOrigin()), e);
            }
        }
    }

    private void addMod(ModCandidateImpl candidate) throws ModResolutionException {
        ModContainerImpl container = new ModContainerImpl(candidate);
        mods.add(container);
        modMap.put(candidate.getId(), container);

        for (String provides : candidate.getProvides()) {
            modMap.put(provides, container);
        }
    }
}
