package com.minimapresize;

import java.util.Arrays;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WidgetInputGateTest
{
	@Test
	public void nativeBlockersAndMinimapWalkingAreDisabledThenRestored()
	{
		Widget root = mock(Widget.class);
		Widget child = mock(Widget.class);
		when(root.getStaticChildren()).thenReturn(new Widget[]{child});
		when(root.getNoClickThrough()).thenReturn(true);
		when(child.getNoScrollThrough()).thenReturn(true);
		when(child.getContentType()).thenReturn(1338);
		when(child.getClickMask()).thenReturn(1234);
		WidgetInputGate gate = new WidgetInputGate();
		gate.exclude(root);
		verify(root).setNoClickThrough(false);
		verify(child).setNoScrollThrough(false);
		verify(child).setContentType(0);
		verify(child).setClickMask(0);
		gate.restore();
		verify(root).setNoClickThrough(true);
		verify(child).setNoScrollThrough(true);
		verify(child).setContentType(1338);
		verify(child).setClickMask(1234);
		// Do not stop orb stat/timer listeners or change game-controlled visibility.
		verify(root, never()).setHidden(anyBoolean());
		verify(child, never()).setHidden(anyBoolean());
		verify(root, never()).setHasListener(anyBoolean());
		verify(child, never()).setHasListener(anyBoolean());
	}

	@Test
	public void sparseNativeMenuOperationsAreRestoredEvenIfClearActionsMutatesTheArray()
	{
		Widget widget = mock(Widget.class);
		String[] actions = {"Inventory", null, "Configure"};
		Object[] onOp = {914, "argument"};
		when(widget.getActions()).thenReturn(actions);
		when(widget.getOnOpListener()).thenReturn(onOp);
		doAnswer(invocation -> { Arrays.fill(actions, null); return null; }).when(widget).clearActions();
		doAnswer(invocation -> { actions[invocation.getArgument(0)] = invocation.getArgument(1); return null; })
			.when(widget).setAction(anyInt(), anyString());
		WidgetInputGate gate = new WidgetInputGate();
		gate.exclude(widget);
		assertArrayEquals(new String[]{null, null, null}, actions);
		verify(widget).setOnOpListener((Object[]) null);
		gate.restore();
		assertArrayEquals(new String[]{"Inventory", null, "Configure"}, actions);
		verify(widget).setOnOpListener(onOp);
		clearInvocations(widget);
		gate.restore();
		verifyNoInteractions(widget);
	}

	@Test
	public void traversesAllKindsOfChildrenWithoutDuplicatingSharedNodes()
	{
		Widget root = mock(Widget.class);
		Widget dynamic = mock(Widget.class);
		Widget nested = mock(Widget.class);
		when(root.getDynamicChildren()).thenReturn(new Widget[]{dynamic, null});
		when(root.getNestedChildren()).thenReturn(new Widget[]{nested, dynamic});
		when(nested.getStaticChildren()).thenReturn(new Widget[]{root});
		WidgetInputGate gate = new WidgetInputGate();
		gate.exclude(root);
		verify(dynamic, times(1)).setNoClickThrough(false);
		verify(nested, times(1)).setNoClickThrough(false);
		verify(root, times(1)).setNoClickThrough(false);
		gate.restore();
	}
}
