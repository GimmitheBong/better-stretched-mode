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
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Better Stretched Mode",
	description = "Independent minimap/orb scaling and modern-layout tab bar scaling",
	tags = {"minimap", "resize", "scale", "orbs", "tabs", "stretch", "filter", "sharpen"}
)
public class MinimapResizePlugin extends Plugin
{
	@Inject private Client client;
	@Inject private MinimapResizeConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private ClientThread clientThread;
	private MinimapMouseListener mouseListener;

	private final Map<WidgetRegion, RegionState> states = new EnumMap<>(WidgetRegion.class);
	private final List<Overlay> overlays = new ArrayList<>();
	private final WidgetInputGate inputGate = new WidgetInputGate();
	private final AtomicReference<PendingPress> pendingPress = new AtomicReference<>();
	private volatile List<MinimapInputFrame> inputFrames = Collections.emptyList();
	private volatile Point pointer = new Point(-1, -1);
	private volatile boolean enabled;
	private boolean rendering;

	private static final class RegionState
	{
		final MinimapCompositor compositor = new MinimapCompositor();
		final WidgetImageScaler scaler = new WidgetImageScaler();
		Widget widget;
		MinimapTransform transform;
		BufferedImage foreground;
		MinimapInputFrame input;
		boolean rendered;
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
		mouseListener = new MinimapMouseListener(client, this);
		for (WidgetRegion region : WidgetRegion.values())
		{
			states.put(region, new RegionState());
			overlays.add(new MinimapScaleOverlay(this, region));
		}
		overlays.add(new MinimapCaptureOverlay(this));
		overlays.add(new ScaledWidgetsOverlay(this));
		overlays.add(new InputGateOverlay(this));
		enabled = true;
		overlays.forEach(overlayManager::add);
		// Stretched Mode registers at index zero; these receive game-space coordinates.
		mouseManager.registerMouseListener(mouseListener);
		mouseManager.registerMouseWheelListener(mouseListener);
	}

	@Override
	protected void shutDown()
	{
		enabled = false;
		inputFrames = Collections.emptyList();
		pendingPress.set(null);
		mouseManager.unregisterMouseListener(mouseListener);
		mouseManager.unregisterMouseWheelListener(mouseListener);
		overlays.forEach(overlayManager::remove);
		overlays.clear();
		clientThread.invokeLater(() ->
		{
			inputGate.restore();
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
		// Input property changes are only for hit testing, never for rendering.
		inputGate.restore();
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
		// ClientTick is AFTER the native interface input pass, BEFORE client scripts.
		// Restore here so scripts can update or intentionally hide the real widgets.
		inputGate.restore();
		PendingPress press = pendingPress.get();
		if (press != null && client.getMouseLastPressedMillis() >= press.when)
		{
			pendingPress.compareAndSet(press, null);
		}
	}

	@Subscribe(priority = -1000)
	public void onPostClientTick(PostClientTick event)
	{
		rendering = false;
		applyInputGate();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) { reset(); }

	@Subscribe
	public void onResizeableChanged(ResizeableChanged event) { reset(); }

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MinimapResizeConfig.GROUP.equals(event.getGroup()))
		{
			inputFrames = Collections.emptyList();
			clientThread.invokeLater(this::reset);
		}
	}

	private void reset()
	{
		inputGate.restore();
		inputFrames = Collections.emptyList();
		pendingPress.set(null);
		for (RegionState state : states.values())
		{
			state.input = null;
			state.rendered = false;
			state.foreground = null;
			state.compositor.invalidate();
		}
	}

	private boolean canScale()
	{
		return enabled && client.getGameState() == GameState.LOGGED_IN && client.isResized();
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
		if (!canScale() || image == null)
		{
			reset();
			return;
		}
		for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
		{
			WidgetRegion region = entry.getKey();
			RegionState state = entry.getValue();
			state.widget = region.visibleWidget(client);
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
			state.compositor.capture(image, source);
		}
		publishFrames();
	}

	private MinimapTransform transform(WidgetRegion region, Rectangle source, int width, int height)
	{
		switch (region)
		{
			case UPPER_TABS:
				return MinimapTransform.fit(source, config.upperTabScale(), config.upperTabLeft(), config.upperTabUp(), width, height, true);
			case LOWER_TABS:
				return MinimapTransform.fit(source, config.lowerTabScale(), config.lowerTabLeft(), config.lowerTabUp(), width, height, true);
			default:
				return MinimapTransform.fit(source, config.scale(), config.offsetX(), config.offsetY(), width, height);
		}
	}

	void drawScaled(WidgetRegion region, Graphics2D graphics)
	{
		RegionState state = states.get(region);
		BufferedImage image = bufferImage();
		if (state == null || !canScale() || image == null)
		{
			return;
		}
		Widget widget = region.visibleWidget(client);
		if (widget != null && widget == state.widget && state.compositor.matches(image, widget.getBounds()))
		{
			state.foreground = state.compositor.extractAndRestore(region == WidgetRegion.MINIMAP ? mapInterior(widget) : null);
		}
		else
		{
			state.input = null;
		}
		// Later layers need the current background, including earlier unscaled widgets.
		for (Map.Entry<WidgetRegion, RegionState> entry : states.entrySet())
		{
			RegionState later = entry.getValue();
			if (entry.getKey().ordinal() > region.ordinal() && later.widget != null
				&& later.compositor.matches(image, later.widget.getBounds()))
			{
				later.compositor.capture(image, later.widget.getBounds());
			}
		}
	}

	void drawOutputs(Graphics2D graphics)
	{
		if (!canScale())
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
				BufferedImage filtered = state.scaler.scale(state.foreground, destination.width, destination.height,
					filter, config.sharpening());
				g.drawImage(filtered, destination.x, destination.y, null);
				state.input = new MinimapInputFrame(entry.getKey(), state.transform, state.foreground);
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

	private Shape mapInterior(Widget container)
	{
		int id = container.getId() == InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER
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
		return enabled ? inputFrames : Collections.emptyList();
	}

	void recordPointer(Point point, WidgetRegion target, int eventId, long when)
	{
		pointer = new Point(point);
		if (eventId == MouseEvent.MOUSE_PRESSED)
		{
			pendingPress.set(new PendingPress(target, when));
		}
		// Runs at the start of the next client cycle, before native interface hit testing.
		clientThread.invokeLater(this::applyInputGate);
	}

	void applyInputGate()
	{
		if (rendering)
		{
			return;
		}
		inputGate.restore();
		if (!canScale())
		{
			return;
		}
		WidgetRegion target = client.isMenuOpen() ? null : InputRoute.resolve(pointer, getInputFrames()).target;
		PendingPress press = pendingPress.get();
		if (press != null && System.nanoTime() - press.created < 500_000_000L)
		{
			// Keep the click's target available even if the pointer moves before the input pass.
			target = press.target;
		}
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
