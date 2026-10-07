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
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.Menu;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.camera.CameraConfig;
import net.runelite.client.plugins.camera.CameraPlugin;
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
	@Mock private ConfigManager configManager;
	@Mock private PluginManager pluginManager;
	@Mock private CameraConfig cameraConfig;
	@InjectMocks private MinimapResizePlugin plugin;

	private BufferedImage image;
	private Widget map;
	private Widget upper;
	private Widget lower;
	private Widget panel;
	private Widget classicMap;
	private Widget classicPanel;
	private final Map<WidgetRegion, Widget> widgets = new EnumMap<>(WidgetRegion.class);
	private final Map<WidgetRegion, Widget> classicWidgets = new EnumMap<>(WidgetRegion.class);

	@Before
	public void setup()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.isResized()).thenReturn(true);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
		when(config.scale()).thenReturn(50);
		when(config.upperTabScale()).thenReturn(50);
		when(config.lowerTabScale()).thenReturn(50);
		when(config.panelScale()).thenReturn(100);
		when(configManager.getConfig(CameraConfig.class)).thenReturn(cameraConfig);
		doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
			.when(clientThread).invokeLater(any(Runnable.class));
		image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
		MainBufferProvider buffer = mock(MainBufferProvider.class);
		when(buffer.getImage()).thenReturn(image);
		when(client.getBufferProvider()).thenReturn(buffer);
		map = widget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER, new Rectangle(750, 0, 250, 180));
		upper = widget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER, new Rectangle(760, 400, 240, 40));
		lower = widget(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER, new Rectangle(760, 660, 240, 40));
		panel = widget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER, new Rectangle(790, 440, 200, 220));
		// Deliberately keep both roots cached and "visible" to reproduce the reported bug.
		classicMap = widget(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, new Rectangle(750, 0, 250, 180));
		classicPanel = widget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU, new Rectangle(760, 400, 240, 300));
		classicWidgets.put(WidgetRegion.MINIMAP, classicMap);
		classicWidgets.put(WidgetRegion.SIDE_PANEL, classicPanel);
		widgets.put(WidgetRegion.MINIMAP, map);
		widgets.put(WidgetRegion.LOWER_TABS, lower);
		widgets.put(WidgetRegion.UPPER_TABS, upper);
		widgets.put(WidgetRegion.SIDE_PANEL, panel);
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
		render(WidgetRegion.values());
	}

	private void render(WidgetRegion... order)
	{
		render(true, order);
	}

	private void render(boolean drawMap, WidgetRegion... order)
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
			for (WidgetRegion region : order)
			{
				Widget widget = client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_OSRS_STRETCH
					? classicWidgets.get(region) : client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_PRE_EOC
					? widgets.get(region) : null;
				if (widget != null && !widget.isHidden())
				{
					if (drawMap || region != WidgetRegion.MINIMAP)
					{
						graphics.setColor(widget == classicPanel ? Color.BLUE : Color.RED);
						graphics.fill(widget.getBounds());
					}
					plugin.drawScaled(region, widget.getId(), graphics);
				}
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
	public void translucentInventoryInteriorAndBorderKeepNativeOpacityInBothLayouts()
	{
		when(client.isGpu()).thenReturn(true);
		when(config.panelScale()).thenReturn(150);
		when(config.scale()).thenReturn(100);
		when(config.upperTabScale()).thenReturn(100);
		when(config.lowerTabScale()).thenReturn(100);
		int translucent = 0x6a303030;
		int border = 0xffa08060;
		for (int root : new int[]{InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL_PRE_EOC})
		{
			when(client.getTopLevelInterfaceId()).thenReturn(root);
			Widget inventory = root == InterfaceID.TOPLEVEL_OSRS_STRETCH ? classicPanel : panel;
			Rectangle bounds = inventory.getBounds();
			MinimapTransform transform = MinimapTransform.fit(bounds, 150, 0, 0, image.getWidth(), image.getHeight(), true);
			int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
			for (ScalingFilter filter : ScalingFilter.values())
			{
				when(config.filter()).thenReturn(filter);
				when(config.sharpening()).thenReturn(40);
				java.util.Arrays.fill(pixels, 0);
				plugin.onBeforeRender(null);
				plugin.captureBackground();
				for (int y = bounds.y; y < bounds.y + bounds.height; y++)
				{
					for (int x = bounds.x; x < bounds.x + bounds.width; x++)
					{
						boolean edge = x < bounds.x + 8 || y < bounds.y + 8
							|| x >= bounds.x + bounds.width - 8 || y >= bounds.y + bounds.height - 8;
						pixels[y * image.getWidth() + x] = edge ? border : translucent;
					}
				}
				Graphics2D graphics = image.createGraphics();
				try
				{
					plugin.drawScaled(WidgetRegion.SIDE_PANEL, inventory.getId(), graphics);
					plugin.drawOutputs(graphics);
				}
				finally { graphics.dispose(); }
				Rectangle destination = transform.destination();
				assertEquals("Interior must not darken or become opaque: " + root + " / " + filter,
					translucent, pixels[(destination.y + destination.height / 2) * image.getWidth() + destination.x + destination.width / 2]);
				assertEquals("Opaque frame color must not change: " + root + " / " + filter,
					border, pixels[(destination.y + 3) * image.getWidth() + destination.x + 3]);
			}
		}
	}

	@Test
	public void middleCameraDragCrossesScaledInventoryWithoutChangingMouseDelta()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		listener.mouseMoved(new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 122, 0, 400, 300, 0, false));
		listener.mousePressed(new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, java.awt.event.InputEvent.BUTTON2_DOWN_MASK,
			400, 300, 1, false, MouseEvent.BUTTON2));
		clearInvocations(panel);
		MouseEvent drag = new MouseEvent(new Canvas(), MouseEvent.MOUSE_DRAGGED, 124, java.awt.event.InputEvent.BUTTON2_DOWN_MASK,
			740, 380, 0, false);
		assertEquals(new Point(740, 380), listener.mouseDragged(drag).getPoint());
		plugin.onPostClientTick(null);
		verify(panel, never()).setNoClickThrough(true);
		listener.mouseReleased(new MouseEvent(new Canvas(), MouseEvent.MOUSE_RELEASED, 125, 0, 740, 380, 1, false, MouseEvent.BUTTON2));
		MouseEvent hover = new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 126, 0, 740, 380, 0, false);
		assertEquals(new Point(823, 473), listener.mouseMoved(hover).getPoint());
	}

	@Test
	public void rightCameraPressRetainsWidgetMenusAndKeepsMenuMotionContinuous()
	{
		when(config.panelScale()).thenReturn(150);
		when(cameraConfig.rightClickMovesCamera()).thenReturn(true);
		when(cameraConfig.rightClickMenuBlocksCamera()).thenReturn(false);
		plugin.onPluginChanged(new PluginChanged(mock(CameraPlugin.class), true));
		render();
		Menu menu = mock(Menu.class);
		when(client.getMenu()).thenReturn(menu);
		when(menu.getMenuX()).thenReturn(753);
		when(menu.getMenuY()).thenReturn(473);
		when(menu.getMenuWidth()).thenReturn(140);
		when(menu.getMenuHeight()).thenReturn(180);
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		listener.mouseMoved(new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 122, 0, 740, 380, 0, false));
		clearInvocations(panel);
		MouseEvent press = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123,
			java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 740, 380, 1, true, MouseEvent.BUTTON3);
		assertEquals(new Point(823, 473), listener.mousePressed(press).getPoint());
		verify(panel, never()).setNoClickThrough(false);
		when(client.isMenuOpen()).thenReturn(true);
		plugin.onMenuOpened(null);
		MouseEvent drag = new MouseEvent(new Canvas(), MouseEvent.MOUSE_DRAGGED, 124,
			java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 750, 400, 0, false);
		assertEquals(new Point(833, 493), listener.mouseDragged(drag).getPoint());
		assertEquals(new Point(833, 493), plugin.translateMenuPoint(new Point(750, 400)));
	}

	@Test
	public void cameraSettingsRespectNativeToggleAndCameraPluginActivity()
	{
		MouseEvent middle = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0, 400, 300, 1, false, MouseEvent.BUTTON2);
		MouseEvent right = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0, 400, 300, 1, false, MouseEvent.BUTTON3);
		assertTrue(plugin.isCameraPress(middle));
		assertFalse(plugin.isCameraPress(right));
		when(client.getVarbitValue(VarbitID.MOUSECAM_DISABLED)).thenReturn(1);
		plugin.onPostClientTick(null);
		assertFalse(plugin.isCameraPress(middle));
		when(cameraConfig.rightClickMovesCamera()).thenReturn(true);
		when(cameraConfig.rightClickMenuBlocksCamera()).thenReturn(false);
		CameraPlugin cameraPlugin = mock(CameraPlugin.class);
		plugin.onPluginChanged(new PluginChanged(cameraPlugin, true));
		assertTrue(plugin.isCameraPress(right));
		assertTrue(plugin.isCameraPress(middle));
		when(cameraConfig.rightClickMenuBlocksCamera()).thenReturn(true);
		plugin.onPostClientTick(null);
		assertFalse(plugin.isCameraPress(right));
		when(client.getVarbitValue(VarbitID.MOUSECAM_DISABLED)).thenReturn(0);
		plugin.onPostClientTick(null);
		MouseEvent altLeft = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123,
			java.awt.event.InputEvent.ALT_DOWN_MASK, 400, 300, 1, false, MouseEvent.BUTTON1);
		assertTrue(plugin.isCameraPress(altLeft));
		when(cameraConfig.rightClickMenuBlocksCamera()).thenReturn(false);
		plugin.onPluginChanged(new PluginChanged(cameraPlugin, false));
		assertFalse("Saved right-camera settings must not apply with Camera disabled", plugin.isCameraPress(right));
	}

	@Test
	public void lateCameraPluginEnablementKeepsButtonRemappersBeforeOurListener()
	{
		clearInvocations(mouseManager);
		plugin.onPluginChanged(new PluginChanged(mock(CameraPlugin.class), true));
		org.mockito.InOrder order = inOrder(mouseManager);
		order.verify(mouseManager).unregisterMouseListener(any(MinimapMouseListener.class));
		order.verify(mouseManager).registerMouseListener(any(MinimapMouseListener.class));
		order.verify(mouseManager).unregisterMouseWheelListener(any(MinimapMouseListener.class));
		order.verify(mouseManager).registerMouseWheelListener(any(MinimapMouseListener.class));
	}

	@Test
	public void inventoryRightClickAndMenuMovementUseTheSameNativeMenuBounds()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		Menu menu = mock(Menu.class);
		when(client.getMenu()).thenReturn(menu);
		when(menu.getMenuX()).thenReturn(753);
		when(menu.getMenuY()).thenReturn(473);
		when(menu.getMenuWidth()).thenReturn(140);
		when(menu.getMenuHeight()).thenReturn(180);
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		MouseEvent press = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0,
			740, 380, 1, true, MouseEvent.BUTTON3);
		assertEquals(new Point(823, 473), listener.mousePressed(press).getPoint());
		when(client.isMenuOpen()).thenReturn(true);
		plugin.onMenuOpened(null);
		MouseEvent move = new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 124, 0, 750, 400, 0, false);
		Point menuPoint = listener.mouseMoved(move).getPoint();
		assertEquals(new Point(833, 493), menuPoint);
		assertTrue(new Rectangle(753, 473, 140, 180).contains(menuPoint));
		MouseEvent select = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 125, 0,
			750, 400, 1, false, MouseEvent.BUTTON1);
		assertEquals(menuPoint, listener.mousePressed(select).getPoint());
		when(client.isMenuOpen()).thenReturn(false);
		MouseEvent scene = new MouseEvent(new Canvas(), MouseEvent.MOUSE_MOVED, 126, 0, 400, 300, 0, false);
		assertEquals(new Point(400, 300), listener.mouseMoved(scene).getPoint());
	}

	@Test
	public void refreshLimitKeepsHitMaskAlignedToCachedVisibleImageThenAppliesDeferredChange()
	{
		when(config.scalingFps()).thenReturn(1);
		render();
		assertEquals(WidgetRegion.MINIMAP, InputRoute.resolve(new Point(900, 40), plugin.getInputFrames()).target);
		render(false, WidgetRegion.values());
		assertEquals("The cached map remains visible until the refresh limit permits updating", Color.RED.getRGB(), image.getRGB(900, 40));
		assertEquals(WidgetRegion.MINIMAP, InputRoute.resolve(new Point(900, 40), plugin.getInputFrames()).target);
		when(config.scalingFps()).thenReturn(0);
		render(false, WidgetRegion.values());
		assertEquals(0, image.getRGB(900, 40));
		assertNull(InputRoute.resolve(new Point(900, 40), plugin.getInputFrames()).target);
	}

	@Test
	public void layerRefreshPreservesAnUnscaledPanelDrawnBeforeTheMinimap()
	{
		Rectangle mapBounds = map.getBounds();
		when(panel.getBounds()).thenReturn(mapBounds);
		plugin.onBeforeRender(null);
		plugin.captureBackground();
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(Color.BLUE);
			graphics.fill(panel.getBounds());
			plugin.drawScaled(WidgetRegion.SIDE_PANEL, panel.getId(), graphics);
			graphics.setColor(Color.RED);
			graphics.fillRect(900, 100, 8, 8);
			plugin.drawScaled(WidgetRegion.MINIMAP, map.getId(), graphics);
			plugin.drawOutputs(graphics);
		}
		finally
		{
			graphics.dispose();
		}
		assertEquals("Removing the original minimap must restore the current underlying panel", Color.BLUE.getRGB(), image.getRGB(900, 100));
		assertEquals(Color.RED.getRGB(), image.getRGB(950, 50));
	}

	@Test
	public void enlargedInventoryRoutesItemsWithoutChangingTabSize()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		InputRoute item = InputRoute.resolve(new Point(740, 380), plugin.getInputFrames());
		assertEquals(WidgetRegion.SIDE_PANEL, item.target);
		assertEquals(new Point(823, 473), item.point);
		assertEquals(Color.RED.getRGB(), image.getRGB(740, 380));
		InputRoute overlap = InputRoute.resolve(new Point(900, 430), plugin.getInputFrames());
		assertEquals("Open panel should be on top of tabs it covers", WidgetRegion.SIDE_PANEL, overlap.target);
		assertEquals(4, plugin.getInputFrames().size());
	}

	@Test
	public void shrunkInventoryHasClickThroughAtOldLocations()
	{
		when(config.panelScale()).thenReturn(50);
		render();
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		for (Point oldLocation : Arrays.asList(new Point(600, 500), new Point(800, 500)))
		{
			MouseEvent click = new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0,
				oldLocation.x, oldLocation.y, 1, false, MouseEvent.BUTTON1);
			assertEquals(oldLocation, listener.mousePressed(click).getPoint());
			assertFalse(click.isConsumed());
			assertEquals(0, image.getRGB(oldLocation.x, oldLocation.y));
		}
		verify(panel, atLeastOnce()).setNoClickThrough(false);
		InputRoute itemClick = InputRoute.resolve(new Point(950, 600), plugin.getInputFrames());
		assertEquals(WidgetRegion.SIDE_PANEL, itemClick.target);
		assertEquals(new Point(910, 540), itemClick.point);
	}

	@Test
	public void classicPanelUsesTheCorrectContainerAndAlternateHookOrder()
	{
		when(config.panelScale()).thenReturn(150);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		render(WidgetRegion.SIDE_PANEL, WidgetRegion.MINIMAP);
		assertEquals(2, plugin.getInputFrames().size());
		assertEquals(WidgetRegion.SIDE_PANEL, InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).target);
		assertEquals(Color.BLUE.getRGB(), image.getRGB(670, 280));
		assertEquals(new Point(826, 486), InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).point);
		verify(panel, never()).setNoClickThrough(false);
		verify(lower, never()).setNoClickThrough(false);
		verify(upper, never()).setNoClickThrough(false);
	}

	@Test
	public void closingSidePanelClearsItsFrameAndRestoresInputProperties()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		verify(panel, atLeastOnce()).setNoClickThrough(false);
		when(panel.isHidden()).thenReturn(true);
		render();
		assertNull(InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).target);
		verify(panel, atLeastOnce()).setNoClickThrough(true);
	}

	@Test
	public void draggingInventoryOutsideItsBoundsKeepsNativeInputUntilReleaseIsProcessed()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		MinimapMouseListener listener = new MinimapMouseListener(client, plugin);
		listener.mousePressed(new MouseEvent(new Canvas(), MouseEvent.MOUSE_PRESSED, 123, 0, 740, 380, 1, false, MouseEvent.BUTTON1));
		when(client.getMouseLastPressedMillis()).thenReturn(123L);
		plugin.onClientTick(null);
		clearInvocations(panel);
		listener.mouseDragged(new MouseEvent(new Canvas(), MouseEvent.MOUSE_DRAGGED, 124, 0, 640, 300, 0, false));
		plugin.onPostClientTick(null);
		verify(panel, never()).setNoClickThrough(false);
		listener.mouseReleased(new MouseEvent(new Canvas(), MouseEvent.MOUSE_RELEASED, 125, 0, 640, 300, 1, false, MouseEvent.BUTTON1));
		plugin.onClientTick(null);
		plugin.onPostClientTick(null);
		verify(panel).setNoClickThrough(false);
	}

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
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		render();
		assertEquals(1, plugin.getInputFrames().size());
		assertEquals(WidgetRegion.MINIMAP, plugin.getInputFrames().get(0).region());
		verify(upper, never()).setNoClickThrough(false);
		verify(lower, never()).setNoClickThrough(false);
		verify(client, never()).getWidget(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER);
		verify(client, never()).getWidget(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER);
	}

	@Test
	public void modernLayoutNeverUsesCachedClassicWidgets()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		assertEquals(Color.RED.getRGB(), image.getRGB(740, 380));
		assertEquals(new Point(823, 473), InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).point);
		verify(classicPanel, never()).setNoClickThrough(false);
		verify(classicMap, never()).setNoClickThrough(false);
		verify(client, never()).getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU);
	}

	@Test
	public void modernToClassicSwitchDropsOldMappingsBeforeRenderAndRestoresOldFlags()
	{
		when(config.panelScale()).thenReturn(150);
		render();
		verify(panel, atLeastOnce()).setNoClickThrough(false);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		assertTrue("AWT must not use modern mappings while the active root is classic", plugin.getInputFrames().isEmpty());
		render();
		verify(panel, atLeastOnce()).setNoClickThrough(true);
		assertEquals(2, plugin.getInputFrames().size());
		assertEquals(Color.BLUE.getRGB(), image.getRGB(740, 380));
		assertEquals(new Point(826, 486), InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).point);
		assertEquals(0, image.getRGB(600, 300));
	}

	@Test
	public void classicToModernSwitchRestoresClassicFlagsAndReenablesModernBars()
	{
		when(config.panelScale()).thenReturn(150);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		render();
		verify(classicPanel, atLeastOnce()).setNoClickThrough(false);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
		assertTrue(plugin.getInputFrames().isEmpty());
		render();
		verify(classicPanel, atLeastOnce()).setNoClickThrough(true);
		assertEquals(4, plugin.getInputFrames().size());
		assertEquals(Color.RED.getRGB(), image.getRGB(740, 380));
		assertEquals(new Point(823, 473), InputRoute.resolve(new Point(740, 380), plugin.getInputFrames()).point);
		assertEquals(0, image.getRGB(650, 270));
	}

	@Test
	public void inactiveModernLayerHookCannotConsumeTheClassicPanelCapture()
	{
		when(config.panelScale()).thenReturn(150);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
		plugin.onBeforeRender(null);
		plugin.captureBackground();
		Graphics2D graphics = image.createGraphics();
		try
		{
			plugin.drawScaled(WidgetRegion.SIDE_PANEL, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER, graphics);
			graphics.setColor(Color.BLUE);
			graphics.fill(classicPanel.getBounds());
			plugin.drawScaled(WidgetRegion.SIDE_PANEL, classicPanel.getId(), graphics);
			plugin.drawOutputs(graphics);
		}
		finally { graphics.dispose(); }
		assertEquals(1, plugin.getInputFrames().size());
		assertEquals(Color.BLUE.getRGB(), image.getRGB(670, 280));
	}

	@Test
	public void unsupportedOrFixedRootBypassesAllCachedResizableWidgets()
	{
		render();
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_DISPLAY);
		assertTrue(plugin.getInputFrames().isEmpty());
		render();
		assertTrue(plugin.getInputFrames().isEmpty());
		verify(upper, atLeastOnce()).setNoClickThrough(true);
		when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
		when(client.isResized()).thenReturn(false);
		render();
		assertTrue(plugin.getInputFrames().isEmpty());
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
