package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class MinimapCaptureOverlay extends Overlay
{
	private final MinimapResizePlugin plugin;

	@Inject
	MinimapCaptureOverlay(MinimapResizePlugin plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPriority(PRIORITY_HIGHEST + 1);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		plugin.captureBackground();
		return null;
	}
}
