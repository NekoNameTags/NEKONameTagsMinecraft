import org.gradle.api.tasks.SourceSetContainer

plugins {
    `java`
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>()["main"].output

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:${property("paper_api_version")}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

project.setProperty("archivesBaseName", "NekoNameTags-Paper")

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    from(coreMainOutput)
}

