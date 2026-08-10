package ru.cws.fox.loader.fabric;

import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.ModDependencyImpl;
import net.fabricmc.loader.impl.util.Arguments;
import ru.cws.fox.loader.Fox;

import java.nio.file.Path;
import java.util.*;

public class FoxGameProvider implements GameProvider {
    public static final FoxGameProvider INSTANCE = new FoxGameProvider();

    @Override
    public String getGameId() {
        return "minecraft";
    }

    @Override
    public String getGameName() {
        return "Minecraft";
    }

    @Override
    public String getRawGameVersion() {
        return Fox.MINECRAFT_VERSION;
    }

    @Override
    public String getNormalizedGameVersion() {
        return Fox.MINECRAFT_VERSION;
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        BuiltinModMetadata.Builder metadata = new BuiltinModMetadata.Builder(getGameId(), getNormalizedGameVersion())
                .setName(getGameName());

        McVersion versionData = new McVersion.Builder().setNameAndRelease(Fox.MINECRAFT_VERSION).setClassVersion(Fox.ASM_VERSION).build();
        if (versionData.getClassVersion().isPresent()) {
            int version = versionData.getClassVersion().getAsInt() - 44;

            try {
                metadata.addDependency(new ModDependencyImpl(ModDependency.Kind.DEPENDS, "java", Collections.singletonList(String.format(Locale.ENGLISH, ">=%d", version))));
            } catch (VersionParsingException e) {
                throw new RuntimeException(e);
            }
        }

        return Collections.singletonList(new BuiltinMod(List.of(Fox.getMinecraftJarPath()), metadata.build()));
    }

    @Override
    public String getEntrypoint() {
        return null;
    }

    @Override
    public Path getLaunchDirectory() {
        return Fox.FABRIC_MODS_ENGINE.getGameDir();
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        return Set.of(BuiltinTransform.STRIP_ENVIRONMENT, BuiltinTransform.CLASS_TWEAKS);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void launch(ClassLoader loader) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Arguments getArguments() {
        return new Arguments();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return new String[0];
    }
}
