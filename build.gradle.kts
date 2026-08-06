plugins {
    id("java")
    id("com.gradleup.shadow") version("9.3.0+")
}

group = "ru.cws"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/folia.jar"))

    implementation("space.vectrix.ignite:ignite-api:1.1.0")

    implementation(files("libs/launchwrapper-1.12.jar"))
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    implementation("io.github.llamalad7:mixinextras-common:0.5.4")

    compileOnly("org.jetbrains:annotations:26.1.0")
    implementation("org.tinylog:tinylog-api:2.8.0-M1")
    implementation("org.tinylog:tinylog-impl:2.8.0-M1")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")
    implementation("org.ow2.asm:asm-util:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveBaseName = "FoX-CWS"
    manifest {
        attributes["Main-Class"] = "ru.cws.fox.Fox"
    }
}

tasks.test {
    useJUnitPlatform()
}
