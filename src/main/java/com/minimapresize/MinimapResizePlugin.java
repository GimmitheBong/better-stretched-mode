package com.minimapresize;

import com.google.inject.Provides;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.camera.CameraConfig;
import net.runelite.client.plugins.camera.CameraPlugin;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Better Stretched Mode",
	description = "Independent minimap, inventory/side-panel and modern-layout tab bar scaling",
	tags = {"minimap", "resize", "scale", "orbs", "tabs", "stretch", "filter", "sharpen", "inventory"}
)
public class MinimapResizePlugin extends Plugin
{
	@Inject private Client client;
	@Inject private MinimapResizeConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private PluginManager pluginManager;
	private MinimapMouseListener mouseListener;
	private MenuRelocator menus;
	private CameraConfig cameraConfig;
	private volatile boolean cameraPluginActive;
	private volatile int cameraButtons;
	private volatile Point cameraInputOffset;

	private final Map<WidgetRegion, RegionState> states = new EnumMap<>(WidgetRegion.class);
	private final List<Overlay> overlays = new ArrayList<>();
	private final WidgetInputGate inputGate = new WidgetInputGate();
	private final AtomicReference<PendingPress> pendingPress = new AtomicReference<>();
	private final AtomicBoolean gateQueued = new AtomicBoolean();
	private volatile List<MinimapInputFrame> inputFrames = Collections.emptyList();
	private volatile Point pointer = new Point(-1, -1);
	private volatile WidgetRegion dragTarget;
	private volatile boolean dragReleased;
	private volatile boolean enabled;
	private boolean rendering;
	private boolean gateApplied;
	private WidgetRegion lastGatedTarget;
	private volatile ResizableLayout layout = ResizableLayout.UNSUPPORTED;

	private static final class RegionState
	{
		final MinimapCompositor compositor = new MinimapCompositor();
		final WidgetImageScaler scaler = new WidgetImageScaler();
		Widget widget;
		MinimapTransform transform;
		BufferedImage foreground;
		MinimapInputFrame input;
		boolean rendered;
		boolean pendingMaskChange;
	}

	private static final class PendingPress
	{
		final WidgetRegion target;
		final long when;
		final long created = System.nanoTime();

		PendingPress(WidgetRegion target, long when)
		{
			this.target = target;
			this.when = when;
		}
	}

	@Provides
	MinimapResizeConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(MinimapResizeConfig.class);
	}

	@Override
	protected void startUp()
	{
		menus = new MenuRelocator(client);
		cameraConfig = configManager.getConfig(CameraConfig.class);
		cameraPluginActive = false;
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof CameraPlugin && pluginManager.isPluginActive(plugin)) { cameraPluginActive = true; }
		}
		mouseListener = new MinimapMouseListener(client, this);
		for (WidgetRegion region : WidgetRegion.values())
		{
			states.put(region, new RegionState());
			for (int component : region.components)
			{
				overlays.add(new MinimapScaleOverlay(this, region, component));
			}
		}
		overlays.add(new MinimapCaptureOverlay(this));
		overlays.add(new ScaledWidgetsOverlay(this));
		overlays.add(new InputGateOverlay(this));
		overlays.add(new MenuBackgroundOverlay(menus));
		overlays.add(new MenuRelocationOverlay(menus));
		enabled = true;
		overlays.forEach(overlayManager::add);
		// Stretched Mode registers at index zero; these receive game-space coordinates.
		mouseManager.registerMouseListener(mouseListener);
		mouseManager.registerMouseWheelListener(mouseListener);
		clientThread.invokeLater(this::updateCameraButtons);
	}

	@Override
	protected void shutDown()
	{
		enabled = false;
		cameraButtons = 0;
		cameraInputOffset = null;
		inputFrames = Collections.emptyList();
		layout = ResizableLayout.UNSUPPORTED;
		pendingPress.set(null);
		dragTarget = null;
		mouseManager.unregisterMouseListener(mouseListener);
		mouseManager.unregisterMouseWheelListener(mouseListener);
		overlays.forEach(overlayManager::remove);
		overlays.clear();
		clientThread.invokeLater(() ->
		{
			inputGate.restore();
			menus.reset();
			inputGate.invalidateTraversal();
			gateApplied = false;
			if (!enabled)
			{
				states.values().forEach(state ->
				{
					state.compositor.clear();
					state.scaler.clear();
				});
				states.clear();
			}
		});
	}

	@Subscribe(priority = 1000)
	public void onBeforeRender(BeforeRender event)
	{
		synchronizeLayout();
		// Input property changes are only for hit testing, never for rendering.
		inputGate.restore();
		gateApplied = false;
		rendering = true;
		for (RegionState state : states.values())
		{
			state.compositor.invalidate();
			state.foreground = null;
			if (!state.rendered || !canScale())
			{
				state.input = null;
			}
			state.rendered = false;
		}
		publishFrames();
	}

	@Subscribe(priority = 1000)
	public void onClientTick(ClientTick event)
	{
		updateCameraButtons();
		synchronizeLayout();
		// ClientTick is AFTER the native interface input pass, BEFORE client scripts.
		// Restore here so scripts can update or intentionally hide the real widgets.
		inputGate.restore();
		inputGate.invalidateTraversal();
		gateApplied = false;
		PendingPress press = pendingPress.get();
		if (press != null && client.getMouseLastPressedMillis() >= press.when)
		{
			pendingPress.compareAndSet(press, null);
		}
		if (dragReleased && client.getMouseCurrentButton() == 0)
		{
			dragTarget = null;
			dragReleased = false;
		}
	}

	@Subscribe(priority = -1000)
	public void onPostClientTick(PostClientTick event)
	{
		updateCameraButtons();
		rendering = false;
		applyInputGate();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) { reset(); }

	@Subscribe
	public void onResizeableChanged(ResizeableChanged event) { reset(); }

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (canScale() && synchronizeLayout()) { menus.opened(); }
		else { menus.reset(); }
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MinimapResizeConfig.GROUP.equals(event.getGroup()))
		{
			inputFrames = Collections.emptyList();
			clientThread.invokeLater(this::reset);
		}
		else if ("zoom".equals(event.getGroup()))
		{
			clientThread.invokeLater(this::updateCameraButtons);
		}
	}

	private void reset()
	{
		if (!enabled || client.getGameState() != GameState.LOGGED_IN)
		{
			mouseListener.resetGestures();
			cameraInputOffset = null;
		}
		menus.reset();
		inputGate.restore();
		inputGate.invalidateTraversal();
		gateApplied = false;
		inputFrames = Collections.emptyList();
		pendingPress.set(null);
		dragTarget = null;
		for (RegionState state : states.values())
		{
			state.widget = null;
			state.transform = null;
			state.input = null;
			state.rendered = false;
			state.foreground = null;
			state.compositor.invalidate();
			state.pendingMaskChange = false;
		}
	}

	/** Client thread only: restore old widget flags before discarding the old root's state. */
	private boolean synchronizeLayout()
	{
		ResizableLayout current = ResizableLayout.current(client);
		if (current != layout)
		{
			reset();
			for (RegionState state : states.values())
			{
				state.compositor.clear();
				state.scaler.clear();
			}
			layout = current;
		}
		return current != ResizableLayout.UNSUPPORTED;
	}

	private boolean canScale()
	{
		return enabled && client.getGameState() == GameState.LOGGED_IN
			&& ResizableLayout.current(client) != ResizableLayout.UNSUPPORTED;
	}

	private BufferedImage bufferImage()
	{
		if (!(client.getBufferProvider() instanceof MainBufferProvider))
		{
			return null;
		}
		Image image = ((MainBufferProvider) client.getBufferProvider()).getImage();
		return image instanceof BufferedImage ? (BufferedImage) image : null;
	}

	void captureBackground()
	{
		BufferedImage image = bufferImage();
		if (!synchronizeLayout() || !canScale() || image == null)
		{
			reset();
			return;
		}
		for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
		{
			WidgetRegion region = entry.getKey();
			RegionState state = entry.getValue();
			state.widget = region.visibleWidget(client, layout);
			if (state.widget == null)
			{
				state.input = null;
				continue;
			}
			Rectangle source = state.widget.getBounds();
			if (source.isEmpty() || !new Rectangle(image.getWidth(), image.getHeight()).contains(source))
			{
				state.input = null;
				continue;
			}
			state.transform = transform(region, source, image.getWidth(), image.getHeight());
			if (state.transform.isIdentity())
			{
				state.input = null;
				continue;
			}
			state.compositor.capture(image, source, client.isGpu());
		}
		publishFrames();
	}

	private MinimapTransform transform(WidgetRegion region, Rectangle source, int width, int height)
	{
		switch (region)
		{
			case SIDE_PANEL:
				return MinimapTransform.fit(source, config.panelScale(), config.panelLeft(), config.panelUp(), width, height, true);
			case UPPER_TABS:
				return MinimapTransform.fit(source, config.upperTabScale(), config.upperTabLeft(), config.upperTabUp(), width, height, true);
			case LOWER_TABS:
				return MinimapTransform.fit(source, config.lowerTabScale(), config.lowerTabLeft(), config.lowerTabUp(), width, height, true);
			default:
				return MinimapTransform.fit(source, config.scale(), config.offsetX(), config.offsetY(), width, height);
		}
	}

	void drawScaled(WidgetRegion region, int component, Graphics2D graphics)
	{
		RegionState state = states.get(region);
		BufferedImage image = bufferImage();
		if (!synchronizeLayout() || state == null || !canScale() || image == null || region.component(layout) != component)
		{
			return;
		}
		Widget widget = region.visibleWidget(client, layout);
		if (widget != null && widget == state.widget && state.compositor.matches(image, widget.getBounds()))
		{
			state.foreground = state.compositor.extractAndRestore(region == WidgetRegion.MINIMAP ? mapInterior() : null);
		}
		else
		{
			state.input = null;
		}
		// Refresh every capture whose hook has not fired yet. Classic and modern
		// have different nesting/draw orders, so enum order cannot predict the next layer.
		for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
		{
			RegionState later = entry.getValue();
			if (entry.getKey() != region && later.widget != null
				&& later.compositor.matches(image, later.widget.getBounds()))
			{
				later.compositor.capture(image, later.widget.getBounds(), client.isGpu());
			}
		}
	}

	void drawOutputs(Graphics2D graphics)
	{
		if (!synchronizeLayout() || !canScale())
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setComposite(AlphaComposite.SrcOver);
			ScalingFilter filter = config.filter();
			if (filter == null)
			{
				filter = config.smooth() ? ScalingFilter.BILINEAR : ScalingFilter.NEAREST;
			}
			for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
			{
				RegionState state = entry.getValue();
				if (state.foreground == null)
				{
					state.input = null;
					continue;
				}
				Rectangle destination = state.transform.destination();
				state.pendingMaskChange |= state.compositor.maskChanged();
				boolean newInput = state.input == null;
				BufferedImage filtered = state.scaler.scale(state.foreground, destination.width, destination.height,
					filter, config.sharpening(), state.compositor.changed() || newInput, newInput ? 0 : config.scalingFps());
				UiBlitter.draw(bufferImage(), filtered, destination.x, destination.y, g, client.isGpu());
				state.input = new MinimapInputFrame(entry.getKey(), state.transform, state.foreground,
					state.input, state.scaler.updated() && state.pendingMaskChange);
				if (state.scaler.updated()) { state.pendingMaskChange = false; }
				state.rendered = true;
			}
		}
		finally
		{
			g.dispose();
		}
		publishFrames();
	}

	void finishRender()
	{
		rendering = false;
		applyInputGate();
	}

	private Shape mapInterior()
	{
		int id = layout == ResizableLayout.CLASSIC
			? InterfaceID.ToplevelOsrsStretch.MINIMAP : InterfaceID.ToplevelPreEoc.MINIMAP;
		Widget map = client.getWidget(id);
		if (map == null || map.isHidden())
		{
			return null;
		}
		Rectangle bounds = map.getBounds();
		return new Ellipse2D.Double(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4), Math.max(0, bounds.height - 4));
	}

	private void publishFrames()
	{
		List<MinimapInputFrame> frames = new ArrayList<>();
		for (RegionState state : states.values())
		{
			if (state.input != null)
			{
				frames.add(state.input);
			}
		}
		inputFrames = Collections.unmodifiableList(frames);
	}

	List<MinimapInputFrame> getInputFrames()
	{
		return enabled && layout != ResizableLayout.UNSUPPORTED && layout == ResizableLayout.current(client)
			? inputFrames : Collections.emptyList();
	}

	void recordMenuPress(Point cursor, Point nativePoint, WidgetRegion target)
	{
		if (enabled && layout == ResizableLayout.current(client)) { menus.recordPress(cursor, nativePoint, target); }
	}

	Point translateMenuPoint(Point point)
	{
		return enabled && layout == ResizableLayout.current(client) ? menus.translate(point) : point;
	}

	private void updateCameraButtons()
	{
		if (!enabled || client.getGameState() != GameState.LOGGED_IN) { cameraButtons = 0; return; }
		boolean rightAlways = cameraPluginActive && cameraConfig != null && cameraConfig.rightClickMovesCamera()
			&& !cameraConfig.rightClickMenuBlocksCamera();
		int buttons = client.getVarbitValue(VarbitID.MOUSECAM_DISABLED) == 0 || rightAlways ? 1 << MouseEvent.BUTTON2 : 0;
		if (rightAlways) { buttons |= 1 << MouseEvent.BUTTON3; }
		cameraButtons = buttons;
	}

	boolean isCameraPress(MouseEvent event)
	{
		// Match native Alt/middle and Meta/right button interpretation after remappers.
		int button = event.isAltDown() || event.getButton() == MouseEvent.BUTTON2 ? MouseEvent.BUTTON2
			: event.isMetaDown() || event.getButton() == MouseEvent.BUTTON3 ? MouseEvent.BUTTON3 : event.getButton();
		return button > 0 && button < 32 && (cameraButtons & (1 << button)) != 0;
	}

	void recordCameraInputOffset(Point offset)
	{
		cameraInputOffset = offset == null ? null : new Point(offset);
		if (enabled) { menus.setCameraInputOffset(offset); }
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			mouseListener.resetGestures();
			recordCameraInputOffset(null);
			dragTarget = null;
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof CameraPlugin)
		{
			cameraPluginActive = event.isLoaded();
			clientThread.invokeLater(this::updateCameraButtons);
		}
		if (enabled && event.getPlugin() != this && event.isLoaded())
		{
			// See final remapped buttons even when another input plugin is enabled later.
			mouseManager.unregisterMouseListener(mouseListener);
			mouseManager.registerMouseListener(mouseListener);
			mouseManager.unregisterMouseWheelListener(mouseListener);
			mouseManager.registerMouseWheelListener(mouseListener);
		}
	}

	void recordPointer(Point point, WidgetRegion target, int eventId, long when)
	{
		pointer = new Point(point);
		if (eventId == MouseEvent.MOUSE_PRESSED)
		{
			pendingPress.set(new PendingPress(target, when));
		}
		// Runs at the start of the next client cycle, before native interface hit testing.
		if (gateQueued.compareAndSet(false, true))
		{
			clientThread.invokeLater(() ->
			{
				gateQueued.set(false);
				applyInputGate();
			});
		}
	}

	void recordDragTarget(WidgetRegion target, boolean released)
	{
		dragTarget = target;
		dragReleased = released;
	}

	void applyInputGate()
	{
		if (rendering)
		{
			return;
		}
		if (!synchronizeLayout() || !canScale())
		{
			inputGate.restore();
			gateApplied = false;
			return;
		}
		WidgetRegion target = client.isMenuOpen() ? null : InputRoute.resolve(pointer, getInputFrames()).target;
		PendingPress press = pendingPress.get();
		if (press != null && System.nanoTime() - press.created < 500_000_000L)
		{
			// Keep the click's target available even if the pointer moves before the input pass.
			target = press.target;
		}
		if (dragTarget != null && !client.isMenuOpen())
		{
			target = dragTarget;
		}
		if (cameraInputOffset != null)
		{
			// A right-camera press may also open an object/widget menu. Preserve that
			// click's operations for its input pass, then exclude UI for the held drag.
			target = press != null && System.nanoTime() - press.created < 500_000_000L ? press.target : null;
		}
		if (gateApplied && target == lastGatedTarget) { return; }
		inputGate.restore();
		lastGatedTarget = target;
		gateApplied = true;
		for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
		{
			RegionState state = entry.getValue();
			if (state.input != null && state.input.isFresh() && entry.getKey() != target)
			{
				inputGate.exclude(state.widget);
			}
		}
	}
}
