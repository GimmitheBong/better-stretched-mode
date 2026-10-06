package com.minimapresize;

import java.awt.Canvas;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercise the render/input lifecycle together, including native input-property restoration. */
public class MinimapResizePluginTest
{
	@Rule public MockitoRule mocks = MockitoJUnit.rule();
	@Mock private Client client;
	@Mock private MinimapResizeConfig config;
	@Mock private OverlayManager overlayManager;
	@Mock private MouseManager mouseManager;
	@Mock private ClientThread clientThread;
	@InjectMocks private MinimapResizePlugin plugin;

	private BufferedImage image;
	private Widget map;
	private Widget upper;
	private Widget lower;

	@Before
	public void setup()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isResized()).thenReturn(true);
		when(config.scale()).thenReturn(50);
		when(config.upperTabScale()).thenReturn(50);
		when(config.lowerTabScale()).thenReturn(50);
		doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
			.when(clientThread).invokeLater(any(Runnable.class));
		image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
		MainBufferProvider buffer = mock(MainBufferProvider.class);
		when(buffer.getImage()).thenReturn(image);
		when(client.getBufferProvider()).thenReturn(buffer);
		map = widget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER, new Rectangle(750, 0, 250, 180));
		upper = widget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER, new Rectangle(760, 400, 240, 40));
		lower = widget(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER, new Rectangle(760, 660, 240, 40));
		plugin.startUp();
	}

	private Widget widget(int id, Rectangle bounds)
	{
		Widget widget = mock(Widget.class);
		when(widget.getId()).thenReturn(id);
		when(widget.getBounds()).thenReturn(bounds);
		when(widget.getNoClickThrough()).thenReturn(true);
		when(client.getWidget(id)).thenReturn(widget);
		return widget;
	}

	private void render()
	{
		Graphics2D clear = image.createGraphics();
		clear.setComposite(AlphaComposite.Clear);
		clear.fillRect(0, 0, image.getWidth(), image.getHeight());
		clear.dispose();
		plugin.onBeforeRender(null);
		plugin.captureBackground();
		Graphics2D graphics = image.createGraphics();
		try
		{
			Widget[] widgets = {map, lower, upper};
			int i = 0;
			for (WidgetRegion region : WidgetRegion.values())
			{
				graphics.setColor(Color.RED);
				graphics.fill(widgets[i++].getBounds());
				plugin.drawScaled(region, graphics);
			}
			plugin.drawOutputs(graphics);
		}
		finally
		{
			graphics.dispose();
		}
		plugin.finishRender();
	}

	@After
	public void cleanup() { plugin.shutDown(); }

	@Test
	public void everyFilterPreservesTabRoutingAndClickThroughAtOldLocations()
	{
		when(config.sharpening()).thenReturn(40);
		for (ScalingFilter filter : ScalingFilter.values())
		{
			when(config.filter()).thenReturn(filter);
			render();
			InputRoute click = InputRoute.resolve(new Point(900, 430), plugin.getInputFrames());
			assertEquals(WidgetRegion.UPPER_TABS, click.target);
			assertEquals(new Point(800, 420), click.point);
			InputRoute old = InputRoute.resolve(new Point(800, 420), plugin.getInputFrames());
			assertNull(old.target);
			assertEquals(new Point(800, 420), old.point);
			assertEquals(0, image.getRGB(800, 420));
		}
	}

	@Test
	public void shrinkingAllThreeRegionsLeavesOriginalAreaClickableAndClearsNativeBlockers()
	{
		render();
		assertEquals(3, plugin.getInputFrames().size());
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		for (Point oldLocation : Arrays.asList(new Point(800, 120), new Point(800, 420), new Point(800, 680)))
		{
			MouseEvent click = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0,
				oldLocation.x, oldLocation.y, 1, false, MouseEvent.BUTTON1);
			assertEquals(oldLocation, listener.mousePressed(click).getPoint());
			assertFalse(click.isConsumed());
			assertEquals(0, image.getRGB(oldLocation.x, oldLocation.y));
		}
		verify(map, atLeastOnce()).setNoClickThrough(false);
		verify(upper, atLeastOnce()).setNoClickThrough(false);
		verify(lower, atLeastOnce()).setNoClickThrough(false);
		assertEquals(Color.RED.getRGB(), image.getRGB(900, 40));
		assertEquals(Color.RED.getRGB(), image.getRGB(900, 430));
		assertEquals(Color.RED.getRGB(), image.getRGB(900, 690));
	}

	@Test
	public void resizedButtonTargetKeepsNativeOperationsWhileOtherRegionsPassThrough()
	{
		render();
		clearInvocations(map, upper, lower);
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		MouseEvent click = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0,
			900, 430, 1, false, MouseEvent.BUTTON1);
		assertEquals(new Point(800, 420), listener.mousePressed(click).getPoint());
		verify(upper).setNoClickThrough(true);
		verify(upper, never()).setNoClickThrough(false);
		verify(lower).setNoClickThrough(false);
		verify(map).setNoClickThrough(false);
		// A quick move away must not disable the button before the pending click is processed.
		clearInvocations(upper);
		listener.mouseMoved(new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 124, 0, 400, 400, 0, false));
		verify(upper, never()).setNoClickThrough(false);
		when(client.getMouseLastPressedMillis()).thenReturn(123L);
		plugin.onClientTick(null);
		plugin.onPostClientTick(null);
		verify(upper).setNoClickThrough(false);
	}

	@Test
	public void inputPropertiesAreRestoredBeforeRenderingScriptsAndShutdown()
	{
		render();
		clearInvocations(map, upper, lower);
		plugin.onClientTick(null);
		verify(map).setNoClickThrough(true);
		verify(upper).setNoClickThrough(true);
		verify(lower).setNoClickThrough(true);
		plugin.onPostClientTick(null);
		clearInvocations(map);
		plugin.onBeforeRender(null);
		verify(map).setNoClickThrough(true);
		plugin.finishRender();
		clearInvocations(map);
		plugin.shutDown();
		verify(map).setNoClickThrough(true);
		assertTrue(plugin.getInputFrames().isEmpty());
	}

	@Test
	public void classicLayoutDoesNotScaleOrGateTheModernTabBars()
	{
		when(client.getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER)).thenReturn(null);
		when(client.getWidget(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER)).thenReturn(map);
		when(upper.isHidden()).thenReturn(true);
		when(lower.isHidden()).thenReturn(true);
		render();
		assertEquals(1, plugin.getInputFrames().size());
		assertEquals(WidgetRegion.MINIMAP, plugin.getInputFrames().get(0).region());
		verify(upper, never()).setNoClickThrough(false);
		verify(lower, never()).setNoClickThrough(false);
	}

	@Test
	public void tabBarsCanScaleWhileTheMinimapRemainsAtNormalSize()
	{
		when(config.scale()).thenReturn(100);
		render();
		assertEquals(2, plugin.getInputFrames().size());
		assertEquals(WidgetRegion.LOWER_TABS, plugin.getInputFrames().get(0).region());
		assertEquals(WidgetRegion.UPPER_TABS, plugin.getInputFrames().get(1).region());
		verify(map, never()).setNoClickThrough(false);
		assertEquals(Color.RED.getRGB(), image.getRGB(800, 120));
	}

	@Test
	public void hundredPercentBypassesInputGateAndLayoutChangeClearsOldFrames()
	{
		when(config.scale()).thenReturn(100);
		when(config.upperTabScale()).thenReturn(100);
		when(config.lowerTabScale()).thenReturn(100);
		render();
		assertTrue(plugin.getInputFrames().isEmpty());
		verify(map, never()).setNoClickThrough(false);
		verify(upper, never()).setNoClickThrough(false);
		verify(lower, never()).setNoClickThrough(false);
		when(config.scale()).thenReturn(50);
		render();
		assertEquals(1, plugin.getInputFrames().size());
		plugin.onResizeableChanged(null);
		assertTrue(plugin.getInputFrames().isEmpty());
		verify(map, atLeastOnce()).setNoClickThrough(true);
	}
}
