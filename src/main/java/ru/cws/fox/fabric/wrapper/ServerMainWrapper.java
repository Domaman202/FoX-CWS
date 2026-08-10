package ru.cws.fox.fabric.wrapper;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import ru.cws.fox.Fox;

public class ServerMainWrapper {
    public static void initFabricMods() {
        Fox.FABRIC_MODS_ENGINE.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);
        Fox.FABRIC_MODS_ENGINE.invokeEntrypoints("server", DedicatedServerModInitializer.class, DedicatedServerModInitializer::onInitializeServer);
    }
}
