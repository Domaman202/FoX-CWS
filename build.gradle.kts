plugins {
    id("java")
    id("com.gradleup.shadow") version("9.3.0+")
}

group = "ru.cws"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net")
}

dependencies {
    compileOnly(files("libs/folia.jar"))
    compileOnly(files("/home/dmn/Workspace/Projects/FoX-CWS/run/versions/1.21.11/folia-1.21.11.jar"))

    implementation("space.vectrix.ignite:ignite-api:1.1.0")
    implementation("net.fabricmc:class-tweaker:0.3.0-beta.2")
    implementation("net.fabricmc:tiny-remapper:0.14.0")
    implementation("net.fabricmc:mapping-io:0.9.1")

    implementation(files("libs/launchwrapper-1.12.jar"))
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-common:0.5.4")

    compileOnly("org.jetbrains:annotations:26.1.0")
    implementation("org.tinylog:tinylog-api:2.8.0-M1")
    implementation("org.tinylog:tinylog-impl:2.8.0-M1")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    implementation("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")
    implementation("org.slf4j:slf4j-simple:2.1.0-alpha1")

    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(files("/home/dmn/Workspace/projects/FoX-CWS/run/versions/1.21.11/folia-1.21.11.jar"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveBaseName = "FoX-CWS"
    manifest {
        attributes["Main-Class"] = "ru.cws.fox.loader.Fox"
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Запускает Fox в папке run"

    doFirst {
        val runDir = file("run")
        if (!runDir.exists())
            runDir.mkdirs()
        val targetJar = file("run/folia.jar")
        if (!targetJar.exists()) {
            copy {
                from(file("libs/folia.jar"))
                into(runDir)
            }
        }
    }

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("ru.cws.fox.loader.Fox")
    workingDir = file("run")
    args = listOf("--nogui")
    systemProperty("mixin.debug.export", true)
    systemProperty("fox.debug", true)
}
