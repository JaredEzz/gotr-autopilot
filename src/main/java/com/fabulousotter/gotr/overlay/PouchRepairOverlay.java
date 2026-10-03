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
import net.runelite.client.util.Text;

/**
 * Outlines the Dark Mage's "Can you repair my pouches?" dialogue option (or the step that leads to
 * it) so the pouches can be repaired without hunting through the contact menu.
 */
public class PouchRepairOverlay extends Overlay
{
	private static final Stroke STROKE = new BasicStroke(2f);

	private final Client client;
	private final GotrAutopilotConfig config;

	@Inject
	PouchRepairOverlay(Client client, GotrAutopilotConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.pouchRepairLoads() <= 0)
		{
			return null;
		}
		Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (options == null || options.isHidden())
		{
			return null;
		}
		Widget[] children = options.getChildren();
		if (children == null)
		{
			return null;
		}
		Widget repair = null;
		Widget path = null;
		for (Widget child : children)
		{
			if (child == null || child.isHidden() || child.getText() == null)
			{
				continue;
			}
			String text = Text.removeTags(child.getText()).toLowerCase();
			if (text.contains("repair my pouches"))
			{
				repair = child;
			}
			else if (text.contains("help with something"))
			{
				path = child;
			}
		}
		Widget target = repair != null ? repair : path;
		if (target == null)
		{
			return null;
		}
		Rectangle bounds = target.getBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return null;
		}
		Stroke old = graphics.getStroke();
		graphics.setStroke(STROKE);
		graphics.setColor(config.highlightColor());
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setStroke(old);
		return null;
	}
}
