import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("net.minecraftforge.gradle") version "6.0.43"
    `java`
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

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

base {
    archivesName.set("NekoNameTags-Forge")
}

tasks.jar {
    from(coreMainOutput)
}

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
