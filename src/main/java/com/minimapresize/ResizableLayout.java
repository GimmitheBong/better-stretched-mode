package com.minimapresize;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;

/** The active interface root is authoritative; cached widgets from other roots may look visible. */
enum ResizableLayout
{
	CLASSIC(InterfaceID.TOPLEVEL_OSRS_STRETCH),
	MODERN(InterfaceID.TOPLEVEL_PRE_EOC),
	UNSUPPORTED(-1);

	final int interfaceId;

	ResizableLayout(int interfaceId) { this.interfaceId = interfaceId; }

	static ResizableLayout current(Client client)
	{
		if (!client.isResized()) { return UNSUPPORTED; }
		int root = client.getTopLevelInterfaceId();
		if (root == CLASSIC.interfaceId) { return CLASSIC; }
		if (root == MODERN.interfaceId) { return MODERN; }
		return UNSUPPORTED;
	}
}
