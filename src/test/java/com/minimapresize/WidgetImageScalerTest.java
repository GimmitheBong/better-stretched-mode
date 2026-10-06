package com.minimapresize;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.junit.Test;
import static org.junit.Assert.*;

public class WidgetImageScalerTest
{
	private BufferedImage detailImage()
	{
		BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int value = (x / 2) % 2 == 0 ? 48 : 208;
				image.setRGB(x, y, 0xff000000 | value * 0x010101);
			}
		}
		return image;
	}

	private int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private long edgeContrast(BufferedImage image)
	{
		long contrast = 0;
		for (int x = 1; x < image.getWidth(); x++)
		{
			int delta = (image.getRGB(x, image.getHeight() / 2) & 255)
				- (image.getRGB(x - 1, image.getHeight() / 2) & 255);
			contrast += delta * delta;
		}
		return contrast;
	}

	@Test
	public void sharpBicubicIncreasesFineEdgeContrastAndStrengthIsAdjustable()
	{
		BufferedImage source = detailImage();
		WidgetImageScaler scaler = new WidgetImageScaler();
		long plain = edgeContrast(scaler.scale(source, 24, 12, ScalingFilter.BICUBIC, 40));
		long mild = edgeContrast(scaler.scale(source, 24, 12, ScalingFilter.SHARP_BICUBIC, 40));
		long strong = edgeContrast(scaler.scale(source, 24, 12, ScalingFilter.SHARP_BICUBIC, 100));
		assertTrue("Sharpening should increase edge contrast over plain Bicubic", mild > plain);
		assertTrue("Higher strength should increase contrast on this detail pattern", strong > mild);
	}

	@Test
	public void zeroStrengthMatchesPlainBicubicAndOtherModesIgnoreStrength()
	{
		WidgetImageScaler scaler = new WidgetImageScaler();
		BufferedImage source = detailImage();
		int[] bicubic = pixels(scaler.scale(source, 11, 5, ScalingFilter.BICUBIC, 100));
		assertArrayEquals(bicubic, pixels(scaler.scale(source, 11, 5, ScalingFilter.SHARP_BICUBIC, 0)));
		for (ScalingFilter filter : new ScalingFilter[]{ScalingFilter.NEAREST, ScalingFilter.BILINEAR, ScalingFilter.BICUBIC})
		{
			int[] without = pixels(scaler.scale(source, 24, 12, filter, 0));
			assertArrayEquals(without, pixels(scaler.scale(source, 24, 12, filter, 100)));
		}
	}

	@Test
	public void sharpeningPreservesAlphaAndValidPremultipliedColors()
	{
		BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y < 7; y++)
		{
			for (int x = 1; x < 7; x++)
			{
				source.setRGB(x, y, (x % 2 == 0 ? 0x80 : 0xff) << 24 | 0x33cc88);
			}
		}
		WidgetImageScaler scaler = new WidgetImageScaler();
		int[] plain = pixels(scaler.scale(source, 13, 13, ScalingFilter.BICUBIC, 0));
		BufferedImage sharp = scaler.scale(source, 13, 13, ScalingFilter.SHARP_BICUBIC, 100);
		int[] sharpened = pixels(sharp);
		int[] premultiplied = ((DataBufferInt) sharp.getRaster().getDataBuffer()).getData();
		for (int i = 0; i < plain.length; i++)
		{
			int alpha = plain[i] >>> 24;
			assertEquals(alpha, sharpened[i] >>> 24);
			assertTrue(((premultiplied[i] >>> 16) & 255) <= alpha);
			assertTrue(((premultiplied[i] >>> 8) & 255) <= alpha);
			assertTrue((premultiplied[i] & 255) <= alpha);
			if (alpha == 0)
			{
				assertEquals(0, premultiplied[i]);
			}
		}
	}

	@Test
	public void transparentBordersDoNotBecomeDarkOrPickUpHiddenPixelColors()
	{
		BufferedImage source = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 5; y++)
		{
			for (int x = 0; x < 5; x++)
			{
				source.setRGB(x, y, x > 0 && x < 4 && y > 0 && y < 4 ? 0xffffffff : 0x00ff0000);
			}
		}
		WidgetImageScaler scaler = new WidgetImageScaler();
		BufferedImage output = scaler.scale(source, 9, 9, ScalingFilter.SHARP_BICUBIC, 100);
		for (int pixel : pixels(output))
		{
			if ((pixel >>> 24) > 0)
			{
				assertEquals("White edges should stay white at every coverage", 0xffffff, pixel & 0xffffff);
			}
		}
	}

	@Test
	public void reusedBuffersHaveNoOldFrameGhostsAndDoNotChangeSource()
	{
		BufferedImage source = detailImage();
		int[] original = pixels(source);
		WidgetImageScaler scaler = new WidgetImageScaler();
		BufferedImage first = scaler.scale(source, 24, 12, ScalingFilter.SHARP_BICUBIC, 40);
		assertArrayEquals(original, pixels(source));
		BufferedImage transparent = new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB);
		BufferedImage next = scaler.scale(transparent, 24, 12, ScalingFilter.SHARP_BICUBIC, 40);
		assertSame(first, next);
		assertArrayEquals(new int[24 * 12], pixels(next));
		scaler.clear();
		assertNotSame(first, scaler.scale(source, 24, 12, ScalingFilter.SHARP_BICUBIC, 40));
	}

	@Test
	public void oneToOneMovingDoesNotFilterOrSharpenOriginalImage()
	{
		BufferedImage source = detailImage();
		WidgetImageScaler scaler = new WidgetImageScaler();
		for (ScalingFilter filter : ScalingFilter.values())
		{
			assertSame(source, scaler.scale(source, 16, 8, filter, 100));
		}
	}

	@Test
	public void defaultFilterKeepsThePreviousSmoothSetting()
	{
		MinimapResizeConfig oldSmooth = new MinimapResizeConfig()
		{
			@Override public boolean smooth() { return true; }
		};
		assertEquals(ScalingFilter.BILINEAR, oldSmooth.filter());
		assertEquals(ScalingFilter.NEAREST, new MinimapResizeConfig() {}.filter());
	}
}
