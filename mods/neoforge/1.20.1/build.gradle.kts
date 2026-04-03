import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.141"
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

legacyForge {
    enable {
        neoForgeVersion = property("neoforge_version").toString()
    }
}

dependencies {
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

project.setProperty("archivesBaseName", "NekoNameTags-NeoForge")

tasks.jar {
    from(coreMainOutput)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version.toString())
    }
}
