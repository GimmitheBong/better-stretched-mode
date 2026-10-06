package com.minimapresize;

import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinimapCompositorTest
{
	@Test
	public void gpuForegroundPreservesAlphaAndRestoresOriginalBackground()
	{
		BufferedImage frame = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		frame.setRGB(0, 0, 0xff112233);
		MinimapCompositor compositor = new MinimapCompositor();
		Rectangle source = new Rectangle(10, 0, 10, 10);
		compositor.capture(frame, source);
		frame.setRGB(12, 3, 0x80800000);
		BufferedImage foreground = compositor.extractAndRestore(null);
		assertEquals(0x80800000, foreground.getRGB(2, 3));
		assertEquals(0, foreground.getRGB(0, 0));
		assertEquals(0, frame.getRGB(12, 3));
		assertEquals(0xff112233, frame.getRGB(0, 0));
		assertFalse(compositor.matches(frame, source));
	}

	@Test
	public void softwareSceneIsRemovedFromCutoutAndMapInteriorRemainsOpaque()
	{
		BufferedImage frame = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
		MinimapCompositor compositor = new MinimapCompositor();
		compositor.capture(frame, new Rectangle(10, 0, 10, 10));
		frame.setRGB(19, 0, 0xffff0000);
		BufferedImage foreground = compositor.extractAndRestore(new Ellipse2D.Double(12, 2, 6, 6));
		assertEquals(0, foreground.getRGB(0, 0));
		assertEquals(0xff000000, foreground.getRGB(5, 5));
		assertEquals(0xffff0000, foreground.getRGB(9, 0));
		assertEquals(0xff000000, frame.getRGB(19, 0));
	}

	@Test
	public void oldCaptureIsRejectedWhenBufferOrBoundsChange()
	{
		BufferedImage frame = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		MinimapCompositor compositor = new MinimapCompositor();
		Rectangle source = new Rectangle(10, 0, 10, 10);
		compositor.capture(frame, source);
		assertTrue(compositor.matches(frame, source));
		assertFalse(compositor.matches(frame, new Rectangle(9, 0, 10, 10)));
		assertFalse(compositor.matches(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), source));
		compositor.invalidate();
		assertFalse(compositor.matches(frame, source));
	}
}
