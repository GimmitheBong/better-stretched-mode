package com.minimapresize;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class MenuRelocationOverlay extends Overlay
{
	private final MenuRelocator menus;

	MenuRelocationOverlay(MenuRelocator menus)
	{
		this.menus = menus;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST + 2);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		menus.draw(graphics);
		return null;
	}
}
