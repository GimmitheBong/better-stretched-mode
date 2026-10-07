package com.minimapresize;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;

/** Moves the rendered native menu and its input together without altering any menu actions. */
final class MenuRelocator
{
	private static final class Press
	{
		final Point cursor;
		final long created = System.nanoTime();

		Press(Point cursor) { this.cursor = new Point(cursor); }
	}

	private final Client client;
	private final AtomicReference<Press> pending = new AtomicReference<>();
	private final MinimapCompositor compositor = new MinimapCompositor();
	private volatile Press active;
	private volatile Point cameraInputOffset;
	private MenuPlacement capturedPlacement;
	private Rectangle capturedArea;
	private BufferedImage capturedImage;

	MenuRelocator(Client client) { this.client = client; }

	void recordPress(Point cursor, Point nativePoint, WidgetRegion target)
	{
		// Also supports a left-click that opens a context menu in single-button mode.
		pending.set(!client.isMenuOpen() && target != null && !cursor.equals(nativePoint) ? new Press(cursor) : null);
	}

	void opened()
	{
		Press press = pending.getAndSet(null);
		active = press != null && System.nanoTime() - press.created < 1_000_000_000L ? press : null;
		compositor.invalidate();
	}

	Point translate(Point point)
	{
		MenuPlacement placement = placement();
		return placement == null ? point : placement.toNative(point);
	}

	void setCameraInputOffset(Point offset)
	{
		cameraInputOffset = offset == null ? null : new Point(offset);
	}

	private BufferedImage image()
	{
		if (!(client.getBufferProvider() instanceof MainBufferProvider)) { return null; }
		java.awt.Image image = ((MainBufferProvider) client.getBufferProvider()).getImage();
		return image instanceof BufferedImage ? (BufferedImage) image : null;
	}

	private MenuPlacement placement()
	{
		Press press = active;
		if (press == null || !client.isMenuOpen()) { return null; }
		Menu menu = client.getMenu();
		BufferedImage image = image();
		if (menu == null || image == null) { return null; }
		Rectangle bounds = bounds(menu);
		if (bounds.isEmpty()) { return null; }
		Point cameraOffset = cameraInputOffset;
		return cameraOffset == null ? new MenuPlacement(bounds, press.cursor, image.getWidth(), image.getHeight())
			: new MenuPlacement(bounds, cameraOffset);
	}

	private static Rectangle bounds(Menu menu)
	{
		return new Rectangle(menu.getMenuX(), menu.getMenuY(), menu.getMenuWidth(), menu.getMenuHeight());
	}

	void captureBackground()
	{
		compositor.invalidate();
		capturedPlacement = placement();
		capturedArea = null;
		capturedImage = null;
		if (capturedPlacement == null)
		{
			if (!client.isMenuOpen()) { active = null; }
			return;
		}
		if (capturedPlacement.offsetX == 0 && capturedPlacement.offsetY == 0) { return; }
		capturedImage = image();
		Rectangle area = new Rectangle(capturedPlacement.nativeBounds);
		collectMenuArea(client.getMenu(), area, new IdentityHashMap<>(), 0);
		capturedArea = area.intersection(new Rectangle(capturedImage.getWidth(), capturedImage.getHeight()));
		if (!capturedArea.isEmpty()) { compositor.capture(capturedImage, capturedArea, client.isGpu()); }
	}

	private static void collectMenuArea(Menu menu, Rectangle area, Map<Menu, Boolean> visited, int depth)
	{
		if (menu == null || depth > 8 || visited.put(menu, true) != null) { return; }
		Rectangle rectangle = bounds(menu);
		if (!rectangle.isEmpty()) { area.add(rectangle); }
		MenuEntry[] entries = menu.getMenuEntries();
		if (entries != null)
		{
			for (MenuEntry entry : entries)
			{
				if (entry != null) { collectMenuArea(entry.getSubMenu(), area, visited, depth + 1); }
			}
		}
	}

	void draw(Graphics2D graphics)
	{
		MenuPlacement current = placement();
		if (current == null || capturedPlacement == null || capturedArea == null || capturedArea.isEmpty()
			|| !current.nativeBounds.equals(capturedPlacement.nativeBounds)
			|| !compositor.matches(image(), capturedArea))
		{
			return;
		}
		// Keep the root menu opaque even if a menu pixel equals its background. Extra
		// submenu areas are difference-extracted, so unused submenu bounds don't move the scene.
		BufferedImage foreground = compositor.extractAndRestore(current.nativeBounds);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setComposite(AlphaComposite.SrcOver);
			UiBlitter.draw(capturedImage, foreground, capturedArea.x + current.offsetX, capturedArea.y + current.offsetY, g, client.isGpu());
		}
		finally
		{
			g.dispose();
		}
	}

	void reset()
	{
		pending.set(null);
		active = null;
		cameraInputOffset = null;
		capturedPlacement = null;
		capturedArea = null;
		capturedImage = null;
		compositor.clear();
	}
}
