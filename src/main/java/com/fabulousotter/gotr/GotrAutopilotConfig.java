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
package com.fabulousotter.gotr;

import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.plan.BarrierPriority;
import com.fabulousotter.gotr.plan.Strategy;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup(GotrAutopilotConfig.GROUP)
public interface GotrAutopilotConfig extends Config
{
	String GROUP = "gotrautopilot";

	@ConfigSection(
		name = "Strategy",
		description = "Which plan to follow and its tuning",
		position = 0
	)
	String strategySection = "strategy";

	@ConfigSection(
		name = "Display",
		description = "Overlay, highlights and alerts",
		position = 1
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "strategy",
		name = "Strategy",
		description = "General/team rotation or the solo single-barrier plan",
		section = strategySection,
		position = 0
	)
	default Strategy strategy()
	{
		return Strategy.MASS;
	}

	@Range(
		min = 30,
		max = 300
	)
	@ConfigItem(
		keyName = "openingFragments",
		name = "Opening fragment target",
		description = "Fragments to mine before the first crafting run (wiki: ~120 team, ~110 solo, 165 mass)",
		section = strategySection,
		position = 1
	)
	default int openingFragments()
	{
		return 120;
	}

	@Range(
		min = 20,
		max = 90
	)
	@ConfigItem(
		keyName = "craftWindow",
		name = "Craft window (s)",
		description = "Stop mining and craft essence this many seconds before the altars change",
		section = strategySection,
		position = 2
	)
	default int craftWindow()
	{
		return 50;
	}

	@Range(
		min = 1,
		max = 100
	)
	@ConfigItem(
		keyName = "minFragmentsToCraft",
		name = "Min fragments to craft",
		description = "Do not start a crafting run with fewer fragments than this",
		section = strategySection,
		position = 3
	)
	default int minFragmentsToCraft()
	{
		return 30;
	}

	@Range(
		max = 10
	)
	@ConfigItem(
		keyName = "desiredGuardians",
		name = "Guardians wanted",
		description = "Build guardians with strong/overcharged cells until this many are active",
		section = strategySection,
		position = 4
	)
	default int desiredGuardians()
	{
		return 2;
	}

	@Range(
		max = 1000
	)
	@ConfigItem(
		keyName = "maxImbalance",
		name = "Max energy imbalance",
		description = "Skip a high tier altar when it would put one energy type this far ahead of the other",
		section = strategySection,
		position = 5
	)
	default int maxImbalance()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "balanceWithSaved",
		name = "Balance with banked points",
		description = "Count reward points you already hold when choosing which type to craft",
		section = strategySection,
		position = 6
	)
	default boolean balanceWithSaved()
	{
		return true;
	}

	@ConfigItem(
		keyName = "protectRightBarrier",
		name = "Solo: leave right barrier weak",
		description = "Never recharge the right-most barrier so the 60% explosion does less damage",
		section = strategySection,
		position = 7
	)
	default boolean protectRightBarrier()
	{
		return true;
	}

	@Range(
		min = 1,
		max = 68
	)
	@ConfigItem(
		keyName = "portalMinCapacity",
		name = "Portal min room",
		description = "Only chase a portal when you have room for at least this much essence",
		section = strategySection,
		position = 8
	)
	default int portalMinCapacity()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "barrierPriority",
		name = "Barrier target",
		description = "Lowest health: tend the most damaged or upgradable barrier. Closest: tend the nearest "
			+ "barrier that needs work (not Overcharged, or at 70% health or less), preferring the side the "
			+ "next step heads to (west to deposit runes, east for cells or the remains); when nothing needs "
			+ "work, recharge the closest healthy barrier",
		section = strategySection,
		position = 9
	)
	default BarrierPriority barrierPriority()
	{
		return BarrierPriority.LOWEST_HEALTH;
	}

	@ConfigItem(
		keyName = "preferredAltars",
		name = "Always prefer altars",
		description = "Any of these altars is chosen over others whenever it is usable, whatever the cell tier or energy balance",
		section = strategySection,
		position = 10
	)
	default Set<Altar> preferredAltars()
	{
		return EnumSet.noneOf(Altar.class);
	}

	@ConfigItem(
		keyName = "dispreferredAltars",
		name = "Always avoid altars",
		description = "Only chosen when nothing else is available",
		section = strategySection,
		position = 11
	)
	default Set<Altar> dispreferredAltars()
	{
		return EnumSet.noneOf(Altar.class);
	}

	@ConfigItem(
		keyName = "notification",
		name = "Notify on new step",
		description = "Notification when the instruction changes",
		section = displaySection,
		position = 0
	)
	default Notification notification()
	{
		return Notification.ON;
	}

	@Range(
		max = 300
	)
	@ConfigItem(
		keyName = "notifyAfterSeconds",
		name = "Notify only after (s)",
		description = "Only notify when the previous step lasted at least this long (a mining or crafting stint you may have tabbed out of). 0 = always",
		section = displaySection,
		position = 1
	)
	default int notifyAfterSeconds()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "showPath",
		name = "Path line",
		description = "Draw the walking route from you to the current target",
		section = displaySection,
		position = 2
	)
	default boolean showPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hintArrow",
		name = "Hint arrow",
		description = "Point the game's hint arrow at the current target",
		section = displaySection,
		position = 3
	)
	default boolean hintArrow()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Outline colour for the current target and items",
		section = displaySection,
		position = 4
	)
	default Color highlightColor()
	{
		return new Color(0, 255, 180, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "urgentColor",
		name = "Urgent colour",
		description = "Outline colour when the step is urgent (broken tile, open portal)",
		section = displaySection,
		position = 5
	)
	default Color urgentColor()
	{
		return new Color(255, 80, 80, 230);
	}

	@ConfigItem(
		keyName = "showTimers",
		name = "Show timers",
		description = "Portal, altar rotation and energy lines under the instruction",
		section = displaySection,
		position = 6
	)
	default boolean showTimers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "compactOverlay",
		name = "Compact overlay",
		description = "A narrower panel in the small font with the timers folded onto two lines, for fixed and small layouts",
		section = displaySection,
		position = 7
	)
	default boolean compactOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTargetLabel",
		name = "Label on target",
		description = "Draw the current step's text above the object, NPC or tile it points at",
		section = displaySection,
		position = 8
	)
	default boolean showTargetLabel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideGuardianForCells",
		name = "Hide guardian for cells",
		description = "Hide the Great Guardian while a cell is to be placed on a tile or barrier, so it cannot be clicked by mistake",
		section = displaySection,
		position = 9
	)
	default boolean hideGuardianForCells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatSteps",
		name = "Steps in chat",
		description = "Also print each new step as a game message",
		section = displaySection,
		position = 10
	)
	default boolean chatSteps()
	{
		return false;
	}

	@ConfigItem(
		keyName = "comboAltarClickGuard",
		name = "Combo altar click guard",
		description = "While a combination rune is possible at an elemental altar, push Craft-rune off the "
			+ "left-click (it stays available on right-click) so you never craft the plain rune by mistake",
		section = displaySection,
		position = 11
	)
	default boolean comboAltarClickGuard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "magicImbuePrompt",
		name = "Magic Imbue prompt",
		description = "Highlight the magic book tab, or the Magic Imbue spell when the book is open, while a "
			+ "combination rune is about to be crafted and the buff is not up",
		section = displaySection,
		position = 12
	)
	default boolean magicImbuePrompt()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropEssenceLeftClick",
		name = "Drop essence on left click",
		description = "While the plan says to drop an essence, make Drop the left-click option on it",
		section = displaySection,
		position = 13
	)
	default boolean dropEssenceLeftClick()
	{
		return true;
	}
}
