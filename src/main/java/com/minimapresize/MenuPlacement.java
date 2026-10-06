package com.minimapresize;

import java.awt.Point;
import java.awt.Rectangle;

/** Native menu placement rules in game-buffer coordinates, after global stretch translation. */
final class MenuPlacement
{
	final Rectangle nativeBounds;
	final Rectangle visibleBounds;
	final int offsetX;
	final int offsetY;

	MenuPlacement(Rectangle nativeBounds, Point cursor, int width, int height)
	{
		this.nativeBounds = new Rectangle(nativeBounds);
		int x = Math.max(0, Math.min(Math.max(0, width - nativeBounds.width), cursor.x - nativeBounds.width / 2));
		int y = Math.max(0, Math.min(Math.max(0, height - nativeBounds.height), cursor.y));
		visibleBounds = new Rectangle(x, y, nativeBounds.width, nativeBounds.height);
		offsetX = x - nativeBounds.x;
		offsetY = y - nativeBounds.y;
	}

	Point toNative(Point point)
	{
		// Apply the same translation outside the menu too, preserving its normal close margin.
		return new Point(point.x - offsetX, point.y - offsetY);
	}
}
