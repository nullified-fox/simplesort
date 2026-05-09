package me.simplesort.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "simple-sort")
public class ModConfig implements ConfigData {
    public boolean useItemFrameLabels = true;

    public boolean sortHotbar = false;

    public boolean sortOffhand = false;

    public boolean playSounds = true;

    public boolean showOverlayMessages = true;
}
