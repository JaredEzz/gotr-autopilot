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
import com.fabulousotter.gotr.plan.Instruction;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.plan.Urgency;
import com.fabulousotter.gotr.state.GamePhase;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.Snapshot;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

public class InstructionOverlay extends OverlayPanel
{
	private static final Color INFO = new Color(200, 200, 200);
	private static final Color NORMAL = new Color(120, 255, 200);
	private static final Color HIGH = new Color(255, 120, 120);
	// Runecraft XP (level x 45) needs at least this much energy earned in the game.
	private static final int MIN_ENERGY = 300;

	private final GotrAutopilotPlugin plugin;
	private final GotrAutopilotConfig config;
	private String detail = "";
	private String[] detailLines = new String[0];

	@Inject
	InstructionOverlay(GotrAutopilotPlugin plugin, GotrAutopilotConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Instruction instruction = plugin.getInstruction();
		if (instruction.getStep() == Step.NOT_IN_GAME)
		{
			return null;
		}
		boolean compact = config.compactOverlay();
		Font small = FontManager.getRunescapeSmallFont();
		panelComponent.setPreferredSize(new Dimension(compact ? 180 : 260, 0));
		panelComponent.setBorder(compact ? new Rectangle(4, 3, 4, 3) : new Rectangle(5, 5, 5, 5));
		panelComponent.setGap(new Point(0, compact ? 1 : 0));
		Color color = instruction.getUrgency() == Urgency.HIGH ? HIGH : instruction.getUrgency() == Urgency.INFO ? INFO : NORMAL;
		if (compact)
		{
			// A line rather than a title so a long headline wraps instead of running off the panel.
			panelComponent.getChildren().add(LineComponent.builder().left(instruction.getHeadline()).leftColor(color).leftFont(small).build());
		}
		else
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(instruction.getHeadline()).color(color).build());
		}
		if (!detail.equals(instruction.getDetail()))
		{
			detail = instruction.getDetail();
			detailLines = detail.split(" - ");
		}
		for (String part : detailLines)
		{
			if (!part.isEmpty())
			{
				panelComponent.getChildren().add(LineComponent.builder().left(part).leftFont(small).build());
			}
		}
		if (config.showTimers())
		{
			Snapshot s = plugin.getSnapshot();
			if (compact)
			{
				compactTimers(s);
			}
			else
			{
				// A gap between the step's text and the figures below it.
				panelComponent.getChildren().add(LineComponent.builder().left(" ").build());
				timers(s);
			}
		}
		return super.render(graphics);
	}

	/**
	 * The timers on two small rows: the game clock and portal on the first, points and
	 * the cell on the second, with the necklace on a third only while it is worn.
	 */
	private void compactTimers(Snapshot s)
	{
		String left = null;
		String right = null;
		Color rightColor = null;
		if (s.getPhase() == GamePhase.ACTIVE)
		{
			StringBuilder sb = new StringBuilder();
			if (s.getSecondsSinceStart() >= 0)
			{
				sb.append(clock(s.getSecondsSinceStart()));
			}
			if (s.getSecondsToClose() >= 0)
			{
				sb.append(sb.length() > 0 ? "  " : "").append("closes ~").append(clock(s.getSecondsToClose()));
			}
			left = sb.toString();
			if (s.isPortalOpen())
			{
				right = s.getPortalSecondsRemaining() >= 0 ? "portal " + s.getPortalSecondsRemaining() + "s" : "portal open";
				rightColor = HIGH;
			}
			else if (s.getSecondsToNextPortal() >= 0)
			{
				right = "portal ~" + s.getSecondsToNextPortal() + "s";
			}
		}
		else if (s.getPhase() == GamePhase.COUNTDOWN && s.getSecondsToStart() >= 0)
		{
			left = "starts in " + s.getSecondsToStart() + "s";
		}
		else if (s.getLocation() == Location.LOBBY && s.getSecondsToNextGame() >= 0)
		{
			left = "next game ~" + clock(s.getSecondsToNextGame());
		}
		if (left != null || right != null)
		{
			smallLine(left, right, rightColor);
		}
		left = s.getSavedElementalPoints() >= 0 || s.getSavedCatalyticPoints() >= 0
			? "E " + Math.max(0, s.getSavedElementalPoints()) + " / C " + Math.max(0, s.getSavedCatalyticPoints())
			: null;
		right = s.getChargedCell() != null ? s.getChargedCell().getLabel() + " cell" : null;
		if (left != null || right != null)
		{
			smallLine(left, right, null);
		}
		if (s.getPhase() == GamePhase.ACTIVE && config.showMinEnergy())
		{
			int earned = s.getElementalEnergy() + s.getCatalyticEnergy();
			if (earned < MIN_ENERGY)
			{
				smallLine("XP min", earned + " / " + MIN_ENERGY, HIGH);
			}
		}
		if (s.isBindingNecklaceWorn())
		{
			smallLine("necklace", s.getNecklaceCharges() + " charges", s.getNecklaceCharges() <= 2 ? HIGH : null);
		}
	}

	private void smallLine(@Nullable String left, @Nullable String right, @Nullable Color rightColor)
	{
		Font small = FontManager.getRunescapeSmallFont();
		LineComponent.LineComponentBuilder builder = LineComponent.builder().leftFont(small).rightFont(small);
		if (left != null)
		{
			builder.left(left);
		}
		if (right != null)
		{
			builder.right(right);
		}
		if (rightColor != null)
		{
			builder.rightColor(rightColor);
		}
		panelComponent.getChildren().add(builder.build());
	}

	private void timers(Snapshot s)
	{
		if (s.getPhase() == GamePhase.ACTIVE)
		{
			if (s.getSecondsSinceStart() >= 0)
			{
				line("Game", clock(s.getSecondsSinceStart()));
			}
			if (s.isPortalOpen())
			{
				line("Portal", s.getPortalSecondsRemaining() >= 0 ? "open - " + s.getPortalSecondsRemaining() + "s" : "open", HIGH);
			}
			else if (s.getSecondsToNextPortal() >= 0)
			{
				line("Next portal", "~" + s.getSecondsToNextPortal() + "s");
			}
			if (s.getSecondsToClose() >= 0)
			{
				line("Rift closes", "~" + clock(s.getSecondsToClose()), s.getSecondsToClose() < 60 ? HIGH : null);
			}
		}
		else if (s.getPhase() == GamePhase.COUNTDOWN && s.getSecondsToStart() >= 0)
		{
			line("Starts in", s.getSecondsToStart() + "s");
		}
		if (s.getLocation() == Location.LOBBY && s.getPhase() != GamePhase.COUNTDOWN && s.getSecondsToNextGame() >= 0)
		{
			line("Next game", "~" + clock(s.getSecondsToNextGame()));
		}
		if (s.getSavedElementalPoints() >= 0 || s.getSavedCatalyticPoints() >= 0)
		{
			line("Points", "E " + Math.max(0, s.getSavedElementalPoints()) + " / C " + Math.max(0, s.getSavedCatalyticPoints()));
		}
		if (s.getPhase() == GamePhase.ACTIVE && config.showMinEnergy())
		{
			int earned = s.getElementalEnergy() + s.getCatalyticEnergy();
			if (earned < MIN_ENERGY)
			{
				line("XP min", earned + " / " + MIN_ENERGY, HIGH);
			}
		}
		if (s.getChargedCell() != null)
		{
			line("Cell", s.getChargedCell().getLabel());
		}
		if (s.isBindingNecklaceWorn())
		{
			line("Necklace", s.getNecklaceCharges() + " charges", s.getNecklaceCharges() <= 2 ? HIGH : null);
		}
	}

	private void line(String left, String right)
	{
		line(left, right, null);
	}

	private void line(String left, String right, @Nullable Color rightColor)
	{
		LineComponent.LineComponentBuilder builder = LineComponent.builder().left(left).right(right);
		if (rightColor != null)
		{
			builder.rightColor(rightColor);
		}
		panelComponent.getChildren().add(builder.build());
	}

	private static String clock(int seconds)
	{
		int remainder = seconds % 60;
		return seconds / 60 + (remainder < 10 ? ":0" : ":") + remainder;
	}
}
