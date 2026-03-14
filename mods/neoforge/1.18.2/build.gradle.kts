import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("net.neoforged.moddev") version "2.0.78"
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

neoForge {
    version = property("neoforge_version").toString()
}

dependencies {
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

base {
    archivesName.set("NekoNameTags-NeoForge")
}

tasks.jar {
    from(coreMainOutput)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version.toString())
    }
}
