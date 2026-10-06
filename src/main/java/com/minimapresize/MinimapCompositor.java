package com.minimapresize;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Captures the pre-widget background and extracts changes at the map-layer hook. */
final class MinimapCompositor
{
	private BufferedImage frame;
	private Rectangle source;
	private int[] background;
	private BufferedImage foreground;

	void capture(BufferedImage frame, Rectangle source)
	{
		this.frame = frame;
		this.source = new Rectangle(source);
		int length = source.width * source.height;
		if (background == null || background.length != length)
		{
			background = new int[length];
		}
		if (foreground == null || foreground.getWidth() != source.width || foreground.getHeight() != source.height)
		{
			foreground = new BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB);
		}
		frame.getRGB(source.x, source.y, source.width, source.height, background, 0, source.width);
	}

	boolean matches(BufferedImage frame, Rectangle source)
	{
		return this.frame == frame && source.equals(this.source);
	}

	BufferedImage extractAndRestore(Shape opaqueMapInterior)
	{
		int[] pixels = ((DataBufferInt) foreground.getRaster().getDataBuffer()).getData();
		frame.getRGB(source.x, source.y, source.width, source.height, pixels, 0, source.width);
		for (int y = 0, i = 0; y < source.height; y++)
		{
			for (int x = 0; x < source.width; x++, i++)
			{
				// The software renderer has no separate UI alpha channel. Preserve the map's
				// opaque interior even when a map pixel happens to equal the scene beneath it.
				if (pixels[i] == background[i]
					&& (opaqueMapInterior == null || !opaqueMapInterior.contains(source.x + x + 0.5, source.y + y + 0.5)))
				{
					pixels[i] = 0;
				}
			}
		}
		// Replace, rather than alpha-blend, so transparent GPU background is restored too.
		frame.setRGB(source.x, source.y, source.width, source.height, background, 0, source.width);
		frame = null;
		return foreground;
	}

	void invalidate()
	{
		frame = null;
	}

	void clear()
	{
		frame = null;
		source = null;
		background = null;
		foreground = null;
	}
}
