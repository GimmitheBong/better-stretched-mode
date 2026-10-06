package com.minimapresize;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Per-region reusable filtering buffers. Only the image changes; input geometry does not. */
final class WidgetImageScaler
{
	private BufferedImage scaled;
	private BufferedImage sharpened;

	BufferedImage scale(BufferedImage source, int width, int height, ScalingFilter filter, int sharpening)
	{
		if (width <= 0 || height <= 0)
		{
			throw new IllegalArgumentException("Scaled image dimensions must be positive");
		}
		if (width == source.getWidth() && height == source.getHeight())
		{
			return source;
		}
		if (scaled == null || scaled.getWidth() != width || scaled.getHeight() != height)
		{
			// Filter premultiplied colors so transparent pixels cannot bleed their RGB
			// into the visible border. Reuse these buffers instead of allocating per frame.
			scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
			sharpened = null;
		}
		Graphics2D graphics = scaled.createGraphics();
		try
		{
			// Replace every pixel, including transparency; never accumulate old frames.
			graphics.setComposite(AlphaComposite.Src);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, filter.interpolation);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally
		{
			graphics.dispose();
		}
		int strength = Math.max(0, Math.min(100, sharpening));
		if (filter != ScalingFilter.SHARP_BICUBIC || strength == 0)
		{
			return scaled;
		}
		if (sharpened == null)
		{
			sharpened = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
		}
		sharpen(strength / 100.0);
		return sharpened;
	}

	private void sharpen(double amount)
	{
		int width = scaled.getWidth();
		int height = scaled.getHeight();
		int[] input = ((DataBufferInt) scaled.getRaster().getDataBuffer()).getData();
		int[] output = ((DataBufferInt) sharpened.getRaster().getDataBuffer()).getData();
		for (int y = 0, i = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++, i++)
			{
				int pixel = input[i];
				int alpha = pixel >>> 24;
				if (alpha == 0)
				{
					output[i] = 0;
					continue;
				}
				int sumAlpha = 0;
				int sumRed = 0;
				int sumGreen = 0;
				int sumBlue = 0;
				// A small Gaussian unsharp mask restores local contrast after bicubic
				// interpolation. Weight by coverage so transparent neighbors do not
				// create dark outlines. Preserve coverage itself exactly.
				for (int dy = -1; dy <= 1; dy++)
				{
					int row = Math.max(0, Math.min(height - 1, y + dy)) * width;
					for (int dx = -1; dx <= 1; dx++)
					{
						int neighbor = input[row + Math.max(0, Math.min(width - 1, x + dx))];
						int weight = (dy == 0 ? 2 : 1) * (dx == 0 ? 2 : 1);
						sumAlpha += weight * (neighbor >>> 24);
						sumRed += weight * ((neighbor >>> 16) & 255);
						sumGreen += weight * ((neighbor >>> 8) & 255);
						sumBlue += weight * (neighbor & 255);
					}
				}
				double normalization = (double) alpha / sumAlpha;
				int red = sharpenChannel((pixel >>> 16) & 255, sumRed * normalization, alpha, amount);
				int green = sharpenChannel((pixel >>> 8) & 255, sumGreen * normalization, alpha, amount);
				int blue = sharpenChannel(pixel & 255, sumBlue * normalization, alpha, amount);
				output[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
			}
		}
	}

	private static int sharpenChannel(int center, double blurred, int alpha, double amount)
	{
		return Math.max(0, Math.min(alpha, (int) Math.round(center + amount * (center - blurred))));
	}

	void clear()
	{
		scaled = null;
		sharpened = null;
	}
}
