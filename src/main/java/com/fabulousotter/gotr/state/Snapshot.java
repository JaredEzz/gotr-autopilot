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
package com.fabulousotter.gotr.state;

import com.fabulousotter.gotr.model.Alignment;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.model.CellTier;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.plan.Target;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Planner input captured once per game tick. Times are seconds; -1 means unknown.
 */
@Value
@Builder(toBuilder = true)
public class Snapshot
{
	public static final Snapshot OUTSIDE = Snapshot.builder().build();

	@Builder.Default
	Location location = Location.OUTSIDE;
	@Nullable
	Altar altarRoom;
	@Nullable
	WorldPoint playerLocation;

	@Builder.Default
	GamePhase phase = GamePhase.WAITING;
	@Builder.Default
	int secondsToStart = -1;
	@Builder.Default
	int secondsSinceStart = -1;
	@Builder.Default
	int secondsToNextGame = -1;
	int elementalEnergy;
	int catalyticEnergy;
	int power;
	int maxPower;
	@Nullable
	Altar activeElemental;
	@Nullable
	Altar activeCatalytic;
	@Builder.Default
	int altarSecondsRemaining = -1;
	boolean portalOpen;
	@Builder.Default
	int portalSecondsRemaining = -1;
	@Nullable
	String portalDirection;
	@Builder.Default
	int secondsSinceLastPortal = -1;
	@Builder.Default
	int secondsToNextPortal = -1;
	boolean anyPortalThisGame;
	int guardiansActive;
	@Builder.Default
	int guardiansMax = 10;

	int fragments;
	int essence;
	int elementalStones;
	int catalyticStones;
	int polyElementalStones;
	int polyCatalyticStones;
	@Nullable
	CellTier chargedCell;
	int unchargedCells;
	boolean chisel;
	boolean pickaxe;
	int freeSlots;
	@Builder.Default
	Set<Altar> talismans = ImmutableSet.of();
	int depositableRunes;
	@Nullable
	Altar baseRune;
	int baseRuneCount;
	@Builder.Default
	List<PouchState> pouches = ImmutableList.of();
	// Essence left before the closest-to-degrading pouch fails, and that pouch's capacity; -1 when
	// no degradable pouch is held.
	@Builder.Default
	int pouchUsesLeft = -1;
	@Builder.Default
	int pouchWorstCapacity = 0;

	boolean bindingNecklaceWorn;
	int necklaceCharges;
	boolean magicImbueActive;
	boolean lunarSpellbook;
	boolean runePouch;
	@Builder.Default
	int runecraftLevel = 1;
	@Builder.Default
	int agilityLevel = 1;
	@Builder.Default
	int magicLevel = 1;
	@Builder.Default
	Set<Altar> questLockedAltars = ImmutableSet.of();

	@Builder.Default
	List<BarrierState> barriers = ImmutableList.of();
	@Builder.Default
	List<WorldPoint> emptyTiles = ImmutableList.of();
	@Builder.Default
	List<WorldPoint> brokenTiles = ImmutableList.of();
	@Builder.Default
	Map<Altar, WorldPoint> portalGuardians = ImmutableMap.of();
	// Retain the previous target to avoid switching as distances change.
	@Nullable
	WorldPoint chosenTile;
	// Preserve in-progress actions when timing estimates fluctuate.
	@Nullable
	Step lastStep;
	boolean weakCellTablePresent;

	@Builder.Default
	int savedElementalPoints = -1;
	@Builder.Default
	int savedCatalyticPoints = -1;
	boolean pointsCredited;

	// Measured throughput (per game tick) and the crowd's projected rift close.
	@Builder.Default
	double fragmentsPerTick = 0.6;
	@Builder.Default
	double partsPerTick = 0.4;
	@Builder.Default
	double craftPerTick = 1.0;
	@Builder.Default
	double hugePerTick = 0.6;
	@Builder.Default
	int secondsToClose = -1;
	// Walking distances are in tiles; absent entries are unknown.
	@Builder.Default
	Map<Target, Integer> travelTicks = ImmutableMap.of();
	@Builder.Default
	Map<Altar, Integer> guardianTravelTicks = ImmutableMap.of();
	// Remains-to-workbench distance in tiles.
	@Builder.Default
	Map<Target, Integer> returnTicks = ImmutableMap.of();

	public int pouchStored()
	{
		int n = 0;
		for (PouchState pouch : pouches)
		{
			n += pouch.getStored();
		}
		return n;
	}

	public int pouchSpace()
	{
		int n = 0;
		for (PouchState pouch : pouches)
		{
			n += pouch.space();
		}
		return n;
	}

	public boolean anyPouchDegraded()
	{
		for (PouchState pouch : pouches)
		{
			if (pouch.isDegraded())
			{
				return true;
			}
		}
		return false;
	}

	public int essenceTotal()
	{
		return essence + pouchStored();
	}

	public int capacity()
	{
		return freeSlots + pouchSpace();
	}

	public int stonesTotal()
	{
		return elementalStones + catalyticStones + polyElementalStones + polyCatalyticStones;
	}

	public boolean hasStones()
	{
		return stonesTotal() > 0;
	}

	public int energy(Alignment alignment)
	{
		return alignment == Alignment.ELEMENTAL ? elementalEnergy : catalyticEnergy;
	}

	public int savedPoints(Alignment alignment)
	{
		return alignment == Alignment.ELEMENTAL ? savedElementalPoints : savedCatalyticPoints;
	}

	public int powerPercent()
	{
		return maxPower <= 0 ? 0 : (int) Math.round(100.0 * power / maxPower);
	}

	public boolean energyCapped()
	{
		return (elementalEnergy >= 1000 && catalyticEnergy >= 1000) || elementalEnergy + catalyticEnergy >= 1200;
	}
}
