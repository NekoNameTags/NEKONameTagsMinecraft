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
include("ui-core")
project(":ui-core").projectDir = file("ui/core")

val targetLoader = providers.gradleProperty("nnt_target_loader")
    .orNull
    ?.trim()
    ?.lowercase()
    ?: "all"
val targetMinecraftVersion = providers.gradleProperty("nnt_minecraft_version")
    .orElse(providers.gradleProperty("minecraft_version"))
    .orNull
    ?.trim()
    ?: "1.21.1"

val normalizedLoader = when (targetLoader) {
    "bukkit" -> "paper"
    else -> targetLoader
}

fun loaderFolder(loader: String): String? = when (loader) {
    "fabric" -> "mods/fabric"
    "forge" -> "mods/forge"
    "neoforge" -> "mods/neoforge"
    "paper" -> "plugins/paper"
    "sponge" -> "plugins/sponge"
    "fabric-ui" -> "ui/fabric"
    "forge-ui" -> "ui/forge"
    "neoforge-ui" -> "ui/neoforge"
    else -> null
}

fun includeVersionModule(loader: String, mcVersion: String) {
    val baseFolder = loaderFolder(loader) ?: return
    val folder = file("$baseFolder/$mcVersion")
    if (!folder.exists()) {
        return
    }
    val versionSegment = mcVersion.replace('.', '_')
    val projectPath = "$loader:$versionSegment"
    include(projectPath)
    project(":$projectPath").projectDir = folder
}

if (normalizedLoader == "all") {
    listOf("paper", "sponge", "fabric", "forge", "neoforge").forEach { loader ->
        includeVersionModule(loader, targetMinecraftVersion)
    }
} else if (normalizedLoader in setOf("paper", "sponge", "fabric", "forge", "neoforge", "fabric-ui", "forge-ui", "neoforge-ui")) {
    includeVersionModule(normalizedLoader, targetMinecraftVersion)
}
