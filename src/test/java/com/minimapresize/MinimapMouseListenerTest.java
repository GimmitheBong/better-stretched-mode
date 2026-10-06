package com.minimapresize;

import java.awt.Canvas;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinimapMouseListenerTest
{
	private Client client;
	private MinimapResizePlugin plugin;
	private MinimapMouseListener listener;
	private final Canvas canvas = new Canvas();

	@Before
	public void setup()
	{
		client = mock(Client.class);
		plugin = mock(MinimapResizePlugin.class);
		listener = new MinimapMouseListener(client, plugin);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isResized()).thenReturn(true);
		BufferedImage foreground = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 100; y++)
		{
			for (int x = 0; x < 100; x++)
			{
				foreground.setRGB(x, y, 0xff123456);
			}
		}
		when(plugin.getInputFrames()).thenReturn(Collections.singletonList(new MinimapInputFrame(
			new MinimapTransform(new Rectangle(900, 0, 100, 100), new Rectangle(800, 0, 200, 200)), foreground)));
	}

	private MouseEvent press(int x, int y)
	{
		return new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 123, InputEvent.SHIFT_DOWN_MASK,
			x, y, 1, false, MouseEvent.BUTTON1);
	}

	@Test
	public void translatedClickRetainsButtonModifiersAndTime()
	{
		MouseEvent event = listener.mousePressed(press(850, 80));
		assertEquals(new Point(925, 40), event.getPoint());
		assertEquals(MouseEvent.BUTTON1, event.getButton());
		assertEquals(123, event.getWhen());
		assertTrue(event.isShiftDown());
		assertFalse(event.isConsumed());
	}

	@Test
	public void inventoryAndOpenMenusUseTheirNormalCoordinates()
	{
		assertEquals(new Point(950, 500), listener.mousePressed(press(950, 500)).getPoint());
		when(client.isMenuOpen()).thenReturn(true);
		assertEquals(new Point(850, 80), listener.mousePressed(press(850, 80)).getPoint());
	}

	@Test
	public void fixedModeLogoutAndDisabledPluginDoNotTranslate()
	{
		when(client.isResized()).thenReturn(false);
		assertEquals(new Point(850, 80), listener.mousePressed(press(850, 80)).getPoint());
		when(client.isResized()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		assertEquals(new Point(850, 80), listener.mousePressed(press(850, 80)).getPoint());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(plugin.getInputFrames()).thenReturn(Collections.emptyList());
		assertEquals(new Point(850, 80), listener.mousePressed(press(850, 80)).getPoint());
	}

	@Test
	public void wheelCoordinatesChangeWithoutLosingPreciseRotation()
	{
		MouseWheelEvent event = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 123, 0,
			850, 80, 850, 80, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 0.25);
		assertSame(event, listener.mouseWheelMoved(event));
		assertEquals(new Point(925, 40), event.getPoint());
		assertEquals(0.25, event.getPreciseWheelRotation(), 0.0);
	}

	@Test
	public void transparentGapsAndErasedOriginalLocationsPassThroughAtRealCoordinates()
	{
		BufferedImage foreground = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		foreground.setRGB(50, 50, 0xffffffff);
		MinimapInputFrame frame = new MinimapInputFrame(new MinimapTransform(
			new Rectangle(900, 0, 100, 100), new Rectangle(850, 100, 50, 50)), foreground);
		assertEquals(new Point(950, 50), frame.translate(new Point(950, 50)));
		assertEquals(new Point(850, 100), frame.translate(new Point(850, 100)));
		assertEquals(new Point(950, 50), frame.translate(new Point(875, 125)));
		assertEquals(new Point(500, 500), frame.translate(new Point(500, 500)));
	}

	@Test
	public void sceneClicksInformTheNativeInputGateWithoutChangingOrConsumingTheEvent()
	{
		MouseEvent event = press(500, 500);
		assertSame(event, listener.mousePressed(event));
		assertEquals(new Point(500, 500), event.getPoint());
		assertFalse(event.isConsumed());
		verify(plugin).recordPointer(new Point(500, 500), null, MouseEvent.MOUSE_PRESSED, 123);
	}
}
