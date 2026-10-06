package com.minimapresize;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(MinimapResizeConfig.GROUP)
public interface MinimapResizeConfig extends Config
{
	String GROUP = "minimapresize";

	@ConfigSection(name = "Minimap", description = "Scale the minimap, compass and orbs together", position = 0)
	String MINIMAP = "minimap";

	@ConfigSection(name = "Modern layout tab bars", description = "Scale the two tab button bars independently", position = 10)
	String TAB_BARS = "tabBars";

	@ConfigSection(name = "Inventory / side panel", description = "Scale the open inventory, spellbook, prayer or other side panel", position = 20)
	String SIDE_PANEL = "sidePanel";

	@ConfigSection(name = "Filtering & sharpening", description = "Shared image quality and refresh settings for all scaled regions", position = 30)
	String FILTERING = "filtering";

	@ConfigItem(keyName = "scale", name = "Minimap size", position = 0, section = MINIMAP,
		description = "Size of the whole minimap cluster. 100% is normal. Requires a resizable layout.")
	@Range(min = 50, max = 250)
	@Units(Units.PERCENT)
	default int scale()
	{
		return 150;
	}

	@ConfigItem(keyName = "offsetX", name = "Move left", position = 1, section = MINIMAP,
		description = "Move the resized minimap left from its usual right edge, in game pixels.")
	@Range(min = 0, max = 2000)
	default int offsetX()
	{
		return 0;
	}

	@ConfigItem(keyName = "offsetY", name = "Move down", position = 2, section = MINIMAP,
		description = "Move the resized minimap down from its usual top edge, in game pixels.")
	@Range(min = 0, max = 2000)
	default int offsetY()
	{
		return 0;
	}

	// Retain the old key so existing users keep their chosen filter until they select a new one.
	@ConfigItem(keyName = "smooth", name = "Smooth scaling", position = 30, section = FILTERING, hidden = true,
		description = "Use bilinear filtering. Disable for sharp, pixelated scaling.")
	default boolean smooth()
	{
		return false;
	}

	@ConfigItem(keyName = "filter", name = "Scaling filter", position = 31, section = FILTERING,
		description = "Sharp Bicubic combines smooth scaling with crisper detail. Applies to every scaled region.")
	default ScalingFilter filter()
	{
		return smooth() ? ScalingFilter.BILINEAR : ScalingFilter.NEAREST;
	}

	@ConfigItem(keyName = "sharpening", name = "Sharpening strength", position = 32, section = FILTERING,
		description = "Sharp Bicubic only. Start at 40%. Higher values add contrast; 0% is plain Bicubic.")
	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	default int sharpening()
	{
		return 40;
	}

	@ConfigItem(keyName = "scalingFps", name = "Scaling refresh limit", position = 33, section = FILTERING,
		description = "Limit expensive rescaling, not game FPS. 60 is recommended. 0 processes every changed game frame.")
	@Range(min = 0, max = 240)
	@Units("fps")
	default int scalingFps() { return 60; }

	@ConfigItem(keyName = "upperTabScale", name = "Upper tab bar size", position = 11, section = TAB_BARS,
		description = "Combat, inventory, prayer, spellbook, etc. Modern layout only. 100% is normal.")
	@Range(min = 50, max = 250)
	@Units(Units.PERCENT)
	default int upperTabScale() { return 100; }

	@ConfigItem(keyName = "upperTabLeft", name = "Upper bar: move left", position = 12, section = TAB_BARS,
		description = "Move the upper/movable tab bar left, in game pixels.")
	@Range(min = 0, max = 2000)
	default int upperTabLeft() { return 0; }

	@ConfigItem(keyName = "upperTabUp", name = "Upper bar: move up", position = 13, section = TAB_BARS,
		description = "Move the upper/movable tab bar up, in game pixels.")
	@Range(min = 0, max = 2000)
	default int upperTabUp() { return 0; }

	@ConfigItem(keyName = "lowerTabScale", name = "Lower tab bar size", position = 14, section = TAB_BARS,
		description = "Social, settings and other lower-row tabs. Modern layout only. 100% is normal.")
	@Range(min = 50, max = 250)
	@Units(Units.PERCENT)
	default int lowerTabScale() { return 100; }

	@ConfigItem(keyName = "lowerTabLeft", name = "Lower bar: move left", position = 15, section = TAB_BARS,
		description = "Move the lower/static tab bar left, in game pixels.")
	@Range(min = 0, max = 2000)
	default int lowerTabLeft() { return 0; }

	@ConfigItem(keyName = "lowerTabUp", name = "Lower bar: move up", position = 16, section = TAB_BARS,
		description = "Move the lower/static tab bar up, in game pixels.")
	@Range(min = 0, max = 2000)
	default int lowerTabUp() { return 0; }

	@ConfigItem(keyName = "panelScale", name = "Inventory / panel size", position = 21, section = SIDE_PANEL,
		description = "Scale the active inventory, spellbook, prayer or other side panel. Tab bars keep their separate size.")
	@Range(min = 50, max = 250)
	@Units(Units.PERCENT)
	default int panelScale() { return 100; }

	@ConfigItem(keyName = "panelLeft", name = "Panel: move left", position = 22, section = SIDE_PANEL,
		description = "Move the scaled side panel left from its original right edge, in game pixels.")
	@Range(min = 0, max = 2000)
	default int panelLeft() { return 0; }

	@ConfigItem(keyName = "panelUp", name = "Panel: move up", position = 23, section = SIDE_PANEL,
		description = "Move the scaled side panel up from its original bottom edge, in game pixels.")
	@Range(min = 0, max = 2000)
	default int panelUp() { return 0; }
}
