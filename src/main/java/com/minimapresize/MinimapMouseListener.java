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
	private MinimapInputFrame dragFrame;

	@Inject
	MinimapMouseListener(Client client, MinimapResizePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	private <T extends MouseEvent> T translate(T event)
	{
		// Menu translation is independent of the underlying widget's scaling transform.
		if (event.isConsumed()
			|| client.getGameState() != GameState.LOGGED_IN || !client.isResized())
		{
			dragFrame = null;
			plugin.recordDragTarget(null, false);
			return event;
		}
		InputRoute route = client.isMenuOpen() ? InputRoute.scene(event.getPoint())
			: InputRoute.resolve(event.getPoint(), plugin.getInputFrames());
		if (client.isMenuOpen())
		{
			dragFrame = null;
			plugin.recordDragTarget(null, false);
		}
		else if (event.getID() == MouseEvent.MOUSE_PRESSED)
		{
			dragFrame = null;
			if (event.getButton() == MouseEvent.BUTTON1 && route.target != null && route.target.rectangularInput())
			{
				dragFrame = findFrame(route.target);
			}
			plugin.recordDragTarget(dragFrame == null ? null : dragFrame.region(), false);
		}
		else if (dragFrame != null && (event.getID() == MouseEvent.MOUSE_DRAGGED || event.getID() == MouseEvent.MOUSE_RELEASED))
		{
			MinimapInputFrame current = findFrame(dragFrame.region());
			if (current != null)
			{
				dragFrame = current;
				route = InputRoute.drag(event.getPoint(), dragFrame);
				plugin.recordDragTarget(dragFrame.region(), event.getID() == MouseEvent.MOUSE_RELEASED);
			}
			else
			{
				plugin.recordDragTarget(null, false);
			}
			if (event.getID() == MouseEvent.MOUSE_RELEASED || current == null)
			{
				dragFrame = null;
			}
		}
		plugin.recordPointer(event.getPoint(), route.target, event.getID(), event.getWhen());
		if (event.getID() == MouseEvent.MOUSE_PRESSED)
		{
			plugin.recordMenuPress(event.getPoint(), route.point, route.target);
		}
		Point point = client.isMenuOpen() ? plugin.translateMenuPoint(event.getPoint()) : route.point;
		// translatePoint preserves wheel precision, button, time, modifiers and screen coordinates.
		event.translatePoint(point.x - event.getX(), point.y - event.getY());
		return event;
	}

	private MinimapInputFrame findFrame(WidgetRegion region)
	{
		for (MinimapInputFrame frame : plugin.getInputFrames())
		{
			if (frame.region() == region && frame.isFresh())
			{
				return frame;
			}
		}
		return null;
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
