package com.minimapresize;

/** Native GPU UI pixels use premultiplied RGB, regardless of their Java image wrapper. */
final class UiAlpha
{
	private UiAlpha() { }

	static int over(int foreground, int background)
	{
		int alpha = foreground >>> 24;
		if (alpha == 255 || background == 0) { return foreground; }
		if (alpha == 0) { return background; }
		int inverse = 255 - alpha;
		int a = alpha + multiply(background >>> 24, inverse);
		int r = ((foreground >>> 16) & 255) + multiply((background >>> 16) & 255, inverse);
		int g = ((foreground >>> 8) & 255) + multiply((background >>> 8) & 255, inverse);
		int b = (foreground & 255) + multiply(background & 255, inverse);
		return (a << 24) | (Math.min(a, r) << 16) | (Math.min(a, g) << 8) | Math.min(a, b);
	}

	static int extract(int composed, int background)
	{
		int beforeAlpha = background >>> 24;
		int afterAlpha = composed >>> 24;
		if (beforeAlpha == 0 || beforeAlpha == 255 || afterAlpha <= beforeAlpha)
		{
			// An opaque/replaced background cannot be uniquely separated from one sample.
			return composed;
		}
		int denominator = 255 - beforeAlpha;
		int alpha = Math.min(255, ((afterAlpha - beforeAlpha) * 255 + denominator / 2) / denominator);
		int inverse = 255 - alpha;
		int r = channel(((composed >>> 16) & 255) - multiply((background >>> 16) & 255, inverse), alpha);
		int g = channel(((composed >>> 8) & 255) - multiply((background >>> 8) & 255, inverse), alpha);
		int b = channel((composed & 255) - multiply(background & 255, inverse), alpha);
		return (alpha << 24) | (r << 16) | (g << 8) | b;
	}

	private static int multiply(int value, int alpha) { return (value * alpha + 127) / 255; }
	private static int channel(int value, int alpha) { return Math.max(0, Math.min(alpha, value)); }
}
