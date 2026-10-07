package com.minimapresize;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;

/** Direct access to RuneLite's int-backed RGB/ARGB buffers, including translated subimages. */
final class PackedPixels
{
	final int[] pixels;
	final int stride;
	final int origin;
	final boolean opaque;

	private PackedPixels(BufferedImage image, DirectColorModel model, SinglePixelPackedSampleModel sample)
	{
		Raster raster = image.getRaster();
		DataBufferInt buffer = (DataBufferInt) raster.getDataBuffer();
		pixels = buffer.getData();
		stride = sample.getScanlineStride();
		origin = buffer.getOffset() - raster.getSampleModelTranslateY() * stride - raster.getSampleModelTranslateX();
		opaque = model.getAlphaMask() == 0;
	}

	static PackedPixels of(BufferedImage image)
	{
		return image.isAlphaPremultiplied() ? null : nativePixels(image);
	}

	static PackedPixels nativePixels(BufferedImage image)
	{
		if (!(image.getColorModel() instanceof DirectColorModel)
			|| !(image.getRaster().getDataBuffer() instanceof DataBufferInt)
			|| !(image.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel)
			|| !image.getColorModel().getColorSpace().isCS_sRGB())
		{
			return null;
		}
		DirectColorModel model = (DirectColorModel) image.getColorModel();
		if (model.getRedMask() != 0xff0000 || model.getGreenMask() != 0xff00 || model.getBlueMask() != 0xff
			|| (model.getAlphaMask() != 0 && model.getAlphaMask() != 0xff000000))
		{
			return null;
		}
		return new PackedPixels(image, model, (SinglePixelPackedSampleModel) image.getRaster().getSampleModel());
	}

	int index(int x, int y)
	{
		return origin + y * stride + x;
	}

	static int[] straightArgb(BufferedImage image)
	{
		return colorPixels(image, false);
	}

	static int[] alphaPixels(BufferedImage image)
	{
		return colorPixels(image, true);
	}

	private static int[] colorPixels(BufferedImage image, boolean acceptPremultiplied)
	{
		if (image.getType() == BufferedImage.TYPE_INT_ARGB
			|| (acceptPremultiplied && image.getType() == BufferedImage.TYPE_INT_ARGB_PRE))
		{
			DataBufferInt buffer = (DataBufferInt) image.getRaster().getDataBuffer();
			int[] data = buffer.getData();
			if (data.length == image.getWidth() * image.getHeight() && buffer.getOffset() == 0
				&& image.getRaster().getSampleModelTranslateX() == 0 && image.getRaster().getSampleModelTranslateY() == 0)
			{
				return data;
			}
		}
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
