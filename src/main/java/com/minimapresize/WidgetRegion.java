package com.minimapresize;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** The order matches the native modern-layout draw order. */
enum WidgetRegion
{
	MINIMAP(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER),
	LOWER_TABS(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER),
	UPPER_TABS(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER);

	final int[] components;

	WidgetRegion(int... components)
	{
		this.components = components;
	}

	Widget visibleWidget(Client client)
	{
		for (int component : components)
		{
			Widget widget = client.getWidget(component);
			if (widget != null && !widget.isHidden())
			{
				return widget;
			}
		}
		return null;
	}
}
