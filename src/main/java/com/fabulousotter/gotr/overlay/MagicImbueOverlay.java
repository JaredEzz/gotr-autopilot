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
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Points at Magic Imbue while a combination rune is about to be crafted without the buff: the
 * magic book tab when the book is closed, or the spell itself once the book is open.
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
		if (!config.magicImbuePrompt())
		{
			return null;
		}
		Snapshot s = plugin.getSnapshot();
		if (s.getLocation() != Location.ALTAR_ROOM || !s.isLunarSpellbook() || s.isMagicImbueActive()
			|| s.essenceTotal() <= 0 || !plugin.comboAvailableAt(s.getAltarRoom()))
		{
			return null;
		}
		Widget spell = client.getWidget(InterfaceID.MagicSpellbook.MAGIC_IMBUE);
		if (spell != null && !spell.isHidden())
		{
			outline(graphics, spell.getBounds());
			return null;
		}
		// The book is closed: point at its side tab so it can be opened.
		Widget tab = tabWidget();
		if (tab != null && !tab.isHidden())
		{
			outline(graphics, tab.getBounds());
		}
		return null;
	}

	private Widget tabWidget()
	{
		if (!client.isResized())
		{
			return client.getWidget(InterfaceID.Toplevel.STONE6);
		}
		Widget modern = client.getWidget(InterfaceID.ToplevelPreEoc.STONE6);
		if (modern != null && !modern.isHidden())
		{
			return modern;
		}
		return client.getWidget(InterfaceID.ToplevelOsrsStretch.STONE6);
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
}
