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

base {
    archivesName.set("NekoNameTags-UI-Core")
}
