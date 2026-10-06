package com.minimapresize;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class MinimapResizeLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MinimapResizePlugin.class);
		RuneLite.main(args);
	}
}
