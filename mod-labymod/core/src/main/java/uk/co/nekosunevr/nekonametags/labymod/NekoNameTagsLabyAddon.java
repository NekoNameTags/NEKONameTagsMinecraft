package uk.co.nekosunevr.nekonametags.labymod;

import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class NekoNameTagsLabyAddon extends LabyAddon<NekoNameTagsLabyConfiguration> {

  @Override
  protected void enable() {
    this.registerSettingCategory();
    this.logger().info("NekoNameTags LabyMod addon enabled");
  }

  @Override
  protected Class<NekoNameTagsLabyConfiguration> configurationClass() {
    return NekoNameTagsLabyConfiguration.class;
  }
}
