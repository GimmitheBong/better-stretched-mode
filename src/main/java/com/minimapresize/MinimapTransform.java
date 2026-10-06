package com.minimapresize;

import java.awt.Point;
import java.awt.Rectangle;

/** Immutable geometry shared between the rendering thread and AWT input thread. */
final class MinimapTransform
{
	private final Rectangle source;
	private final Rectangle destination;

	MinimapTransform(Rectangle source, Rectangle destination)
	{
		if (source.isEmpty() || destination.isEmpty())
		{
			throw new IllegalArgumentException("Minimap rectangles must have positive dimensions");
		}
		this.source = new Rectangle(source);
		this.destination = new Rectangle(destination);
	}

	static MinimapTransform fit(Rectangle source, int percent, int left, int down, int width, int height)
	{
		return fit(source, percent, left, down, width, height, false);
	}

	static MinimapTransform fit(Rectangle source, int percent, int left, int verticalOffset,
		int width, int height, boolean bottomAnchor)
	{
		double scale = Math.max(50, Math.min(250, percent)) / 100.0;
		scale = Math.min(scale, Math.min((double) width / source.width, (double) height / source.height));
		int w = Math.max(1, (int) Math.round(source.width * scale));
		int h = Math.max(1, (int) Math.round(source.height * scale));
		int desiredX = source.x + source.width - w - left;
		int x = Math.max(0, Math.min(width - w, desiredX));
		int desiredY = bottomAnchor ? source.y + source.height - h - verticalOffset : source.y + verticalOffset;
		int y = Math.max(0, Math.min(height - h, desiredY));
		return new MinimapTransform(source, new Rectangle(x, y, w, h));
	}

	Rectangle source()
	{
		return new Rectangle(source);
	}

	Rectangle destination()
	{
		return new Rectangle(destination);
	}

	boolean isIdentity()
	{
		return source.equals(destination);
	}

	boolean containsSource(Point point)
	{
		return source.contains(point);
	}

	boolean containsDestination(Point point)
	{
		return destination.contains(point);
	}

	Point toSource(Point point)
	{
		return new Point(source.x + (int) Math.floor((point.x - destination.x) * (double) source.width / destination.width),
			source.y + (int) Math.floor((point.y - destination.y) * (double) source.height / destination.height));
	}
}
