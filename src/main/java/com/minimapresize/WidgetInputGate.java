package com.minimapresize;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
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
	private final Map<Widget, SavedInput> templates = new IdentityHashMap<>();
	private final Map<Widget, List<Widget>> trees = new IdentityHashMap<>();

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
		if (widget == null || excluded.containsKey(widget)) { return; }
		List<Widget> tree = trees.get(widget);
		if (tree == null)
		{
			tree = new ArrayList<>();
			collect(widget, tree, new IdentityHashMap<>());
			trees.put(widget, tree);
		}
		for (Widget child : tree)
		{
			if (excluded.containsKey(child)) { continue; }
			excluded.put(child, templates.get(child));
			child.setNoClickThrough(false);
			child.setNoScrollThrough(false);
			child.setContentType(0);
			child.setClickMask(0);
			child.clearActions();
			child.setOnOpListener((Object[]) null);
		}
	}

	private void collect(Widget widget, List<Widget> result, Map<Widget, Boolean> visited)
	{
		if (widget == null || visited.put(widget, true) != null) { return; }
		result.add(widget);
		templates.computeIfAbsent(widget, SavedInput::new);
		collect(widget.getStaticChildren(), result, visited);
		collect(widget.getDynamicChildren(), result, visited);
		collect(widget.getNestedChildren(), result, visited);
	}

	private void collect(Widget[] children, List<Widget> result, Map<Widget, Boolean> visited)
	{
		if (children != null)
		{
			for (Widget child : children)
			{
				collect(child, result, visited);
			}
		}
	}

	/** Call after restoration and before client scripts can recreate or modify widgets. */
	void invalidateTraversal()
	{
		trees.clear();
		templates.clear();
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
