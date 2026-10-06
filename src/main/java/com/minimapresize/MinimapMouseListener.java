package com.minimapresize;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

final class MinimapMouseListener implements MouseListener, MouseWheelListener
{
	private final Client client;
	private final MinimapResizePlugin plugin;

	@Inject
	MinimapMouseListener(Client client, MinimapResizePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	private <T extends MouseEvent> T translate(T event)
	{
		// Native context menus are drawn at normal size and use normal coordinates.
		if (event.isConsumed()
			|| client.getGameState() != GameState.LOGGED_IN || !client.isResized())
		{
			return event;
		}
		InputRoute route = client.isMenuOpen() ? InputRoute.scene(event.getPoint())
			: InputRoute.resolve(event.getPoint(), plugin.getInputFrames());
		plugin.recordPointer(event.getPoint(), route.target, event.getID(), event.getWhen());
		Point point = route.point;
		// translatePoint preserves wheel precision, button, time, modifiers and screen coordinates.
		event.translatePoint(point.x - event.getX(), point.y - event.getY());
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event) { return translate(event); }

	@Override
	public MouseEvent mousePressed(MouseEvent event) { return translate(event); }

	@Override
	public MouseEvent mouseReleased(MouseEvent event) { return translate(event); }

	@Override
	public MouseEvent mouseEntered(MouseEvent event) { return translate(event); }

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		plugin.recordPointer(new Point(-1, -1), null, event.getID(), event.getWhen());
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event) { return translate(event); }

	@Override
	public MouseEvent mouseMoved(MouseEvent event) { return translate(event); }

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) { return translate(event); }
}
