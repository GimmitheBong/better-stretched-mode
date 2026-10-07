package com.minimapresize;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiAlphaTest
{
	private static int[] data(BufferedImage image)
	{
		return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}

	@Test
	public void nativePremultipliedPixelsStayPremultipliedAcrossEveryFilter()
	{
		for (int type : new int[]{BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE})
		{
			BufferedImage nativeFrame = new BufferedImage(12, 12, type);
			MinimapCompositor compositor = new MinimapCompositor();
			compositor.capture(nativeFrame, new Rectangle(2, 2, 8, 8), true);
			int color = 0x6a2a343c;
			for (int y = 2; y < 10; y++)
			{
				for (int x = 2; x < 10; x++) { data(nativeFrame)[y * 12 + x] = color; }
			}
			BufferedImage foreground = compositor.extractAndRestore(null);
			assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, foreground.getType());
			assertEquals(color, data(foreground)[4 * 8 + 4]);
			for (ScalingFilter filter : ScalingFilter.values())
			{
				BufferedImage scaled = new WidgetImageScaler().scale(foreground, 12, 12, filter, 40);
				assertEquals("Interior alpha/color should stay constant for " + filter, color, data(scaled)[6 * 12 + 6]);
			}
			assertEquals(0, data(nativeFrame)[3 * 12 + 3]);
		}
	}

	@Test
	public void premultipliedSubimageFilteringDoesNotUnpremultiplyOrDarkenItsPixels()
	{
		BufferedImage parent = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB_PRE);
		BufferedImage source = parent.getSubimage(4, 5, 8, 8);
		PackedPixels packed = PackedPixels.nativePixels(source);
		for (int y = 0; y < 8; y++)
		{
			for (int x = 0; x < 8; x++) { packed.pixels[packed.index(x, y)] = 0x6a303030; }
		}
		for (ScalingFilter filter : ScalingFilter.values())
		{
			BufferedImage scaled = new WidgetImageScaler().scale(source, 12, 12, filter, 40);
			assertEquals(0x6a303030, data(scaled)[6 * 12 + 6]);
		}
	}

	@Test
	public void bufferEncodingSwitchInvalidatesCachedCoverageEvenIfTheNewLayerIsEmpty()
	{
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		MinimapCompositor compositor = new MinimapCompositor();
		Rectangle source = new Rectangle(2, 2, 8, 8);
		compositor.capture(image, source, false);
		data(image)[3 * 12 + 3] = 0xff808080;
		compositor.extractAndRestore(null);
		compositor.capture(image, source, true);
		compositor.extractAndRestore(null);
		assertTrue(compositor.changed());
		assertTrue(compositor.maskChanged());
	}

	@Test
	public void extractedLayerExcludesAnAlreadyCompositedUiBackground()
	{
		BufferedImage frame = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		int background = 0x50202020;
		int panel = 0x6a303030;
		java.util.Arrays.fill(data(frame), background);
		MinimapCompositor compositor = new MinimapCompositor();
		compositor.capture(frame, new Rectangle(2, 2, 8, 8), true);
		int composed = UiAlpha.over(panel, background);
		data(frame)[3 * 12 + 3] = composed;
		int extracted = data(compositor.extractAndRestore(null))[1 * 8 + 1];
		assertPixelNear(panel, extracted, 1);
		assertPixelNear(composed, UiAlpha.over(extracted, background), 1);
		assertEquals(background, data(frame)[3 * 12 + 3]);
	}

	@Test
	public void drawingIntoDeclaredRgbGpuBufferRetainsAlphaInsteadOfMakingPanelOpaque()
	{
		BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB_PRE);
		int panel = 0x6a303030;
		java.util.Arrays.fill(data(source), panel);
		Graphics2D graphics = target.createGraphics();
		try { UiBlitter.draw(target, source, 2, 2, graphics, true); }
		finally { graphics.dispose(); }
		assertEquals(panel, data(target)[3 * 10 + 3]);
	}

	@Test
	public void drawingOverExistingTranslucentUiDoesNotMultiplyColorOrAlphaTwice()
	{
		BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB_PRE);
		int background = 0x50202020;
		int panel = 0x6a303030;
		java.util.Arrays.fill(data(target), background);
		java.util.Arrays.fill(data(source), panel);
		Graphics2D graphics = target.createGraphics();
		try { UiBlitter.draw(target, source, 2, 2, graphics, true); }
		finally { graphics.dispose(); }
		assertEquals(UiAlpha.over(panel, background), data(target)[3 * 10 + 3]);
	}

	@Test
	public void opaqueBorderAndTransparentPaddingRetainTheirNativeColors()
	{
		BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB_PRE);
		int background = 0x6a202020;
		java.util.Arrays.fill(data(target), background);
		data(source)[0] = 0xffa08060;
		Graphics2D graphics = target.createGraphics();
		try { UiBlitter.draw(target, source, 2, 2, graphics, true); }
		finally { graphics.dispose(); }
		assertEquals(0xffa08060, data(target)[2 * 10 + 2]);
		assertEquals(background, data(target)[2 * 10 + 3]);
	}

	@Test
	public void clippingAndRgbSoftwareDrawingStillWork()
	{
		BufferedImage target = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB_PRE);
		java.util.Arrays.fill(data(source), 0x80404040);
		Graphics2D graphics = target.createGraphics();
		try { UiBlitter.draw(target, source, -2, -2, graphics, true); }
		finally { graphics.dispose(); }
		assertEquals(0x80404040, data(target)[0]);
		assertEquals(0, data(target)[3 * 4 + 3]);
		BufferedImage software = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		graphics = software.createGraphics();
		try { UiBlitter.draw(software, source, 0, 0, graphics, false); }
		finally { graphics.dispose(); }
		assertEquals(0xff404040, software.getRGB(0, 0));
	}

	private static void assertPixelNear(int expected, int actual, int tolerance)
	{
		for (int shift : new int[]{0, 8, 16, 24})
		{
			assertTrue(Math.abs(((expected >>> shift) & 255) - ((actual >>> shift) & 255)) <= tolerance);
		}
	}
}
