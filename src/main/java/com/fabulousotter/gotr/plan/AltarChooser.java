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
package com.fabulousotter.gotr.plan;

import com.fabulousotter.gotr.model.Alignment;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.model.CellTier;
import com.fabulousotter.gotr.model.CombinationRune;
import com.fabulousotter.gotr.state.BarrierState;
import com.fabulousotter.gotr.state.Snapshot;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

public class AltarChooser
{
	// Big enough to dominate the energy and travel terms, so a preferred altar always wins.
	private static final int PREFERENCE_WEIGHT = 1000;

	private AltarChooser()
	{
	}

	// +1 for an always-prefer altar, -1 for an always-avoid altar, 0 otherwise.
	static int preference(PlannerSettings c, Altar altar)
	{
		if (c.getPreferredAltars().contains(altar))
		{
			return 1;
		}
		if (c.getDispreferredAltars().contains(altar))
		{
			return -1;
		}
		return 0;
	}

	@Nullable
	static AltarChoice choose(Snapshot s, PlannerSettings c)
	{
		Set<Altar> candidates = new LinkedHashSet<>();
		if (s.getActiveElemental() != null)
		{
			candidates.add(s.getActiveElemental());
		}
		if (s.getActiveCatalytic() != null)
		{
			candidates.add(s.getActiveCatalytic());
		}
		candidates.addAll(s.getTalismans());

		int essence = Math.max(1, s.essenceTotal());
		AltarChoice best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Altar altar : candidates)
		{
			if (altar.getLevelRequired() > s.getRunecraftLevel() || s.getQuestLockedAltars().contains(altar))
			{
				continue;
			}
			if (s.energy(altar.getAlignment()) >= 1000)
			{
				continue;
			}
			boolean viaTalisman = altar != s.getActiveElemental() && altar != s.getActiveCatalytic();
			CombinationRune combo = combinationAt(altar, s, c);
			int perEssence = combo != null ? 3 : 2;
			AltarChoice choice = new AltarChoice(altar, combo, viaTalisman, perEssence, "");
			double score = c.getStrategy() == Strategy.MASS
				? massScore(choice, s, c, essence) + PREFERENCE_WEIGHT * preference(c, altar)
				: 0;
			boolean win = best == null || (c.getStrategy() == Strategy.MASS
				? score > bestScore
				: better(choice, best, s, c, essence));
			if (win)
			{
				best = choice;
				bestScore = score;
			}
		}
		if (best == null)
		{
			return null;
		}
		return new AltarChoice(best.getAltar(), best.getCombination(), best.isViaTalisman(), best.getEnergyPerEssence(),
			reason(best, essence));
	}

	// Why an open altar cannot be used by this player, or null when it can.
	@Nullable
	static String unusableReason(Altar altar, Snapshot s)
	{
		if (altar.getLevelRequired() > s.getRunecraftLevel())
		{
			return "needs " + altar.getLevelRequired() + " Runecraft";
		}
		if (s.getQuestLockedAltars().contains(altar))
		{
			return "needs " + (altar.getRequiredQuest() == null ? "a quest" : altar.getRequiredQuest().getName());
		}
		if (s.energy(altar.getAlignment()) >= 1000)
		{
			return altar.getAlignment().getLabel().toLowerCase() + " energy is maxed";
		}
		return null;
	}

	@Nullable
	public static CombinationRune combinationAt(Altar altar, Snapshot s, PlannerSettings c)
	{
		if (!c.isCombinationRunes() || altar.getAlignment() != Alignment.ELEMENTAL || s.getBaseRune() == null)
		{
			return null;
		}
		CombinationRune combo = CombinationRune.of(altar, s.getBaseRune());
		if (combo == null || combo.getLevelRequired() > s.getRunecraftLevel())
		{
			return null;
		}
		if (s.getBaseRuneCount() <= 0 || !s.isBindingNecklaceWorn() || s.getNecklaceCharges() <= 0)
		{
			return null;
		}
		if (!s.isLunarSpellbook() && !s.getTalismans().contains(s.getBaseRune()))
		{
			return null;
		}
		return combo;
	}

	private static boolean better(AltarChoice a, AltarChoice b, Snapshot s, PlannerSettings c, int essence)
	{
		int prefA = preference(c, a.getAltar());
		int prefB = preference(c, b.getAltar());
		if (prefA != prefB)
		{
			return prefA > prefB;
		}
		int tierA = effectiveTier(a);
		int tierB = effectiveTier(b);
		boolean aStrong = tierA >= CellTier.STRONG.getRank() && imbalanceAfter(a, s, c, essence) <= c.getMaxImbalance();
		boolean bStrong = tierB >= CellTier.STRONG.getRank() && imbalanceAfter(b, s, c, essence) <= c.getMaxImbalance();
		if (aStrong != bStrong)
		{
			return aStrong;
		}
		if (aStrong)
		{
			if (tierA != tierB)
			{
				return tierA > tierB;
			}
			return projected(a.getAltar().getAlignment(), s, c) < projected(b.getAltar().getAlignment(), s, c);
		}
		// Neither is a safe high-tier pick: craft the type we are short of.
		int needA = projected(a.getAltar().getAlignment(), s, c);
		int needB = projected(b.getAltar().getAlignment(), s, c);
		if (needA != needB)
		{
			return needA < needB;
		}
		if (tierA != tierB)
		{
			return tierA > tierB;
		}
		return a.getEnergyPerEssence() > b.getEnergyPerEssence();
	}

	// Score the trip's yield against imbalance and travel time.
	static double massScore(AltarChoice choice, Snapshot s, PlannerSettings c, int essence)
	{
		double energy = choice.getEnergyPerEssence() * essence + cellValue(choice.getAltar().getCellTier(), s);
		double excess = Math.max(0, imbalanceAfter(choice, s, c, essence) - c.getMaxImbalance());
		double seconds = tripSeconds(s, choice.getAltar());
		// Fixed opportunity cost: one essence per two ticks, worth two energy.
		double timeCost = seconds * (2.0 / 1.2) * 0.5;
		return energy - excess - timeCost;
	}

	static int cellValue(CellTier tier, Snapshot s)
	{
		if (s.getChargedCell() != null || s.getUnchargedCells() <= 0)
		{
			return 0;
		}
		for (BarrierState barrier : s.getBarriers())
		{
			if (barrier.getTier().getRank() < tier.getRank())
			{
				return 2 * tier.getStrengthenEnergy();
			}
		}
		if (!s.getBarriers().isEmpty())
		{
			return 2 * tier.getRechargeEnergy();
		}
		if (s.getGuardiansActive() < s.getGuardiansMax())
		{
			return tier.getGuardianEnergy();
		}
		// Keep a nonzero value for building when no recharge target exists.
		return s.getEmptyTiles().isEmpty() ? 0 : 4 + tier.getRechargeEnergy();
	}

	static double tripSeconds(Snapshot s, Altar altar)
	{
		Integer walk = s.getGuardianTravelTicks().get(altar);
		Integer back = s.getTravelTicks().get(Target.GREAT_GUARDIAN);
		double walkTicks = walk == null ? 25 : walk;
		double backTicks = back == null ? 20 : back;
		return ((walkTicks + backTicks) / 2.0 + altar.getInsideTiles() + 4) * 0.6;
	}

	private static int effectiveTier(AltarChoice choice)
	{
		int tier = choice.getAltar().getCellTier().getRank();
		return choice.getCombination() != null ? Math.min(CellTier.OVERCHARGED.getRank(), tier + 1) : tier;
	}

	static int projected(Alignment alignment, Snapshot s, PlannerSettings c)
	{
		int energy = s.energy(alignment);
		if (c.isBalanceWithSavedPoints() && s.savedPoints(alignment) >= 0)
		{
			energy += 100 * s.savedPoints(alignment);
		}
		return energy;
	}

	private static int imbalanceAfter(AltarChoice choice, Snapshot s, PlannerSettings c, int essence)
	{
		Alignment alignment = choice.getAltar().getAlignment();
		int mine = projected(alignment, s, c) + choice.getEnergyPerEssence() * essence;
		int other = projected(alignment.other(), s, c);
		return Math.max(0, mine - other);
	}

	private static String reason(AltarChoice choice, int essence)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(choice.getAltar().getCellTier().getLabel()).append(" cell");
		if (choice.getCombination() != null)
		{
			sb.append(" - ").append(choice.getCombination().getLabel()).append(" runes, 3 energy each");
		}
		if (choice.isViaTalisman())
		{
			sb.append(" - use your ").append(choice.getAltar().getLabel().toLowerCase()).append(" talisman");
		}
		sb.append(" - +").append(choice.getEnergyPerEssence() * essence).append(' ')
			.append(choice.getAltar().getAlignment().getLabel().toLowerCase());
		return sb.toString();
	}
}
