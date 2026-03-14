import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("fabric-loom") version "1.13.3"
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-content-registries-v0")
    }
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

base {
    archivesName.set("NekoNameTags-Fabric-UI")
}

tasks.jar {
    from(coreMainOutput)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version.toString())
    }
}
