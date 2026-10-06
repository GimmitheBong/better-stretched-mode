package com.minimapresize;

import com.sun.management.ThreadMXBean;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Repeatable CPU benchmark of the real capture/filter/input path, without a logged-in client. */
public final class ScalerBenchmark
{
	private static volatile int sink;

	private static final class Region
	{
		final WidgetRegion kind;
		final Rectangle source;
		final MinimapTransform transform;
		final MinimapCompositor compositor = new MinimapCompositor();
		final WidgetImageScaler scaler = new WidgetImageScaler();
		final int[] content;
		MinimapInputFrame input;

		Region(WidgetRegion kind, Rectangle source)
		{
			this.kind = kind;
			this.source = source;
			transform = MinimapTransform.fit(source, 150, 0, 0, 1280, 800,
				kind != WidgetRegion.MINIMAP);
			content = new int[source.width * source.height];
			for (int y = 0; y < source.height; y++)
			{
				for (int x = 0; x < source.width; x++)
				{
					if (x > 3 && x < source.width - 4 && y > 3 && y < source.height - 4)
					{
						content[y * source.width + x] = 0xff000000 | (((x * 17 + y * 3) & 255) * 0x010101);
					}
				}
			}
		}
	}

	private static void frame(BufferedImage image, Graphics2D graphics, Region[] regions, ScalingFilter filter, int tick)
	{
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		Arrays.fill(pixels, 0);
		for (Region region : regions)
		{
			region.compositor.capture(image, region.source);
			// Animated minimap; other UI regions change at a slower rate, like real item/icon content.
			region.content[region.content.length / 2] = 0xff000000 | ((region.kind == WidgetRegion.MINIMAP ? tick : tick / 80) & 255);
			for (int y = 0; y < region.source.height; y++)
			{
				System.arraycopy(region.content, y * region.source.width, pixels,
					(region.source.y + y) * image.getWidth() + region.source.x, region.source.width);
			}
			BufferedImage foreground = region.compositor.extractAndRestore(region.kind == WidgetRegion.MINIMAP
				? new Ellipse2D.Double(region.source.x + 50, region.source.y + 10, 150, 150) : null);
			Rectangle destination = region.transform.destination();
			BufferedImage scaled = region.scaler.scale(foreground, destination.width, destination.height, filter, 40, region.compositor.changed());
			graphics.drawImage(scaled, destination.x, destination.y, null);
			region.input = new MinimapInputFrame(region.kind, region.transform, foreground, region.input, region.compositor.maskChanged());
			sink ^= scaled.getRGB(destination.width / 2, destination.height / 2);
		}
	}

	private static void run(String label, Region[] regions, ScalingFilter filter)
	{
		BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			for (int i = 0; i < 100; i++) { frame(image, graphics, regions, filter, i); }
			ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
			long thread = Thread.currentThread().getId();
			long bytes = memory.getThreadAllocatedBytes(thread);
			long start = System.nanoTime();
			for (int i = 100; i < 350; i++) { frame(image, graphics, regions, filter, i); }
			long elapsed = System.nanoTime() - start;
			long allocated = memory.getThreadAllocatedBytes(thread) - bytes;
			System.out.printf(Locale.ROOT, "%s / %s: %.3f ms/frame; %.1f KiB/frame%n",
				label, filter, elapsed / 250_000_000.0, allocated / 256000.0);
		}
		finally
		{
			graphics.dispose();
		}
	}

	public static void main(String[] args)
	{
		System.out.println("Synthetic CPU capture/filter/input benchmark (not in-game FPS; excludes native widget traversal).");
		for (ScalingFilter filter : ScalingFilter.values())
		{
			run("Minimap", new Region[]{new Region(WidgetRegion.MINIMAP, new Rectangle(1040, 0, 240, 180))}, filter);
			run("Both tab bars", new Region[]{
				new Region(WidgetRegion.UPPER_TABS, new Rectangle(1040, 360, 240, 40)),
				new Region(WidgetRegion.LOWER_TABS, new Rectangle(1040, 660, 240, 40))}, filter);
			run("All four", new Region[]{
				new Region(WidgetRegion.MINIMAP, new Rectangle(1040, 0, 240, 180)),
				new Region(WidgetRegion.UPPER_TABS, new Rectangle(1040, 360, 240, 40)),
				new Region(WidgetRegion.LOWER_TABS, new Rectangle(1040, 660, 240, 40)),
				new Region(WidgetRegion.SIDE_PANEL, new Rectangle(1060, 400, 200, 260))}, filter);
		}
		run("Inventory alone", new Region[]{new Region(WidgetRegion.SIDE_PANEL, new Rectangle(1060, 400, 200, 260))}, ScalingFilter.SHARP_BICUBIC);
	}
}
