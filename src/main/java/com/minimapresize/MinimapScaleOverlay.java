package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class MinimapScaleOverlay extends Overlay
{
	private final MinimapResizePlugin plugin;
	private final WidgetRegion region;

	MinimapScaleOverlay(MinimapResizePlugin plugin, WidgetRegion region)
	{
		this.plugin = plugin;
		this.region = region;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		setPriority(PRIORITY_HIGHEST + 1);
		for (int component : region.components)
		{
			drawAfterLayer(component);
		}
	}

	@Override
	public String getName() { return "MinimapResize_" + region; }

	@Override
	public Dimension render(Graphics2D graphics)
	{
		plugin.drawScaled(region, graphics);
		return null;
	}
}
