package com.minimapresize;

import java.awt.RenderingHints;

public enum ScalingFilter
{
	NEAREST("Nearest neighbor (pixel sharp)", RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
	BILINEAR("Bilinear (smooth)", RenderingHints.VALUE_INTERPOLATION_BILINEAR),
	BICUBIC("Bicubic", RenderingHints.VALUE_INTERPOLATION_BICUBIC),
	SHARP_BICUBIC("Sharp Bicubic", RenderingHints.VALUE_INTERPOLATION_BICUBIC);

	private final String label;
	final Object interpolation;

	ScalingFilter(String label, Object interpolation)
	{
		this.label = label;
		this.interpolation = interpolation;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
