package com.minimapresize;

public enum ScalingFilter
{
	NEAREST("Nearest neighbor (pixel sharp)"),
	BILINEAR("Bilinear (smooth)"),
	BICUBIC("Bicubic"),
	SHARP_BICUBIC("Sharp Bicubic");

	private final String label;

	ScalingFilter(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
