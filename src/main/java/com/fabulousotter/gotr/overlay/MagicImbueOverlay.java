/*
 * Copyright (c) 2026, FabulousOtter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fabulousotter.gotr.overlay;

import com.fabulousotter.gotr.GotrAutopilotConfig;
import com.fabulousotter.gotr.GotrAutopilotPlugin;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Points at the spell a step needs: NPC Contact while a pouch repair is due, or Magic Imbue while a
 * combination rune is about to be crafted. Outlines the spell when the magic book is open, or its
 * side tab when it is not. Once imbued, it points back at the inventory tab, where the base runes
 * are used on the altar.
 */
public class MagicImbueOverlay extends Overlay
{
	private static final Stroke STROKE = new BasicStroke(2f);

	private final Client client;
	private final GotrAutopilotPlugin plugin;
	private final GotrAutopilotConfig config;

	@Inject
	MagicImbueOverlay(Client client, GotrAutopilotPlugin plugin, GotrAutopilotConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Snapshot s = plugin.getSnapshot();
		Step step = plugin.getInstruction().getStep();

		if (config.pouchRepairLoads() > 0 && step == Step.PRE_REPAIR_POUCHES
			&& s.isLunarSpellbook() && s.isRunePouch())
		{
			Widget darkMage = scanForText(client.getWidget(InterfaceID.LunarContactNpc.UNIVERSE), "dark mage", 6);
			if (darkMage != null)
			{
				outline(graphics, darkMage.getBounds());
				return null;
			}
			highlightSpell(graphics, InterfaceID.MagicSpellbook.NPC_CONTACT);
			return null;
		}

		if (!config.magicImbuePrompt() || s.getLocation() != Location.ALTAR_ROOM
			|| !s.isLunarSpellbook() || step != Step.CRAFT_RUNES || s.essenceTotal() <= 0
			|| !plugin.comboAvailableAt(s.getAltarRoom()))
		{
			return null;
		}
		if (!s.isMagicImbueActive())
		{
			highlightSpell(graphics, InterfaceID.MagicSpellbook.MAGIC_IMBUE);
			return null;
		}
		// Imbued: the base runes are in the inventory, so point back at it while the book is open.
		Widget book = client.getWidget(InterfaceID.MagicSpellbook.MAGIC_IMBUE);
		if (book != null && !book.isHidden())
		{
			highlightTab(graphics, tab(InterfaceID.ToplevelPreEoc.STONE3, InterfaceID.ToplevelOsrsStretch.STONE3, InterfaceID.Toplevel.STONE3));
		}
		return null;
	}

	// Outline a spell once the magic book is open, otherwise point at the magic tab.
	private void highlightSpell(Graphics2D graphics, int spell)
	{
		Widget widget = client.getWidget(spell);
		if (widget != null && !widget.isHidden())
		{
			outline(graphics, widget.getBounds());
			return;
		}
		highlightTab(graphics, tab(InterfaceID.ToplevelPreEoc.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6, InterfaceID.Toplevel.STONE6));
	}

	private void highlightTab(Graphics2D graphics, @Nullable Widget tab)
	{
		if (tab != null && !tab.isHidden())
		{
			outline(graphics, tab.getBounds());
		}
	}

	// A side tab across the three layouts: modern resizable, classic resizable, then fixed.
	@Nullable
	private Widget tab(int modern, int stretch, int fixed)
	{
		if (!client.isResized())
		{
			return client.getWidget(fixed);
		}
		Widget widget = client.getWidget(modern);
		if (widget != null && !widget.isHidden())
		{
			return widget;
		}
		return client.getWidget(stretch);
	}

	private void outline(Graphics2D graphics, Rectangle bounds)
	{
		if (bounds == null || bounds.isEmpty())
		{
			return;
		}
		Stroke old = graphics.getStroke();
		graphics.setStroke(STROKE);
		graphics.setColor(config.highlightColor());
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setStroke(old);
	}

	// The first widget (to a bounded depth) whose text contains the needle.
	@Nullable
	private static Widget scanForText(@Nullable Widget widget, String needle, int depth)
	{
		if (widget == null || depth < 0 || widget.isHidden())
		{
			return null;
		}
		String text = widget.getText();
		if (text != null && Text.removeTags(text).toLowerCase().contains(needle))
		{
			return widget;
		}
		for (Widget[] group : new Widget[][]{widget.getChildren(), widget.getDynamicChildren()})
		{
			if (group == null)
			{
				continue;
			}
			for (Widget child : group)
			{
				Widget found = scanForText(child, needle, depth - 1);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
