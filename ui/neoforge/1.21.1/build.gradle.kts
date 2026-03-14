plugins {
    id("net.neoforged.moddev") version "2.0.78"
}

neoForge {
    version = property("neoforge_version").toString()
}

dependencies {
    implementation(project(":ui-core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

base {
    archivesName.set("NekoNameTags-NeoForge-UI")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version.toString())
    }
}
