package com.minimapresize;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Output stacking order; extraction follows the actual layer hooks in either layout. */
enum WidgetRegion
{
	MINIMAP(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER),
	LOWER_TABS(InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER),
	UPPER_TABS(InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER),
	SIDE_PANEL(InterfaceID.ToplevelOsrsStretch.SIDE_CONTAINER, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);

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

	boolean rectangularInput()
	{
		// Empty inventory slots are still part of the interactive side panel.
		return this == SIDE_PANEL;
	}
}
