/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.cws.fox.fabric;

import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.visitors.ClassTweakerRemapperVisitor;
import net.fabricmc.loader.impl.FormattedException;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.loader.impl.util.FileSystemUtil;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.TinyRemapperLoggerAdapter;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import org.objectweb.asm.commons.Remapper;
import ru.cws.fox.Fox;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class FoxRuntimeModRemapper {
	private static Path getMappingsJarPath() throws URISyntaxException {
		String resourcePath = "mappings/" + Fox.MINECRAFT_VERSION + "/minecraft-common-intermediary-loom.mappings.jar";
		URL resourceUrl = Fox.FOX_CLASS_LOADER.getResource(resourcePath);

		if (resourceUrl == null) {
			Log.warn(LogCategory.MOD_REMAP, "Mapping jar resource not found: " + resourcePath);
			return null;
		}

		return Path.of(resourceUrl.toURI());
	}

	public static void remap(Collection<ModCandidateImpl> modCandidates, Path tmpDir, Path outputDir) {
		List<ModCandidateImpl> modsToRemap = new ArrayList<>();
		for (ModCandidateImpl mod : modCandidates) {
			if (mod.getRequiresRemap()) {
				modsToRemap.add(mod);
			}
		}

		if (modsToRemap.isEmpty()) {
			Log.info(LogCategory.MOD_REMAP, "No mods require remapping.");
			return;
		}

		Log.info(LogCategory.MOD_REMAP, "Remapping %d mod(s): %s", modsToRemap.size(), modsToRemap.stream().map(ModCandidateImpl::getId).collect(Collectors.joining(", ")));

		MappingConfiguration config = Fox.FABRIC_MODS_ENGINE.mappingConfiguration;
		String modNs = config.getDefaultModDistributionNamespace();
		String runtimeNs = config.getRuntimeNamespace();

		Log.info(LogCategory.MOD_REMAP, "modNs: %s, runtimeNs: %s", modNs, runtimeNs);
		Log.info(LogCategory.MOD_REMAP, "Mappings loaded: %b, has classes: %b", config.hasAnyMappings(), config.hasAnyMappings() && !config.getMappings().getClasses().isEmpty());

		if (modNs.equals(runtimeNs) || !config.hasAnyMappings()) {
			Log.warn(LogCategory.MOD_REMAP, "Skipping remap: namespaces are equal or no mappings available.");
			return;
		}

		Map<ModCandidateImpl, RemapInfo> infoMap = new HashMap<>();
		TinyRemapper remapper = null;

		try {
			ClassTweaker mergedClassTweaker = ClassTweaker.newInstance();
			mergedClassTweaker.visitHeader(modNs);

			for (ModCandidateImpl mod : modsToRemap) {
				RemapInfo info = new RemapInfo();
				infoMap.put(mod, info);

				if (mod.hasPath()) {
					List<Path> paths = mod.getPaths();
					if (paths.size() != 1) throw new UnsupportedOperationException("multiple path for " + mod);
					info.inputPath = paths.get(0);
				} else {
					info.inputPath = mod.copyToDir(tmpDir, true);
					info.inputIsTemp = true;
				}

				info.outputPath = outputDir.resolve(mod.getDefaultFileName());
				Files.deleteIfExists(info.outputPath);

				String classTweaker = mod.getMetadata().getClassTweaker();
				if (classTweaker != null) {
					info.classTweakerPath = classTweaker;
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.inputPath, false)) {
						FileSystem fs = jarFs.get();
						info.classTweaker = Files.readAllBytes(fs.getPath(classTweaker));
					} catch (Throwable t) {
						throw new RuntimeException("Error reading class tweaker for mod '" + mod.getId() + "'!", t);
					}
					ClassTweakerReader.create(mergedClassTweaker).read(info.classTweaker, modNs);
				}
			}

			TinyRemapper.Builder builder = TinyRemapper.newRemapper(new TinyRemapperLoggerAdapter(LogCategory.MOD_REMAP))
					.withMappings(TinyUtils.createMappingProvider(Fox.FABRIC_MODS_ENGINE.mappingConfiguration.getMappings(), modNs, runtimeNs))
					.renameInvalidLocals(false)
					.extraAnalyzeVisitor((mrjVersion, className, next) -> mergedClassTweaker.createClassVisitor(Fox.ASM_VERSION, next, null));

			List<Path> classpathList = new ArrayList<>();

			String classpathFileProp = System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE);
			if (classpathFileProp != null && !classpathFileProp.isEmpty()) {
				try {
					classpathList.addAll(getRemapClasspath(classpathFileProp));
				} catch (IOException e) {
					throw new RuntimeException("Failed to read remap classpath file", e);
				}
				Log.info(LogCategory.MOD_REMAP, "Using classpath from " + classpathFileProp);
			} else {
				Path mappingsJar = getMappingsJarPath();
				if (mappingsJar != null && Files.exists(mappingsJar)) {
					classpathList.add(mappingsJar);
					Log.info(LogCategory.MOD_REMAP, "Added mappings jar: " + mappingsJar);
				} else {
					Log.warn(LogCategory.MOD_REMAP, "Mappings jar not available from resources.");
				}

				String systemCp = System.getProperty("java.class.path");
				if (systemCp != null && !systemCp.isEmpty()) {
					Arrays.stream(systemCp.split(File.pathSeparator))
							.map(Paths::get)
							.filter(Files::exists)
							.forEach(classpathList::add);
					Log.info(LogCategory.MOD_REMAP, "Added system classpath entries: " + classpathList.size() + " items");
				} else {
					Log.warn(LogCategory.MOD_REMAP, "System classpath is empty.");
				}
			}

			if (classpathList.isEmpty()) {
				Log.warn(LogCategory.MOD_REMAP, "Classpath is empty, remapping may fail.");
			}

			Set<InputTag> remapMixins = new HashSet<>();
			remapper = builder
					.extension(new MixinExtension(remapMixins::contains))
					.build();

			remapper.readClassPathAsync(classpathList.toArray(new Path[0]));

			for (ModCandidateImpl mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				InputTag tag = remapper.createInputTag();
				info.tag = tag;
				remapMixins.add(tag);
				remapper.readInputsAsync(tag, info.inputPath);
				Log.info(LogCategory.MOD_REMAP, "Added mod '%s' with tag %s", mod.getId(), tag);
			}

			for (ModCandidateImpl mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(info.outputPath).build();

				try (FileSystemUtil.FileSystemDelegate delegate = FileSystemUtil.getJarFileSystem(info.inputPath, false)) {
					if (delegate.get() == null) {
						throw new RuntimeException("Could not open JAR file " + info.inputPath.getFileName() + " for NIO reading!");
					}
					Path inputJar = delegate.get().getRootDirectories().iterator().next();
					outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
				}

				info.outputConsumerPath = outputConsumer;
				remapper.apply(outputConsumer, info.tag);
				Log.info(LogCategory.MOD_REMAP, "Applied remap to mod '%s'", mod.getId());
			}

			for (ModCandidateImpl mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				if (info.classTweaker != null) {
					info.classTweaker = remapClassTweaker(info.classTweaker,
							remapper.getEnvironment().getRemapper(), modNs, runtimeNs);
				}
			}

			remapper.finish();

			for (ModCandidateImpl mod : modsToRemap) {
				RemapInfo info = infoMap.get(mod);
				info.outputConsumerPath.close();

				if (info.classTweakerPath != null) {
					try (FileSystemUtil.FileSystemDelegate jarFs = FileSystemUtil.getJarFileSystem(info.outputPath, false)) {
						FileSystem fs = jarFs.get();
						Files.delete(fs.getPath(info.classTweakerPath));
						Files.write(fs.getPath(info.classTweakerPath), info.classTweaker);
					}
				}

				mod.setPaths(Collections.singletonList(info.outputPath));
			}

			Log.info(LogCategory.MOD_REMAP, "Remapping completed successfully.");

		} catch (Throwable t) {
			if (remapper != null) remapper.finish();
			for (RemapInfo info : infoMap.values()) {
				if (info.outputPath != null) {
					try { Files.deleteIfExists(info.outputPath); } catch (IOException ignored) {}
				}
			}
			throw new FormattedException("Failed to remap mods!", t);
		} finally {
			for (RemapInfo info : infoMap.values()) {
				if (info.inputIsTemp) {
					try { Files.deleteIfExists(info.inputPath); } catch (IOException ignored) {}
				}
			}
		}
	}

	private static byte[] remapClassTweaker(byte[] input, Remapper remapper, String modNs, String runtimeNs) {
		ClassTweakerWriter writer = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
		ClassTweakerRemapperVisitor remappingDecorator = new ClassTweakerRemapperVisitor(writer, remapper, modNs, runtimeNs);
		ClassTweakerReader reader = ClassTweakerReader.create(remappingDecorator);
		reader.read(input, modNs);
		return writer.getOutput();
	}

	private static List<Path> getRemapClasspath(String classpathFile) throws IOException {
		String content = Files.readString(Paths.get(classpathFile));
		return Arrays.stream(content.split(File.pathSeparator))
				.map(Paths::get)
				.collect(Collectors.toList());
	}

	private static class RemapInfo {
		InputTag tag;
		Path inputPath;
		Path outputPath;
		boolean inputIsTemp;
		OutputConsumerPath outputConsumerPath;
		String classTweakerPath;
		byte[] classTweaker;
	}
}