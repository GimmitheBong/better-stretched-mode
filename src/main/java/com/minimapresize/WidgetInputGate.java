package com.minimapresize;

import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.widgets.Widget;

/**
 * Client-thread only. Removes native hit blockers/operations from an inactive scaled
 * container, including static, dynamic and nested children. Widgets stay visible and
 * their timer/stat listeners keep running, so health/prayer/run values still update.
 * Restore before drawing and before client scripts can change these properties.
 */
final class WidgetInputGate
{
	private final Map<Widget, SavedInput> excluded = new IdentityHashMap<>();

	private static final class SavedInput
	{
		final boolean noClickThrough;
		final boolean noScrollThrough;
		final int contentType;
		final int clickMask;
		final String[] actions;
		final Object[] onOp;

		SavedInput(Widget widget)
		{
			noClickThrough = widget.getNoClickThrough();
			noScrollThrough = widget.getNoScrollThrough();
			contentType = widget.getContentType();
			clickMask = widget.getClickMask();
			String[] original = widget.getActions();
			actions = original == null ? null : original.clone();
			onOp = widget.getOnOpListener();
		}

		void restore(Widget widget)
		{
			widget.setNoClickThrough(noClickThrough);
			widget.setNoScrollThrough(noScrollThrough);
			widget.setContentType(contentType);
			widget.setClickMask(clickMask);
			if (actions != null)
			{
				widget.clearActions();
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null)
					{
						widget.setAction(i, actions[i]);
					}
				}
			}
			widget.setOnOpListener(onOp);
		}
	}

	void exclude(Widget widget)
	{
		if (widget == null || excluded.containsKey(widget))
		{
			return;
		}
		excluded.put(widget, new SavedInput(widget));
		widget.setNoClickThrough(false);
		widget.setNoScrollThrough(false);
		// Includes the special minimap content type, which otherwise handles walking
		// directly rather than through menu entries.
		widget.setContentType(0);
		widget.setClickMask(0);
		widget.clearActions();
		widget.setOnOpListener((Object[]) null);
		exclude(widget.getStaticChildren());
		exclude(widget.getDynamicChildren());
		exclude(widget.getNestedChildren());
	}

	private void exclude(Widget[] children)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				exclude(child);
			}
		}
	}

	void restore()
	{
		for (Map.Entry<Widget, SavedInput> entry : excluded.entrySet())
		{
			entry.getValue().restore(entry.getKey());
		}
		excluded.clear();
	}
}
