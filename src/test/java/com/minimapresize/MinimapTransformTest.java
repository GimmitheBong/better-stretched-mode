package com.minimapresize;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinimapTransformTest
{
	@Test
	public void enlargementKeepsTopRightAnchorAndMapsClicks()
	{
		MinimapTransform transform = MinimapTransform.fit(new Rectangle(750, 0, 250, 180), 150, 0, 0, 1000, 700);
		assertEquals(new Rectangle(625, 0, 375, 270), transform.destination());
		assertEquals(new Point(750, 0), transform.toSource(new Point(625, 0)));
		assertEquals(new Point(875, 90), transform.toSource(new Point(813, 135)));
		assertEquals(new Point(999, 179), transform.toSource(new Point(999, 269)));
		assertFalse(transform.containsDestination(new Point(1000, 270)));
	}

	@Test
	public void shrinkAndOffsetsAreClampedInsideCanvas()
	{
		MinimapTransform small = MinimapTransform.fit(new Rectangle(750, 0, 250, 180), 50, 20, 30, 1000, 700);
		assertEquals(new Rectangle(855, 30, 125, 90), small.destination());
		assertEquals(new Point(998, 178), small.toSource(new Point(979, 119)));
		MinimapTransform huge = MinimapTransform.fit(new Rectangle(150, 0, 250, 180), 250, 2000, 2000, 400, 250);
		assertTrue(new Rectangle(400, 250).contains(huge.destination()));
		assertEquals(250, huge.destination().height);
	}

	@Test
	public void tabBarsKeepTheirBottomRightAnchor()
	{
		Rectangle source = new Rectangle(760, 660, 240, 40);
		MinimapTransform transform = MinimapTransform.fit(source, 50, 20, 10, 1000, 700, true);
		assertEquals(new Rectangle(860, 670, 120, 20), transform.destination());
		assertEquals(new Point(760, 660), transform.toSource(new Point(860, 670)));
		assertEquals(new Point(998, 698), transform.toSource(new Point(979, 689)));
	}

	@Test
	public void geometryCannotBeMutatedAfterPublication()
	{
		Rectangle source = new Rectangle(100, 0, 100, 100);
		MinimapTransform transform = new MinimapTransform(source, source);
		source.x = 0;
		transform.source().x = 0;
		transform.destination().x = 0;
		assertEquals(new Rectangle(100, 0, 100, 100), transform.source());
		assertTrue(transform.isIdentity());
	}
}
