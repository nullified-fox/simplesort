/**
    Copyright (c) 2026 FanaticalFoxx. All Rights Reserved.

    You may NOT:
    - Redistribute, copy, or mirror the source code or compiled files
    - Modify or create derivative works
    - Reupload to any mod hosting platform

    You MAY:
    - Include this mod in modpacks (public or private) without prior permission
    - Share modpack links that reference the official download
 */

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
