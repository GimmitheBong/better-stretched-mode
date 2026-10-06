package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Reapply input-only properties after all native interfaces have been drawn. */
final class InputGateOverlay extends Overlay
{
	private final MinimapResizePlugin plugin;

	InputGateOverlay(MinimapResizePlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST + 1);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		plugin.finishRender();
		return null;
	}
}
