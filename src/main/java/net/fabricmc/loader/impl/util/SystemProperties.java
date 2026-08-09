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

package net.fabricmc.loader.impl.util;

public final class SystemProperties {
	// file to source mappings from, defaults to mappings/mappings.tiny on the class path
	public static final String MAPPING_PATH = "fabric.mappingPath";
	// mapping namespace to use at runtime, default: official for unobfuscated games, named for obfuscated games while DEVELOPMENT is set or intermediary otherwise
	public static final String RUNTIME_MAPPING_NAMESPACE = "fabric.runtimeMappingNamespace";
	// mapping namespace to assume mods are using when not explicitly stated, defaults to official if the runtime namespace is official or intermediary otherwise
	public static final String DEFAULT_MOD_DISTRIBUTION_NAMESPACE = "fabric.defaultModDistributionNamespace";
	// set the game version for the builtin game mod/dependencies, bypassing auto-detection
	public static final String GAME_VERSION = "fabric.gameVersion";
	// fallback log file for the builtin log handler (dumped on exit if not replaced with another handler)
	public static final String LOG_FILE = "fabric.log.file";
	// minimum log level for builtin log handler
	public static final String LOG_LEVEL = "fabric.log.level";
	// additional mods to load (path separator separated paths, @ prefix for meta-file with each line referencing an actual file)
	public static final String ADD_MODS = "fabric.addMods";
	// a comma-separated list of mod ids to disable, even if they're discovered. mostly useful for unit testing.
	public static final String DISABLE_MOD_IDS = "fabric.debug.disableModIds";
	// file containing the class path for in-dev runtime mod remapping
	public static final String REMAP_CLASSPATH_FILE = "fabric.remapClasspathFile";
	// class path groups to map multiple class path entries to a mod (paths separated by path separator, groups by double path separator)
	public static final String PATH_GROUPS = "fabric.classPathGroups";
	// enable the fixing of package access errors in the game jar(s)
	public static final String FIX_PACKAGE_ACCESS = "fabric.fixPackageAccess";
	// throw exceptions from entrypoints, discovery etc. directly instead of gathering and attaching as suppressed
	public static final String DEBUG_THROW_DIRECTLY = "fabric.debug.throwDirectly";
	// disables mod load order shuffling to be the same in-dev as in production
	public static final String DEBUG_DISABLE_MOD_SHUFFLE = "fabric.debug.disableModShuffle";
	// workaround for bad load order dependencies
	public static final String DEBUG_LOAD_LATE = "fabric.debug.loadLate";
	// override the mod discovery timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_DISCOVERY_TIMEOUT = "fabric.debug.discoveryTimeout";
	// override the mod resolution timeout, unit in seconds, <= 0 to disable
	public static final String DEBUG_RESOLUTION_TIMEOUT = "fabric.debug.resolutionTimeout";
	// replace mod versions (modA:versionA,modB:versionB,...)
	public static final String DEBUG_REPLACE_VERSION = "fabric.debug.replaceVersion";

	public static boolean isSet(String property) {
		String val = System.getProperty(property);

		return val != null && !val.equalsIgnoreCase("false");
	}
}
