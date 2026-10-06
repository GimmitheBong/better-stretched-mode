package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class MenuBackgroundOverlay extends Overlay
{
	private final MenuRelocator menus;

	MenuBackgroundOverlay(MenuRelocator menus)
	{
		this.menus = menus;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST + 2);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		menus.captureBackground();
		return null;
	}
}
