package com.minimapresize;

import java.awt.Point;
import java.util.List;

final class InputRoute
{
	final Point point;
	final WidgetRegion target;

	private InputRoute(Point point, WidgetRegion target)
	{
		this.point = new Point(point);
		this.target = target;
	}

	static InputRoute scene(Point point)
	{
		return new InputRoute(point, null);
	}

	static InputRoute drag(Point point, MinimapInputFrame frame)
	{
		return new InputRoute(frame.translateDrag(point), frame.region());
	}

	static InputRoute resolve(Point point, List<MinimapInputFrame> frames)
	{
		// Route once, to the topmost visible region. Never feed one transform into another.
		for (int i = frames.size() - 1; i >= 0; i--)
		{
			MinimapInputFrame frame = frames.get(i);
			if (frame.isFresh() && frame.hit(point))
			{
				return new InputRoute(frame.translate(point), frame.region());
			}
		}
		return scene(point);
	}
}
