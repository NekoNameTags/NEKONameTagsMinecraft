plugins {
    `java`
}

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

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/sponge_plugins.json") {
        expand("version" to project.version.toString())
    }
}

