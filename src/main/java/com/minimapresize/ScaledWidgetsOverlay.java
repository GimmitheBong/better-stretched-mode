package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Composite all cutouts after extraction, so overlapping regions cannot capture each other. */
final class ScaledWidgetsOverlay extends Overlay
{
	private final MinimapResizePlugin plugin;

	ScaledWidgetsOverlay(MinimapResizePlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST + 1);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		plugin.drawOutputs(graphics);
		return null;
	}
}
