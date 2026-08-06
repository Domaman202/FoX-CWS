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
package space.vectrix.ignite.mod;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import org.tinylog.Logger;
import ru.cws.fox.Fox;
import ru.cws.fox.clazz.FoxTransformer;
import ru.cws.fox.mixin.AccessTransformerImpl;
import ru.cws.fox.mixin.FoxMixinContainer;
import ru.cws.fox.mixin.FoxMixinService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Represents the mod loading engine.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class ModsImpl implements Mods {
  public static final Gson GSON = new Gson();
  private final ModResourceLocator resourceLocator = new ModResourceLocator();
  private final ModResourceLoader resourceLoader = new ModResourceLoader();
  private final Map<String, ModContainer> containersByConfig = new HashMap<>();
  private final Map<String, ModContainer> containers = new HashMap<>();
  private final List<ModResource> resources = new ArrayList<>();

  /**
   * Creates a new mod loading engine.
   *
   * @since 1.0.0
   */
  public ModsImpl() {
  }

  @Override
  public boolean loaded(final @NotNull String id) {
    return this.containers.containsKey(id);
  }

  @Override
  public @NotNull Optional<ModContainer> container(final @NotNull String id) {
    return Optional.ofNullable(this.containers.get(id));
  }

  @Override
  public @NotNull List<ModResource> resources() {
    return Collections.unmodifiableList(this.resources);
  }

  @Override
  public @NotNull Collection<ModContainer> containers() {
    return Collections.unmodifiableCollection(this.containers.values());
  }

  /**
   * Returns {@code true} if any mod resources were located, otherwise returns
   * {@code false}.
   *
   * @return whether any mod resources were located
   * @since 1.0.0
   */
  public boolean locateResources() {
    return this.resources.addAll(this.resourceLocator.locateResources());
  }

  /**
   * Returns a list of resolved mod container paths.
   *
   * @return resolved mod container paths
   * @since 1.0.0
   */
  public @NotNull List<Map.Entry<String, Path>> resolveResources() {
    final List<Map.Entry<String, Path>> targetResources = new ArrayList<>();
    for(final ModContainerImpl container : this.resourceLoader.loadResources(this)) {
      Fox.FOX_CLASS_LOADER.addTransformationPath(container.resource().path());

      this.containers.put(container.id(), container);

      final String prettyIdentifier = String.format("%s@%s", container.id(), container.version());
      targetResources.add(new AbstractMap.SimpleEntry<>(prettyIdentifier, container.resource().path()));
    }

    return targetResources;
  }

  /**
   * Resolves the access wideners provided by the mods.
   *
   * @param transformer the transformer
   * @since 1.0.0
   */
  public void resolveWideners(final @NotNull FoxTransformer transformer) {
    final AccessTransformerImpl accessTransformer = transformer.getTransformer(AccessTransformerImpl.class);
    if(accessTransformer == null) return;

    for(final ModContainer container : this.containers()) {
      final ModResource resource = container.resource();

      final List<String> wideners = ((ModContainerImpl) container).config().wideners();
      if(wideners != null && !wideners.isEmpty()) {
        for(final String widener : wideners) {
          //noinspection resource
          final Path path = resource.fileSystem().getPath(widener);
          try {
            Logger.trace("Adding the access widener: {}", widener);
            accessTransformer.addWidener(path);
          } catch(final IOException exception) {
            Logger.trace(exception, "Failed to configure widener: {}", widener);
            continue;
          }

          Logger.trace("Added the access widener: {}", widener);
        }
      }
    }
  }

  /**
   * Applies the mixin transformers provided by the mods.
   *
   * @since 1.0.0
   */
  public void resolveMixins() {
    FoxMixinService service = (FoxMixinService) MixinService.getService();
    FoxMixinContainer handle = (FoxMixinContainer) service.getPrimaryContainer();

    // Add the mixin configurations.
    for(final ModContainer container : this.containers()) {
      final ModResource resource = container.resource();

      handle.addResource(resource.path().getFileName().toString(), resource.path());

      final List<String> mixins = ((ModContainerImpl) container).config().mixins();
      if(mixins != null && !mixins.isEmpty()) {
        for(final String config : mixins) {
          final ModContainer previous = this.containersByConfig.putIfAbsent(config, container);
          if(previous != null) {
            Logger.warn("Skipping duplicate mixin configuration: {} (in {} and {})", config, previous.id(), container.id());
            continue;
          }

          Mixins.addConfiguration(config);
        }

        Logger.trace("Added the mixin configurations: {}", String.join(", ", mixins));
      }
    }

    // Add the decorators.
    for(final Config config : Mixins.getConfigs()) {
      final ModContainer container = this.containersByConfig.get(config.getName());
      if(container == null) continue;

      final IMixinConfig mixinConfig = config.getConfig();
      mixinConfig.decorate(FabricUtil.KEY_MOD_ID, container.id());
      mixinConfig.decorate(FabricUtil.KEY_COMPATIBILITY, FabricUtil.COMPATIBILITY_LATEST);
    }
  }
}
