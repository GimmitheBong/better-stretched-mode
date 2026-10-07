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
	private final int component;

	MinimapScaleOverlay(MinimapResizePlugin plugin, WidgetRegion region, int component)
	{
		this.plugin = plugin;
		this.region = region;
		this.component = component;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		setPriority(PRIORITY_HIGHEST + 1);
		drawAfterLayer(component);
	}

	@Override
	public String getName() { return "MinimapResize_" + region + "_" + component; }

	@Override
	public Dimension render(Graphics2D graphics)
	{
		plugin.drawScaled(region, component, graphics);
		return null;
	}
}
