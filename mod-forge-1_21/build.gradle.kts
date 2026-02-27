plugins {
    id("net.minecraftforge.gradle") version "6.0.43"
    `java`
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("forge_version")}")
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

minecraft {
    mappings("official", "1.21.1")
    copyIdeResources.set(true)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version.toString())
    }
}
