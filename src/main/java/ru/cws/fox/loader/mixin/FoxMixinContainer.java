package ru.cws.fox.loader.mixin;


import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;

import java.nio.file.Path;
import java.util.Map;

public final class FoxMixinContainer extends ContainerHandleVirtual {
    public FoxMixinContainer(final @NotNull String name) {
        super(name);
    }

    public void addResource(final @NotNull String name, final @NotNull Path path) {
        this.add(new ResourceContainer(name, path));
    }

    public void addResource(final Map.@NotNull Entry<String, Path> entry) {
        this.add(new ResourceContainer(entry.getKey(), entry.getValue()));
    }

    @Override
    public String toString() {
        return "FoxMixinContainer{name=" + this.getName() + "}";
    }

    private static class ResourceContainer extends ContainerHandleURI {
        private final String name;
        private final Path path;

        public ResourceContainer(final @NotNull String name, final @NotNull Path path) {
            super(path.toUri());

            this.name = name;
            this.path = path;
        }

        public @NotNull String name() {
            return this.name;
        }

        public @NotNull Path path() {
            return this.path;
        }

        @Override
        public @NotNull String toString() {
            return "ResourceContainer{name=" + this.name + ", path=" + this.path + "}";
        }
    }
}
