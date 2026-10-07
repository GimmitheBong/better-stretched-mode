package com.minimapresize;

import java.awt.Point;
import java.awt.event.InputEvent;
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
	private Point cameraOffset;
	private Point movementOffset;
	private volatile boolean resetRequested;

	@Inject
	MinimapMouseListener(Client client, MinimapResizePlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	private <T extends MouseEvent> T translate(T event)
	{
		if (resetRequested)
		{
			resetRequested = false;
			clearCameraDrag();
			dragFrame = null;
			movementOffset = null;
		}
		// Menu translation is independent of the underlying widget's scaling transform.
		if (client.getGameState() != GameState.LOGGED_IN || !client.isResized())
		{
			clearCameraDrag();
			movementOffset = null;
			dragFrame = null;
			plugin.recordDragTarget(null, false);
			return event;
		}
		if (event.isConsumed())
		{
			if (releasesCamera(event)) { clearCameraDrag(); }
			return event;
		}
		if (cameraOffset != null && event.getID() == MouseEvent.MOUSE_MOVED && !anyMouseButtonDown(event))
		{
			// Recover from a release swallowed by another listener or a focus transition.
			clearCameraDrag();
		}
		InputRoute route = client.isMenuOpen() ? InputRoute.scene(event.getPoint())
			: InputRoute.resolve(event.getPoint(), plugin.getInputFrames());
		if (cameraOffset == null && event.getID() == MouseEvent.MOUSE_PRESSED && plugin.isCameraPress(event))
		{
			Point initial = client.isMenuOpen() ? plugin.translateMenuPoint(event.getPoint()) : route.point;
			if (movementOffset != null)
			{
				cameraOffset = new Point(movementOffset);
			}
			else
			{
				// If enabled with the pointer already on a control, preserve the client's
				// existing camera baseline rather than jumping to a new widget space.
				net.runelite.api.Point nativePointer = client.getMouseCanvasPosition();
				if (nativePointer != null && nativePointer.getX() >= 0 && nativePointer.getY() >= 0)
				{
					initial = new Point(nativePointer.getX(), nativePointer.getY());
				}
				cameraOffset = new Point(initial.x - event.getX(), initial.y - event.getY());
			}
			dragFrame = null;
			plugin.recordDragTarget(null, false);
		}
		if (cameraOffset != null)
		{
			Point actual = event.getPoint();
			Point point = new Point(actual.x + cameraOffset.x, actual.y + cameraOffset.y);
			plugin.recordCameraInputOffset(cameraOffset);
			boolean rightMenuPress = event.getID() == MouseEvent.MOUSE_PRESSED && !event.isAltDown()
				&& (event.getButton() == MouseEvent.BUTTON3 || event.isMetaDown());
			plugin.recordPointer(actual, rightMenuPress ? route.target : null, event.getID(), event.getWhen());
			if (event.getID() == MouseEvent.MOUSE_PRESSED)
			{
				plugin.recordMenuPress(actual, point, route.target);
			}
			boolean released = releasesCamera(event);
			applyPoint(event, point);
			if (released) { clearCameraDrag(); }
			return event;
		}
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
		applyPoint(event, point);
		return event;
	}

	private void applyPoint(MouseEvent event, Point point)
	{
		if (event.getID() == MouseEvent.MOUSE_MOVED || event.getID() == MouseEvent.MOUSE_DRAGGED
			|| event.getID() == MouseEvent.MOUSE_ENTERED)
		{
			movementOffset = new Point(point.x - event.getX(), point.y - event.getY());
		}
		// Preserve button, modifiers, wheel precision, timestamps and screen coordinates.
		event.translatePoint(point.x - event.getX(), point.y - event.getY());
	}

	private boolean releasesCamera(MouseEvent event)
	{
		// The native client has one current-button state; any release clears it.
		return cameraOffset != null && event.getID() == MouseEvent.MOUSE_RELEASED;
	}

	private void clearCameraDrag()
	{
		cameraOffset = null;
		plugin.recordCameraInputOffset(null);
	}

	private static boolean anyMouseButtonDown(MouseEvent event)
	{
		for (int button = 1; button <= 20; button++)
		{
			if ((event.getModifiersEx() & InputEvent.getMaskForButton(button)) != 0) { return true; }
		}
		return false;
	}

	void resetGestures() { resetRequested = true; }

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
