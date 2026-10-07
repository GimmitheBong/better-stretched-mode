package com.minimapresize;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Output stacking order; extraction follows the actual layer hooks in either layout. */
enum WidgetRegion
{
	MINIMAP(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.MAP_CONTAINER),
	LOWER_TABS(-1, InterfaceID.ToplevelPreEoc.SIDE_STATIC_LAYER),
	UPPER_TABS(-1, InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_LAYER),
	// Classic's frame and tab buttons are part of SIDE_MENU; SIDE_CONTAINER is only an inner layer.
	SIDE_PANEL(InterfaceID.ToplevelOsrsStretch.SIDE_MENU, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);

	final int[] components;
	private final int classic;
	private final int modern;

	WidgetRegion(int classic, int modern)
	{
		this.classic = classic;
		this.modern = modern;
		this.components = classic == -1 ? new int[]{modern} : new int[]{classic, modern};
	}

	int component(ResizableLayout layout)
	{
		return layout == ResizableLayout.CLASSIC ? classic : layout == ResizableLayout.MODERN ? modern : -1;
	}

	Widget visibleWidget(Client client, ResizableLayout layout)
	{
		int component = component(layout);
		if (component == -1) { return null; }
		Widget widget = client.getWidget(component);
		return widget != null && !widget.isHidden() ? widget : null;
	}

	boolean rectangularInput()
	{
		// Empty inventory slots and classic frame controls stay interactive.
		return this == SIDE_PANEL;
	}
}
