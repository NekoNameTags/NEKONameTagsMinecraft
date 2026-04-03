plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

project.setProperty("archivesBaseName", "NekoNameTags-UI-Core")

