package space.vectrix.ignite;

import org.jetbrains.annotations.NotNull;
import ru.cws.fox.loader.Fox;
import space.vectrix.ignite.mod.Mods;

public class Ignite {
    public static @NotNull Mods mods() {
        return Fox.IGNITE_MODS_ENGINE;
    }
}
