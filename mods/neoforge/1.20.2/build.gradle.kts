import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("net.neoforged.gradle.userdev") version "7.0.116"
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

repositories {
    mavenLocal()
}

dependencies {
    implementation("net.neoforged:neoforge:${property("neoforge_version")}")
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
