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
		when(plugin.translateMenuPoint(any(Point.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

	private void enableMiddleCamera()
	{
		when(plugin.isCameraPress(any(MouseEvent.class))).thenAnswer(invocation ->
			((MouseEvent) invocation.getArgument(0)).getButton() == MouseEvent.BUTTON2);
	}

	private MouseEvent move(int x, int y)
	{
		return new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 122, 0, x, y, 0, false);
	}

	private MouseEvent cameraPress(int x, int y, int modifiers)
	{
		return new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 123, modifiers, x, y, 1, false, MouseEvent.BUTTON2);
	}

	private MouseEvent cameraDrag(int x, int y, int modifiers)
	{
		return new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 124, modifiers, x, y, 0, false);
	}

	@Test
	public void cameraDragFromSceneKeepsRawDeltasAcrossEveryScaledRegion()
	{
		enableMiddleCamera();
		for (WidgetRegion region : WidgetRegion.values())
		{
			BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D graphics = image.createGraphics();
			graphics.setColor(java.awt.Color.WHITE);
			graphics.fillRect(0, 0, 100, 100);
			graphics.dispose();
			when(plugin.getInputFrames()).thenReturn(Collections.singletonList(new MinimapInputFrame(region,
				new MinimapTransform(new Rectangle(900, 0, 100, 100), new Rectangle(800, 0, 200, 200)), image)));
			listener.mouseMoved(move(500, 500));
			assertEquals(new Point(500, 500), listener.mousePressed(cameraPress(500, 500, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
			assertEquals(new Point(850, 80), listener.mouseDragged(cameraDrag(850, 80, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
			assertEquals(new Point(960, 90), listener.mouseDragged(cameraDrag(960, 90, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
			assertEquals(new Point(750, 120), listener.mouseDragged(cameraDrag(750, 120, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
			MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 125, 0, 850, 80, 1, false, MouseEvent.BUTTON2);
			assertEquals(new Point(850, 80), listener.mouseReleased(release).getPoint());
			assertEquals(new Point(925, 40), listener.mouseMoved(move(850, 80)).getPoint());
		}
	}

	@Test
	public void cameraDragStartedOverWidgetKeepsTheExistingHoverOffsetWithoutScalingDeltas()
	{
		enableMiddleCamera();
		assertEquals(new Point(925, 40), listener.mouseMoved(move(850, 80)).getPoint());
		assertEquals(new Point(925, 40), listener.mousePressed(cameraPress(850, 80, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
		assertEquals(new Point(935, 50), listener.mouseDragged(cameraDrag(860, 90, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
		assertEquals(new Point(825, 80), listener.mouseDragged(cameraDrag(750, 120, InputEvent.BUTTON2_DOWN_MASK)).getPoint());
		assertEquals(new Point(825, 80), listener.mouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
			125, 0, 750, 120, 1, false, MouseEvent.BUTTON2)).getPoint());
		assertEquals(new Point(750, 120), listener.mouseMoved(move(750, 120)).getPoint());
	}

	@Test
	public void remappedRightButtonAndUnremappedReleaseDoNotChangeCameraSpace()
	{
		enableMiddleCamera();
		listener.mouseMoved(move(500, 500));
		listener.mousePressed(cameraPress(500, 500, InputEvent.BUTTON3_DOWN_MASK));
		assertEquals(new Point(850, 80), listener.mouseDragged(cameraDrag(850, 80, InputEvent.BUTTON3_DOWN_MASK)).getPoint());
		assertEquals(new Point(850, 80), listener.mouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
			125, 0, 850, 80, 1, false, MouseEvent.BUTTON3)).getPoint());
		assertEquals(new Point(925, 40), listener.mouseMoved(move(850, 80)).getPoint());
	}

	@Test
	public void middleButtonRemappedToMenuStillUsesNormalMenuInput()
	{
		enableMiddleCamera();
		MouseEvent remapped = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 123,
			InputEvent.BUTTON2_DOWN_MASK, 850, 80, 1, false, MouseEvent.BUTTON3);
		assertEquals(new Point(925, 40), listener.mousePressed(remapped).getPoint());
		when(client.isMenuOpen()).thenReturn(true);
		assertEquals(new Point(850, 100), listener.mouseMoved(move(850, 100)).getPoint());
	}

	@Test
	public void focusResetOrMissingReleaseRestoresNormalWidgetRouting()
	{
		enableMiddleCamera();
		listener.mouseMoved(move(500, 500));
		listener.mousePressed(cameraPress(500, 500, InputEvent.BUTTON2_DOWN_MASK));
		listener.mouseDragged(cameraDrag(850, 80, InputEvent.BUTTON2_DOWN_MASK));
		listener.resetGestures();
		assertEquals(new Point(925, 40), listener.mouseMoved(move(850, 80)).getPoint());
		listener.mouseMoved(move(500, 500));
		listener.mousePressed(cameraPress(500, 500, InputEvent.BUTTON2_DOWN_MASK));
		assertEquals(new Point(925, 40), listener.mouseMoved(move(850, 80)).getPoint());
	}

	@Test
	public void cameraWheelInputKeepsPrecisionAndDoesNotJumpToWidgetCoordinates()
	{
		enableMiddleCamera();
		listener.mouseMoved(move(500, 500));
		listener.mousePressed(cameraPress(500, 500, InputEvent.BUTTON2_DOWN_MASK));
		MouseWheelEvent wheel = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 124, InputEvent.BUTTON2_DOWN_MASK,
			850, 80, 850, 80, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 0.25);
		assertEquals(new Point(850, 80), listener.mouseWheelMoved(wheel).getPoint());
		assertEquals(0.25, wheel.getPreciseWheelRotation(), 0.0);
		assertFalse(wheel.isConsumed());
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
	public void openMenuMovementClickAndWheelUseMenuCoordinatesInsteadOfPanelScaling()
	{
		when(client.isMenuOpen()).thenReturn(true);
		when(plugin.translateMenuPoint(any(Point.class))).thenAnswer(invocation ->
		{
			Point point = invocation.getArgument(0);
			return new Point(point.x + 75, point.y - 40);
		});
		MouseEvent move = new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 124, 0, 850, 100, 0, false);
		assertEquals(new Point(925, 60), listener.mouseMoved(move).getPoint());
		assertEquals(new Point(925, 40), listener.mousePressed(press(850, 80)).getPoint());
		MouseWheelEvent wheel = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 123, 0,
			850, 80, 850, 80, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 0.25);
		assertEquals(new Point(925, 40), listener.mouseWheelMoved(wheel).getPoint());
		assertEquals(0.25, wheel.getPreciseWheelRotation(), 0.0);
		assertFalse(wheel.isConsumed());
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
	public void transparentInventoryUsesItsWholeInteractiveRectangle()
	{
		when(plugin.getInputFrames()).thenReturn(Collections.singletonList(new MinimapInputFrame(WidgetRegion.SIDE_PANEL,
			new MinimapTransform(new Rectangle(900, 0, 100, 100), new Rectangle(800, 0, 200, 200)),
			new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB))));
		MouseWheelEvent wheel = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 123, 0,
			850, 80, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1);
		assertEquals(new Point(925, 40), listener.mouseWheelMoved(wheel).getPoint());
	}

	@Test
	public void inventoryDragAndReleaseStayInCapturedCoordinatesOutsidePanel()
	{
		when(plugin.getInputFrames()).thenReturn(Collections.singletonList(new MinimapInputFrame(WidgetRegion.SIDE_PANEL,
			new MinimapTransform(new Rectangle(900, 0, 100, 100), new Rectangle(800, 0, 200, 200)),
			new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB))));
		listener.mousePressed(press(850, 80));
		MouseEvent drag = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 124, InputEvent.BUTTON1_DOWN_MASK, 750, 220, 0, false);
		assertEquals(new Point(875, 110), listener.mouseDragged(drag).getPoint());
		MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 125, 0, 750, 220, 1, false, MouseEvent.BUTTON1);
		assertEquals(new Point(875, 110), listener.mouseReleased(release).getPoint());
		verify(plugin).recordDragTarget(WidgetRegion.SIDE_PANEL, true);
		assertEquals(new Point(750, 220), listener.mouseMoved(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, 126, 0, 750, 220, 0, false)).getPoint());
	}

	@Test
	public void closedPanelCancelsCapturedDragInsteadOfRoutingToAStaleWidget()
	{
		when(plugin.getInputFrames()).thenReturn(Collections.singletonList(new MinimapInputFrame(WidgetRegion.SIDE_PANEL,
			new MinimapTransform(new Rectangle(900, 0, 100, 100), new Rectangle(800, 0, 200, 200)),
			new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB))));
		listener.mousePressed(press(850, 80));
		when(plugin.getInputFrames()).thenReturn(Collections.emptyList());
		MouseEvent drag = new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 124, 0, 750, 220, 0, false);
		assertEquals(new Point(750, 220), listener.mouseDragged(drag).getPoint());
		verify(plugin).recordDragTarget(null, false);
	}

	@Test
	public void cachedHitMasksRemainImmutableAndRefreshWhenDisplayedAlphaChanges()
	{
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(5, 5, 0xffffffff);
		MinimapTransform transform = new MinimapTransform(new Rectangle(0, 0, 10, 10), new Rectangle(20, 20, 10, 10));
		MinimapInputFrame first = new MinimapInputFrame(WidgetRegion.MINIMAP, transform, image);
		image.setRGB(5, 5, 0);
		image.setRGB(6, 6, 0xffffffff);
		MinimapInputFrame deferred = new MinimapInputFrame(WidgetRegion.MINIMAP, transform, image, first, false);
		assertTrue(deferred.hit(new Point(25, 25)));
		assertFalse(deferred.hit(new Point(26, 26)));
		MinimapInputFrame refreshed = new MinimapInputFrame(WidgetRegion.MINIMAP, transform, image, deferred, true);
		assertFalse(refreshed.hit(new Point(25, 25)));
		assertTrue(refreshed.hit(new Point(26, 26)));
		assertTrue(first.hit(new Point(25, 25)));
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
