package com.minimapresize;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Blend into the native UI buffer directly so Java2D cannot reinterpret its alpha encoding. */
final class UiBlitter
{
	private UiBlitter() { }

	static void draw(BufferedImage target, BufferedImage source, int x, int y, Graphics2D graphics, boolean nativePremultiplied)
	{
		PackedPixels destination = nativePremultiplied && target != null ? PackedPixels.nativePixels(target) : null;
		PackedPixels input = source.getType() == BufferedImage.TYPE_INT_ARGB_PRE ? PackedPixels.nativePixels(source) : null;
		if (destination == null || input == null)
		{
			graphics.drawImage(source, x, y, null);
			return;
		}
		int firstX = Math.max(0, -x), firstY = Math.max(0, -y);
		int lastX = Math.min(source.getWidth(), target.getWidth() - x);
		int lastY = Math.min(source.getHeight(), target.getHeight() - y);
		for (int sy = firstY; sy < lastY; sy++)
		{
			int src = input.index(firstX, sy);
			int dst = destination.index(x + firstX, y + sy);
			for (int sx = firstX; sx < lastX; sx++, src++, dst++)
			{
				destination.pixels[dst] = UiAlpha.over(input.pixels[src], destination.pixels[dst]);
			}
		}
	}
}
