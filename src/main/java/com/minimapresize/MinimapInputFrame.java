package com.minimapresize;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/** A published frame owns its hit mask; the renderer never mutates it afterward. */
final class MinimapInputFrame
{
	private final MinimapTransform transform;
	private final WidgetRegion region;
	private final boolean[] occupied;
	private final Rectangle source;
	private final long created = System.nanoTime();

	MinimapInputFrame(MinimapTransform transform, BufferedImage foreground)
	{
		this(WidgetRegion.MINIMAP, transform, foreground);
	}

	MinimapInputFrame(WidgetRegion region, MinimapTransform transform, BufferedImage foreground)
	{
		this.region = region;
		this.transform = transform;
		source = transform.source();
		occupied = new boolean[source.width * source.height];
		for (int y = 0; y < source.height; y++)
		{
			for (int x = 0; x < source.width; x++)
			{
				occupied[y * source.width + x] = (foreground.getRGB(x, y) >>> 24) != 0;
			}
		}
	}

	boolean isFresh()
	{
		return System.nanoTime() - created < 500_000_000L;
	}

	Point translate(Point point)
	{
		return hit(point) ? transform.toSource(point) : point;
	}

	boolean hit(Point point)
	{
		return transform.containsDestination(point) && occupied(transform.toSource(point));
	}

	WidgetRegion region()
	{
		return region;
	}

	private boolean occupied(Point point)
	{
		int x = point.x - source.x;
		int y = point.y - source.y;
		return occupied[y * source.width + x];
	}
}
