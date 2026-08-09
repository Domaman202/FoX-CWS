package ru.cws.fox.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.launch.FabricMixinVersions;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.mappings.MixinIntermediaryDevRemapper;
import net.fabricmc.mappingio.tree.MappingTree;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;
import ru.cws.fox.Fox;

import java.util.*;

public class FabricMixinBootstrap {
    public static void init() {
        if (Fox.FABRIC_MODS_ENGINE.isDevelopmentEnvironment()) {
            MappingConfiguration config = Fox.FABRIC_MODS_ENGINE.mappingConfiguration;
            MappingTree mappings = config.getMappings();
            final String modNs = config.getDefaultModDistributionNamespace();
            String runtimeNs = config.getRuntimeNamespace();

            if (config.hasAnyMappings() && !modNs.equals(runtimeNs)) {
                List<String> namespaces = new ArrayList<>(mappings.getDstNamespaces());
                namespaces.add(mappings.getSrcNamespace());

                if (namespaces.contains(modNs) && namespaces.contains(runtimeNs)) {
                    System.setProperty("mixin.env.remapRefMap", "true");

                    try {
                        MixinIntermediaryDevRemapper remapper = new MixinIntermediaryDevRemapper(mappings, modNs, runtimeNs);
                        MixinEnvironment.getDefaultEnvironment().getRemappers().add(remapper);
                        Log.info(LogCategory.MIXIN, "Loaded Fabric development mappings for mixin remapper!");
                    } catch (Exception e) {
                        Log.error(LogCategory.MIXIN, "Fabric development environment setup error - the game will probably crash soon!", e);
                    }
                }
            }
        }

        Map<String, ModContainerImpl> configToModMap = new HashMap<>();

        for (ModContainerImpl mod : Fox.FABRIC_MODS_ENGINE.getModsInternal()) {
            for (String config : mod.getMetadata().getMixinConfigs(EnvType.SERVER)) {
                ModContainerImpl prev = configToModMap.putIfAbsent(config, mod);
                if (prev != null) throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s", config, prev.getMetadata().getId(), mod.getMetadata().getId()));

                try {
                    Mixins.addConfiguration(config);
                } catch (Throwable t) {
                    throw new RuntimeException(String.format("Error parsing or using Mixin config %s for mod %s", config, mod.getMetadata().getId()), t);
                }
            }
        }

        for (Config config : Mixins.getConfigs()) {
            ModContainerImpl mod = configToModMap.get(config.getName());
            if (mod == null) continue;
        }

        try {
            IMixinConfig.class.getMethod("decorate", String.class, Object.class);
            MixinConfigDecorator.apply(configToModMap);
        } catch (NoSuchMethodException e) {
            Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
        }
    }

    private static final class MixinConfigDecorator {
        static void apply(Map<String, ModContainerImpl> configToModMap) {
            for (Config rawConfig : Mixins.getConfigs()) {
                ModContainerImpl mod = configToModMap.get(rawConfig.getName());
                if (mod == null) continue;

                IMixinConfig config = rawConfig.getConfig();
                config.decorate(FabricUtil.KEY_MOD_ID, mod.getMetadata().getId());
                config.decorate(FabricUtil.KEY_COMPATIBILITY, getMixinCompat(mod));
            }
        }

        private static int getMixinCompat(ModContainerImpl mod) {
            // infer from loader dependency by determining the least relevant loader version the mod accepts
            // AND any loader deps

            List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

            for (ModDependency dep : mod.getMetadata().getDependencies()) {
                if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                    if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                        reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                    } else if (dep.getKind() == ModDependency.Kind.BREAKS) {
                        reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                    }
                }
            }

            if (reqIntervals.isEmpty()) throw new IllegalStateException("mod "+mod+" is incompatible with every loader version?"); // shouldn't get there

            Version minLoaderVersion = reqIntervals.get(0).getMin(); // it is sorted, to 0 has the absolute lower bound

            if (minLoaderVersion != null) { // has a lower bound
                for (FabricMixinVersions.LoaderMixinVersionEntry version : FabricMixinVersions.getVersions()) {
                    if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
                        return version.mixinVersion;
                    }
                }
            }

            return FabricUtil.COMPATIBILITY_0_9_2;
        }
    }
}
