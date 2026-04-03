plugins {
    id("net.minecraftforge.gradle") version "6.0.43"
    `java`
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("forge_version")}")
    implementation(project(":ui-core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

project.setProperty("archivesBaseName", "NekoNameTags-Forge-UI")

minecraft {
    mappings("official", property("minecraft_version").toString())
    copyIdeResources.set(true)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version.toString())
    }
}

