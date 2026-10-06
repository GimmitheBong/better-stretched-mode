package com.minimapresize;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class InputRouteTest
{
	private MinimapInputFrame frame(WidgetRegion region, Rectangle source, Rectangle destination)
	{
		BufferedImage image = new BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.height; y++)
		{
			for (int x = 0; x < source.width; x++)
			{
				image.setRGB(x, y, 0xffffffff);
			}
		}
		return new MinimapInputFrame(region, new MinimapTransform(source, destination), image);
	}

	@Test
	public void bothTabBarsRouteIndependentlyAndTheirOldLocationsPassThrough()
	{
		MinimapInputFrame lower = frame(WidgetRegion.LOWER_TABS,
			new Rectangle(760, 660, 240, 40), new Rectangle(880, 680, 120, 20));
		MinimapInputFrame upper = frame(WidgetRegion.UPPER_TABS,
			new Rectangle(760, 400, 240, 40), new Rectangle(880, 420, 120, 20));
		InputRoute upperClick = InputRoute.resolve(new Point(900, 430), Arrays.asList(lower, upper));
		assertEquals(WidgetRegion.UPPER_TABS, upperClick.target);
		assertEquals(new Point(800, 420), upperClick.point);
		InputRoute lowerClick = InputRoute.resolve(new Point(900, 690), Arrays.asList(lower, upper));
		assertEquals(WidgetRegion.LOWER_TABS, lowerClick.target);
		assertEquals(new Point(800, 680), lowerClick.point);
		for (Point original : Arrays.asList(new Point(800, 680), new Point(800, 420)))
		{
			InputRoute scene = InputRoute.resolve(original, Arrays.asList(lower, upper));
			assertNull(scene.target);
			assertEquals(original, scene.point);
		}
	}

	@Test
	public void overlappingScaledRegionsChooseTopmostWithoutDoubleTransforming()
	{
		MinimapInputFrame lower = frame(WidgetRegion.LOWER_TABS,
			new Rectangle(700, 660, 100, 40), new Rectangle(500, 500, 100, 40));
		MinimapInputFrame upper = frame(WidgetRegion.UPPER_TABS,
			new Rectangle(500, 510, 100, 40), new Rectangle(500, 500, 100, 40));
		InputRoute route = InputRoute.resolve(new Point(520, 510), Arrays.asList(lower, upper));
		assertEquals(WidgetRegion.UPPER_TABS, route.target);
		assertEquals(new Point(520, 520), route.point);
	}
}
