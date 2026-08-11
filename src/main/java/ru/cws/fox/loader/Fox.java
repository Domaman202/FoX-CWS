package ru.cws.fox.loader;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.tinylog.Logger;
import ru.cws.fox.loader.clazz.FoxClassLoader;
import ru.cws.fox.loader.clazz.FoxTransformer;
import ru.cws.fox.loader.clazz.TransformerService;
import ru.cws.fox.loader.fabric.FabricLoaderImpl;
import ru.cws.fox.loader.fabric.FabricMixinBootstrap;
import space.vectrix.ignite.mod.ModsImpl;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Fox {
    public static final boolean IS_DEBUG = Boolean.parseBoolean(System.getProperty("fox.debug", "true"));

    public static final String VERSION = "1.0.0";
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final int ASM_VERSION = Opcodes.ASM9;

    public static final FoxTransformer FOX_TRANSFORMER = new FoxTransformer();
    public static final FoxClassLoader FOX_CLASS_LOADER = new FoxClassLoader(FOX_TRANSFORMER);

    public static final ModsImpl IGNITE_MODS_ENGINE = new ModsImpl();
    public static final FabricLoaderImpl FABRIC_MODS_ENGINE = new FabricLoaderImpl();

    public static void main(String[] args) throws Throwable {
        FOX_TRANSFORMER.addResourceExclusionFilter(resourceFilter());
        FOX_CLASS_LOADER.addTransformationFilter(packageFilter());

        Thread.currentThread().setContextClassLoader(FOX_CLASS_LOADER);

        if (!Files.exists(getMinecraftJarPath())) {
            initMixinBootstrap();
            completeMixinBootstrap();
            initFoliaFiles();
            return;
        }

        initFoliaFiles();
        loadIgnitMods();
        loadFabricMods();
        initMixinBootstrap();
        prepareIgnitMods();
        completeMixinBootstrap();
        initPreLaunchFabricMods();
        launchFolia(args);
    }

    public static Path getMinecraftJarPath() {
        return new File("versions/"+MINECRAFT_VERSION+"/folia-"+MINECRAFT_VERSION+".jar").toPath();
    }

    private static void launchFolia(String[] args) throws Throwable {
        Class.forName("io.papermc.paperclip.Paperclip", true, FOX_CLASS_LOADER)
                .getMethod("main", String[].class)
                .invoke(null, (Object) args);
    }

    private static void initFoliaFiles() throws Throwable {
        File foliaFile = new File("folia.jar");
        if (!foliaFile.exists() || foliaFile.isDirectory()) {
            Logger.error("File folia.jar not founded!");
            System.exit(1);
        }
        FOX_CLASS_LOADER.addTransformationPath(foliaFile.toPath());
        Logger.info("Founded folia.jar");

        if (!Files.exists(getMinecraftJarPath())) {
            Logger.warn("Game jar not founded. Trying download...");
            System.setProperty("paperclip.patchonly", "true");
            FOX_CLASS_LOADER.loadClass("io.papermc.paperclip.Paperclip").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } else {
            FOX_CLASS_LOADER.addTransformationPath(new File("versions/"+MINECRAFT_VERSION+"/folia-"+MINECRAFT_VERSION+".jar").toPath());
            Logger.info("Founded game jar");
        }

        File librariesDir = new File("libraries");
        if (librariesDir.isDirectory()) {
            try (var stream = Files.walk(librariesDir.toPath())) {
                for (Iterator<@NotNull Path> iter = stream.filter(Files::isRegularFile).filter(it -> it.getFileName().toString().endsWith(".jar")).iterator(); iter.hasNext(); ) {
                    FOX_CLASS_LOADER.addTransformationPath(iter.next());
                }
            }
            Logger.info("Libraries founded");
        }
    }

    private static void initMixinBootstrap() {
        MixinBootstrap.init();
        FabricMixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
    }

    private static void completeMixinBootstrap() {
        try {
            final Method method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            method.setAccessible(true);
            method.invoke(null, MixinEnvironment.Phase.INIT);
            method.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch(final Exception exception) {
            Logger.error(exception, "Failed to complete mixin bootstrap!");
        }

        for (TransformerService transformer : FOX_TRANSFORMER.getTransformers()) {
            transformer.prepare(FOX_TRANSFORMER);
        }

        MixinExtrasBootstrap.init();
    }

    private static void loadIgnitMods() {
        if (IGNITE_MODS_ENGINE.locateResources()) {
            final Set<String> names = IGNITE_MODS_ENGINE
                    .resolveResources()
                    .stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            Logger.info("Found {} Ignit mod(s): {}", names.size(), String.join(", ", names));
        }
    }

    private static void prepareIgnitMods() {
        IGNITE_MODS_ENGINE.resolveWideners(FOX_TRANSFORMER);
        IGNITE_MODS_ENGINE.resolveMixins();
    }

    private static void loadFabricMods() {
        FABRIC_MODS_ENGINE.load();
        FABRIC_MODS_ENGINE.finishModLoading();
        FABRIC_MODS_ENGINE.loadClassTweakers();
    }

    private static void initPreLaunchFabricMods() {
        Fox.FABRIC_MODS_ENGINE.invokeEntrypoints("preLaunch", PreLaunchEntrypoint.class, PreLaunchEntrypoint::onPreLaunch);
    }

    public static final String[] TRANSFORMATION_EXCLUDED_RESOURCES = {
            // Mixin
            "org/spongepowered/asm/"
    };

    public static final String[] TRANSFORMATION_EXCLUDED_PACKAGES = {
            // Launcher
            "ru.cws.fox.loader.",

            // Mixin
            "org.spongepowered.asm.",
            "com.llamalad7.mixinextras.",

            // Access Widener
            "net.fabricmc.accesswidener.",
    };

    private static @NotNull Predicate<String> packageFilter() {
        return name -> {
            for (final String test : TRANSFORMATION_EXCLUDED_PACKAGES)
                if(name.startsWith(test))
                    return false;
            return true;
        };
    }

    private static @NotNull Predicate<String> resourceFilter() {
        return path -> {
            for (final String test : TRANSFORMATION_EXCLUDED_RESOURCES)
                if (path.startsWith(test))
                    return false;
            return true;
        };
    }
}
