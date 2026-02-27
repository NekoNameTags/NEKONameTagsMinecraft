pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
    }
}

rootProject.name = "NekoNameTags-Minecraft"

include("core")
include("plugin-paper")
include("mod-fabric-1_21")
include("mod-forge-1_21")
