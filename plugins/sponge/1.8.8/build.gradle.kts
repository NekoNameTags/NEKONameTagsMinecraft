import org.gradle.api.tasks.SourceSetContainer

plugins {
    `java`
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

dependencies {
    implementation(project(":core"))
    compileOnly("org.spongepowered:spongeapi:${property("sponge_api_version")}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

project.setProperty("archivesBaseName", "NekoNameTags-Sponge")

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/sponge_plugins.json") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    from(coreMainOutput)
}

