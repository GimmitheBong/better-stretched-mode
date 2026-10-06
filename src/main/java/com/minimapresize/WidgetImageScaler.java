package com.minimapresize;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Cached, separable fixed-point resampling; avoids Java2D's expensive per-frame transform path. */
final class WidgetImageScaler
{
	private static final int ONE = 4096;
	private BufferedImage scaled;
	private BufferedImage sharpened;
	private BufferedImage lastSource;
	private BufferedImage lastResult;
	private ScalingFilter lastFilter;
	private int lastStrength;
	private int sourceWidth;
	private int sourceHeight;
	private int taps;
	private int[] premultiplied;
	private int[] horizontal;
	private int[] blurHorizontal;
	private Axis xAxis;
	private Axis yAxis;
	private long lastScaledNanos;
	private boolean pendingChange;
	private boolean updated;

	private static final class Axis
	{
		final int[] positions;
		final int[] weights;

		Axis(int source, int destination, int taps)
		{
			positions = new int[destination * taps];
			weights = new int[positions.length];
			for (int i = 0; i < destination; i++)
			{
				double coordinate = (i + 0.5) * source / destination - 0.5;
				int floor = (int) Math.floor(coordinate);
				int total = 0;
				for (int j = 0; j < taps; j++)
				{
					int sample = taps == 1 ? (int) Math.floor(coordinate + 0.5) : floor + j - (taps == 4 ? 1 : 0);
					int weight = taps == 1 ? ONE : taps == 2
						? (int) Math.round((j == 0 ? 1 - (coordinate - floor) : coordinate - floor) * ONE)
						: (int) Math.round(cubic(coordinate - sample) * ONE);
					if (j == taps - 1) { weight = ONE - total; }
					positions[i * taps + j] = Math.max(0, Math.min(source - 1, sample));
					weights[i * taps + j] = weight;
					total += weight;
				}
			}
		}

		private static double cubic(double distance)
		{
			double x = Math.abs(distance);
			return x <= 1 ? (1.5 * x - 2.5) * x * x + 1 : x < 2 ? ((-0.5 * x + 2.5) * x - 4) * x + 2 : 0;
		}
	}

	BufferedImage scale(BufferedImage source, int width, int height, ScalingFilter filter, int sharpening)
	{
		return scale(source, width, height, filter, sharpening, true);
	}

	BufferedImage scale(BufferedImage source, int width, int height, ScalingFilter filter, int sharpening, boolean pixelsChanged)
	{
		return scale(source, width, height, filter, sharpening, pixelsChanged, 0);
	}

	BufferedImage scale(BufferedImage source, int width, int height, ScalingFilter filter, int sharpening,
		boolean pixelsChanged, int refreshLimit)
	{
		updated = true;
		if (width <= 0 || height <= 0) { throw new IllegalArgumentException("Scaled image dimensions must be positive"); }
		if (width == source.getWidth() && height == source.getHeight())
		{
			lastSource = null;
			return source;
		}
		int strength = filter == ScalingFilter.SHARP_BICUBIC ? Math.max(0, Math.min(100, sharpening)) : 0;
		pendingChange |= pixelsChanged;
		long now = System.nanoTime();
		if (lastSource == source && lastFilter == filter && lastStrength == strength
			&& lastResult != null && lastResult.getWidth() == width && lastResult.getHeight() == height)
		{
			if (!pendingChange || (refreshLimit > 0 && now - lastScaledNanos < 1_000_000_000L / Math.min(240, refreshLimit)))
			{
				updated = false;
				return lastResult;
			}
		}
		int sampleTaps = filter == ScalingFilter.NEAREST ? 1 : filter == ScalingFilter.BILINEAR ? 2 : 4;
		if (scaled == null || scaled.getWidth() != width || scaled.getHeight() != height
			|| sourceWidth != source.getWidth() || sourceHeight != source.getHeight() || taps != sampleTaps)
		{
			scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
			sharpened = null;
			blurHorizontal = null;
			sourceWidth = source.getWidth();
			sourceHeight = source.getHeight();
			taps = sampleTaps;
			xAxis = new Axis(sourceWidth, width, taps);
			yAxis = new Axis(sourceHeight, height, taps);
			premultiplied = new int[sourceWidth * sourceHeight];
			horizontal = taps == 1 ? null : new int[width * sourceHeight * 4];
		}
		premultiply(source);
		resample(width, height);
		if (strength != 0)
		{
			if (sharpened == null)
			{
				sharpened = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
				blurHorizontal = new int[width * height * 4];
			}
			sharpen(strength / 100.0);
		}
		lastSource = source;
		lastFilter = filter;
		lastStrength = strength;
		lastResult = strength == 0 ? scaled : sharpened;
		lastScaledNanos = now;
		pendingChange = false;
		return lastResult;
	}

	boolean updated() { return updated; }

	private void premultiply(BufferedImage source)
	{
		int[] pixels = PackedPixels.straightArgb(source);
		for (int i = 0; i < pixels.length; i++)
		{
			int pixel = pixels[i];
			int alpha = pixel >>> 24;
			premultiplied[i] = alpha == 255 ? pixel : alpha == 0 ? 0 : (alpha << 24)
				| ((((pixel >>> 16) & 255) * alpha + 127) / 255 << 16)
				| ((((pixel >>> 8) & 255) * alpha + 127) / 255 << 8)
				| ((pixel & 255) * alpha + 127) / 255;
		}
	}

	private void resample(int width, int height)
	{
		int[] output = data(scaled);
		if (taps == 1)
		{
			for (int y = 0, i = 0; y < height; y++)
			{
				int row = yAxis.positions[y] * sourceWidth;
				for (int x = 0; x < width; x++, i++) { output[i] = premultiplied[row + xAxis.positions[x]]; }
			}
			return;
		}
		for (int y = 0, i = 0; y < sourceHeight; y++)
		{
			int row = y * sourceWidth;
			for (int x = 0; x < width; x++, i += 4)
			{
				int axis = x * taps;
				int p0 = premultiplied[row + xAxis.positions[axis]], w0 = xAxis.weights[axis];
				int p1 = premultiplied[row + xAxis.positions[axis + 1]], w1 = xAxis.weights[axis + 1];
				int p2 = taps == 4 ? premultiplied[row + xAxis.positions[axis + 2]] : 0, w2 = taps == 4 ? xAxis.weights[axis + 2] : 0;
				int p3 = taps == 4 ? premultiplied[row + xAxis.positions[axis + 3]] : 0, w3 = taps == 4 ? xAxis.weights[axis + 3] : 0;
				horizontal[i] = ((p0 >>> 24) * w0 + (p1 >>> 24) * w1 + (p2 >>> 24) * w2 + (p3 >>> 24) * w3 + ONE / 2) >> 12;
				horizontal[i + 1] = (((p0 >>> 16) & 255) * w0 + ((p1 >>> 16) & 255) * w1 + ((p2 >>> 16) & 255) * w2 + ((p3 >>> 16) & 255) * w3 + ONE / 2) >> 12;
				horizontal[i + 2] = (((p0 >>> 8) & 255) * w0 + ((p1 >>> 8) & 255) * w1 + ((p2 >>> 8) & 255) * w2 + ((p3 >>> 8) & 255) * w3 + ONE / 2) >> 12;
				horizontal[i + 3] = ((p0 & 255) * w0 + (p1 & 255) * w1 + (p2 & 255) * w2 + (p3 & 255) * w3 + ONE / 2) >> 12;
			}
		}
		for (int y = 0, i = 0; y < height; y++)
		{
			int axis = y * taps;
			int row0 = yAxis.positions[axis] * width * 4, w0 = yAxis.weights[axis];
			int row1 = yAxis.positions[axis + 1] * width * 4, w1 = yAxis.weights[axis + 1];
			int row2 = taps == 4 ? yAxis.positions[axis + 2] * width * 4 : row0, w2 = taps == 4 ? yAxis.weights[axis + 2] : 0;
			int row3 = taps == 4 ? yAxis.positions[axis + 3] * width * 4 : row0, w3 = taps == 4 ? yAxis.weights[axis + 3] : 0;
			for (int x = 0; x < width; x++, i++)
			{
				int offset = x * 4;
				int alpha = clamp(weighted(row0, row1, row2, row3, offset, w0, w1, w2, w3), 255);
				output[i] = (alpha << 24) | (clamp(weighted(row0, row1, row2, row3, offset + 1, w0, w1, w2, w3), alpha) << 16)
					| (clamp(weighted(row0, row1, row2, row3, offset + 2, w0, w1, w2, w3), alpha) << 8)
					| clamp(weighted(row0, row1, row2, row3, offset + 3, w0, w1, w2, w3), alpha);
			}
		}
	}

	private int weighted(int row0, int row1, int row2, int row3, int offset, int w0, int w1, int w2, int w3)
	{
		return (horizontal[row0 + offset] * w0 + horizontal[row1 + offset] * w1
			+ horizontal[row2 + offset] * w2 + horizontal[row3 + offset] * w3 + ONE / 2) >> 12;
	}

	private void sharpen(double amount)
	{
		int width = scaled.getWidth();
		int height = scaled.getHeight();
		int[] input = data(scaled);
		int[] output = data(sharpened);
		// Separable [1,2,1] Gaussian: six samples instead of nine, preserving alpha-aware weights.
		for (int y = 0, i = 0; y < height; y++)
		{
			int row = y * width;
			for (int x = 0; x < width; x++, i += 4)
			{
				int left = input[row + Math.max(0, x - 1)];
				int center = input[row + x];
				int right = input[row + Math.min(width - 1, x + 1)];
				blurHorizontal[i] = (left >>> 24) + 2 * (center >>> 24) + (right >>> 24);
				blurHorizontal[i + 1] = ((left >>> 16) & 255) + 2 * ((center >>> 16) & 255) + ((right >>> 16) & 255);
				blurHorizontal[i + 2] = ((left >>> 8) & 255) + 2 * ((center >>> 8) & 255) + ((right >>> 8) & 255);
				blurHorizontal[i + 3] = (left & 255) + 2 * (center & 255) + (right & 255);
			}
		}
		for (int y = 0, i = 0; y < height; y++)
		{
			int above = Math.max(0, y - 1) * width * 4;
			int row = y * width * 4;
			int below = Math.min(height - 1, y + 1) * width * 4;
			for (int x = 0; x < width; x++, i++)
			{
				int pixel = input[i], alpha = pixel >>> 24;
				if (alpha == 0) { output[i] = 0; continue; }
				int offset = x * 4;
				int sumAlpha = blurHorizontal[above + offset] + 2 * blurHorizontal[row + offset] + blurHorizontal[below + offset];
				double normalization = (double) alpha / sumAlpha;
				int r = channel((pixel >>> 16) & 255, verticalSum(above, row, below, offset + 1) * normalization, alpha, amount);
				int g = channel((pixel >>> 8) & 255, verticalSum(above, row, below, offset + 2) * normalization, alpha, amount);
				int b = channel(pixel & 255, verticalSum(above, row, below, offset + 3) * normalization, alpha, amount);
				output[i] = (alpha << 24) | (r << 16) | (g << 8) | b;
			}
		}
	}

	private int verticalSum(int above, int row, int below, int offset)
	{
		return blurHorizontal[above + offset] + 2 * blurHorizontal[row + offset] + blurHorizontal[below + offset];
	}

	private static int channel(int center, double blurred, int alpha, double amount)
	{
		return clamp((int) Math.round(center + amount * (center - blurred)), alpha);
	}

	private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }
	private static int[] data(BufferedImage image) { return ((DataBufferInt) image.getRaster().getDataBuffer()).getData(); }

	void clear()
	{
		scaled = null;
		sharpened = null;
		lastSource = null;
		lastResult = null;
		premultiplied = null;
		horizontal = null;
		blurHorizontal = null;
		xAxis = null;
		yAxis = null;
	}
}
