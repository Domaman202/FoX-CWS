package ru.cws.fox;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.tinylog.Logger;
import ru.cws.fox.clazz.FoxClassLoader;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.clazz.TransformerService;
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
    public static final FoxTransformer FOX_TRANSFORMER = new FoxTransformer();
    public static final FoxClassLoader FOX_CLASS_LOADER = new FoxClassLoader(FOX_TRANSFORMER);
    public static final ModsImpl IGNITE_MODS_ENGINE = new ModsImpl();

    public static void main(String[] args) throws Throwable {
        FOX_TRANSFORMER.addResourceExclusionFilter(resourceFilter());
        FOX_CLASS_LOADER.addTransformationFilter(packageFilter());

        Thread.currentThread().setContextClassLoader(FOX_CLASS_LOADER);

        loadMods();
        MixinBootstrap.init();
        prepareMods();
        completeMixinBootstrap();
        MixinExtrasBootstrap.init();

        initFoliaFiles();
        launchFolia(args);
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

        if (!checkFoliaGameJar()) {
            Logger.warn("Game jar not founded. Trying download...");
            System.setProperty("paperclip.patchonly", "true");
            FOX_CLASS_LOADER.loadClass("io.papermc.paperclip.Paperclip").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } else {
            FOX_CLASS_LOADER.addTransformationPath(new File("versions/1.21.11/folia-1.21.11.jar").toPath());
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

    private static boolean checkFoliaGameJar() {
        return new File("versions/1.21.11/folia-1.21.11.jar").exists();
    }

    private static void completeMixinBootstrap() throws Throwable {
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
    }

    private static void prepareMods() throws Throwable {
        IGNITE_MODS_ENGINE.resolveWideners(FOX_TRANSFORMER);
        IGNITE_MODS_ENGINE.resolveMixins();
    }

    private static void loadMods() throws Throwable {
        if (!checkFoliaGameJar()) {
            Logger.warn("Game jar not founded, skipping mod loading!");
            return;
        }

        if (IGNITE_MODS_ENGINE.locateResources()) {
            final Set<String> names = IGNITE_MODS_ENGINE
                    .resolveResources()
                    .stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            Logger.info("Found {} Ignit mod(s): {}", names.size(), String.join(", ", names));
        }
    }

    public static final String[] TRANSFORMATION_EXCLUDED_RESOURCES = {
            // Mixin
            "org/spongepowered/asm/"
    };

    public static final String[] TRANSFORMATION_EXCLUDED_PACKAGES = {
            // Launcher
            "ru.cws.fox.",

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
