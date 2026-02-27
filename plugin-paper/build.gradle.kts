plugins {
    `java`
}

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

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

