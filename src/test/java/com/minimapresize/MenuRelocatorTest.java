package com.minimapresize;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MenuRelocatorTest
{
	private Client client;
	private Menu menu;
	private BufferedImage image;
	private MenuRelocator relocator;

	@Before
	public void setup()
	{
		client = mock(Client.class);
		menu = mock(Menu.class);
		when(client.getMenu()).thenReturn(menu);
		when(menu.getMenuX()).thenReturn(900);
		when(menu.getMenuY()).thenReturn(200);
		when(menu.getMenuWidth()).thenReturn(180);
		when(menu.getMenuHeight()).thenReturn(120);
		image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
		MainBufferProvider buffer = mock(MainBufferProvider.class);
		when(buffer.getImage()).thenReturn(image);
		when(client.getBufferProvider()).thenReturn(buffer);
		relocator = new MenuRelocator(client);
	}

	private void open(WidgetRegion region)
	{
		relocator.recordPress(new Point(200, 100), new Point(990, 200), region);
		when(client.isMenuOpen()).thenReturn(true);
		relocator.opened();
	}

	@Test
	public void everyScaledRegionAnchorsMenuAtActualCursorBeforeFirstRender()
	{
		for (WidgetRegion region : WidgetRegion.values())
		{
			when(client.isMenuOpen()).thenReturn(false);
			open(region);
			Point mapped = relocator.translate(new Point(210, 120));
			assertEquals(new Point(1000, 220), mapped);
			assertTrue("Moving into the visible menu must also be inside the native menu", new Rectangle(900, 200, 180, 120).contains(mapped));
		}
	}

	@Test
	public void renderingRestoresOldMenuBackgroundAndDrawsAtCursorWithoutResizing()
	{
		open(WidgetRegion.SIDE_PANEL);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(Color.BLUE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			relocator.captureBackground();
			graphics.setColor(Color.GREEN);
			graphics.fillRect(900, 200, 180, 120);
			relocator.draw(graphics);
			assertEquals(Color.BLUE.getRGB(), image.getRGB(990, 250));
			assertEquals(Color.GREEN.getRGB(), image.getRGB(110, 100));
			assertEquals(Color.GREEN.getRGB(), image.getRGB(289, 219));
			assertEquals(Color.BLUE.getRGB(), image.getRGB(290, 219));
		}
		finally { graphics.dispose(); }
		verify(menu, never()).setMenuEntries(any());
	}

	@Test
	public void matchingMenuAndBackgroundColorsStillProduceOpaqueMenu()
	{
		open(WidgetRegion.SIDE_PANEL);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(Color.GREEN);
			graphics.fillRect(900, 200, 180, 120);
			relocator.captureBackground();
			relocator.draw(graphics);
			assertEquals(Color.GREEN.getRGB(), image.getRGB(200, 110));
		}
		finally { graphics.dispose(); }
	}

	@Test
	public void sceneMenusAndExistingMenuSelectionDoNotGetANewAnchor()
	{
		relocator.recordPress(new Point(200, 100), new Point(200, 100), null);
		when(client.isMenuOpen()).thenReturn(true);
		relocator.opened();
		assertEquals(new Point(210, 120), relocator.translate(new Point(210, 120)));
		when(client.isMenuOpen()).thenReturn(false);
		open(WidgetRegion.UPPER_TABS);
		// A menu selection is a new press, but must not move an already-open menu.
		relocator.recordPress(new Point(210, 120), new Point(210, 120), null);
		assertEquals(new Point(1000, 220), relocator.translate(new Point(210, 120)));
	}

	@Test
	public void submenusMoveWithRootAndTheirOldPixelsAreRestored()
	{
		Menu child = mock(Menu.class);
		when(child.getMenuX()).thenReturn(1080);
		when(child.getMenuY()).thenReturn(210);
		when(child.getMenuWidth()).thenReturn(100);
		when(child.getMenuHeight()).thenReturn(60);
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getSubMenu()).thenReturn(child);
		when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{entry});
		open(WidgetRegion.SIDE_PANEL);
		Graphics2D graphics = image.createGraphics();
		try
		{
			relocator.captureBackground();
			graphics.setColor(Color.GREEN);
			graphics.fillRect(900, 200, 180, 120);
			graphics.setColor(Color.RED);
			graphics.fillRect(1080, 210, 100, 60);
			relocator.draw(graphics);
			assertEquals(0, image.getRGB(1100, 220));
			assertEquals(Color.RED.getRGB(), image.getRGB(310, 120));
			assertEquals(new Point(1100, 220), relocator.translate(new Point(310, 120)));
		}
		finally { graphics.dispose(); }
	}

	@Test
	public void closingOrResettingMenuRestoresNormalMouseCoordinates()
	{
		open(WidgetRegion.SIDE_PANEL);
		when(client.isMenuOpen()).thenReturn(false);
		assertEquals(new Point(200, 100), relocator.translate(new Point(200, 100)));
		when(client.isMenuOpen()).thenReturn(true);
		relocator.reset();
		assertEquals(new Point(200, 100), relocator.translate(new Point(200, 100)));
	}

	@Test
	public void normalPlacementClampsToScreenEdgesAndPreservesCloseMargin()
	{
		MenuPlacement placement = new MenuPlacement(new Rectangle(700, 100, 200, 180), new Point(990, 690), 1000, 700);
		assertEquals(new Rectangle(800, 520, 200, 180), placement.visibleBounds);
		assertEquals(new Point(890, 270), placement.toNative(new Point(990, 690)));
		assertTrue(placement.nativeBounds.contains(placement.toNative(new Point(990, 690))));
		Rectangle nativeGrace = new Rectangle(placement.nativeBounds);
		nativeGrace.grow(10, 10);
		assertTrue(nativeGrace.contains(placement.toNative(new Point(799, 600))));
		assertFalse(nativeGrace.contains(placement.toNative(new Point(789, 600))));
		MenuPlacement left = new MenuPlacement(new Rectangle(700, 100, 200, 180), new Point(5, 2), 1000, 700);
		assertEquals(new Rectangle(0, 2, 200, 180), left.visibleBounds);
	}
}
