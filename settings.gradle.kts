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
include("plugin-paper")
include("mod-fabric-1_21")
include("mod-forge-1_21")
include("mod-neoforge-1_21")
include("plugin-sponge")
