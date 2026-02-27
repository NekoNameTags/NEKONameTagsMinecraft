plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
}

dependencies {
    api("com.google.code.gson:gson:2.11.0")
}

