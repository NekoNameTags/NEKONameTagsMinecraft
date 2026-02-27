plugins {
    id("base")
}

allprojects {
    group = "uk.co.nekosunevr.nekonametags"
    version = (findProperty("mod_version") as String? ?: "0.1.0-SNAPSHOT")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
