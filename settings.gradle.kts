pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "NekoNameTags-Minecraft"

include("core")

val targetLoader = providers.gradleProperty("nnt_target_loader")
    .orNull
    ?.trim()
    ?.lowercase()
    ?: "all"
val targetMinecraftVersion = providers.gradleProperty("nnt_minecraft_version")
    .orNull
    ?.trim()
    ?: ""

fun includeFor(vararg loaders: String, projectPath: String) {
    if (targetLoader == "all" || loaders.any { it == targetLoader }) {
        include(projectPath)
    }
}

includeFor("paper", "bukkit", projectPath = "plugin-paper")
includeFor("sponge", projectPath = "plugin-sponge")
includeFor("forge", projectPath = "mod-forge-1_21")
includeFor("neoforge", projectPath = "mod-neoforge-1_21")

if (targetLoader == "all" || targetLoader == "fabric") {
    if (targetMinecraftVersion == "1.21.10" || targetMinecraftVersion == "1.21.11") {
        include("mod-fabric-1_21_11")
    } else {
        include("mod-fabric-1_21")
    }
}
