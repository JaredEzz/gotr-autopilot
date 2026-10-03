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
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.PouchState;
import com.fabulousotter.gotr.state.Snapshot;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Selects an instruction from a snapshot without accessing the client or retaining state.
 */
public class Planner
{
	private static final Set<Integer> STONE_ITEMS = ImmutableSet.of(
		ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL,
		ItemID.GOTR_GUARDIAN_STONE_CATALYTIC,
		ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL,
		ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC
	);
	private static final Set<Integer> FRAGMENT_ITEM = ImmutableSet.of(ItemID.GOTR_GUARDIAN_FRAGMENT);
	private static final Set<Integer> ESSENCE_ITEM = ImmutableSet.of(ItemID.GOTR_GUARDIAN_ESSENCE);
	private static final int TILE_REPAIR_FRAGMENTS = 12;
	private static final int RUMBLE_PERCENT = 60;
	private static final int MIN_TRIP_ESSENCE = 10;
	// Hysteresis prevents close-time jitter from alternating mine and craft instructions.
	private static final int MIN_MINING_LEG = 8;
	// craftingUnderway() lets an existing run finish below this threshold.
	private static final int MIN_TOPUP = 5;
	private static final int GRACE_MARGIN_SECONDS = 20;
	private static final int DROP_ESSENCE_WAIT_SECONDS = 30;
	private static final int SPARE_CELL_TILES = 10;
	/** Smallest load worth an altar trip squeezed in before the last portal of the round. */
	private static final int MIN_PRE_PORTAL_TRIP = 15;
	/** Shortest stint at the parts worth walking over for while the altars are closed. */
	private static final int MIN_PARTS_STINT_SECONDS = 10;
	/** Seconds before the game starts at which the plan moves to the remains regardless of cells. */
	private static final int POSITION_SECONDS = 10;
	/** Portal cadence and the time one costs: the walk in, the 30 s inside and the altar trip for the essence. */
	private static final int PORTAL_INTERVAL_SECONDS = 140;
	private static final int PORTAL_VISIT_SECONDS = 40;

	public Instruction plan(Snapshot s, PlannerSettings c)
	{
		if (s.getLocation() == Location.OUTSIDE)
		{
			return Instruction.builder().step(Step.NOT_IN_GAME).headline("").urgency(Urgency.INFO).build();
		}
		if (s.getLocation() == Location.LOBBY)
		{
			return lobby(s);
		}
		switch (s.getPhase())
		{
			case ACTIVE:
				return active(s, c);
			case CLOSING:
			case ENDED:
				return ended(s, c);
			default:
				return preGame(s, c);
		}
	}

	private Instruction lobby(Snapshot s)
	{
		String detail;
		switch (s.getPhase())
		{
			case ACTIVE:
			case CLOSING:
				detail = "A game is in progress";
				break;
			case COUNTDOWN:
				detail = "Starting soon";
				break;
			default:
				detail = "";
				break;
		}
		return Instruction.builder().step(Step.IDLE_LOBBY).headline("Waiting for the next game")
			.detail(detail).urgency(Urgency.INFO).build();
	}

	private Instruction preGame(Snapshot s, PlannerSettings c)
	{
		int t = s.getSecondsToStart();
		String when = "";
		if (s.getLocation() == Location.ALTAR_ROOM)
		{
			return Instruction.builder().step(Step.LEAVE_ALTAR).headline("Leave the altar")
				.detail(when).target(Target.ALTAR_EXIT).build();
		}
		if (s.getLocation() == Location.HUGE_REMAINS)
		{
			return Instruction.builder().step(Step.WAIT_FOR_PORTAL).headline("Wait for the game to start")
				.detail(when).urgency(Urgency.INFO).build();
		}
		if (s.isPortalOpen() && s.getUnchargedCells() >= 10 && !s.anyPouchDegraded())
		{
			return Instruction.builder().step(Step.ENTER_PORTAL).headline("Enter the portal and wait inside")
				.detail("Wait inside for the next game").target(Target.PORTAL).build();
		}
		if (s.getDepositableRunes() > 0)
		{
			return Instruction.builder().step(Step.DEPOSIT_RUNES).headline("Deposit your runes")
				.detail(keepBaseRune(s))
				.target(Target.DEPOSIT_POOL).build();
		}
		if (s.anyPouchDegraded() || pouchWillDegrade(s, c))
		{
			boolean degraded = s.anyPouchDegraded();
			String how = s.isLunarSpellbook() && s.isRunePouch()
				? "Cast NPC Contact: Dark Mage (Astral Contact)"
				: "Right-click Apprentice Cordelia and choose Repair";
			return Instruction.builder().step(Step.PRE_REPAIR_POUCHES)
				.headline(degraded ? "Repair your pouches" : "Repair your pouches before the next game")
				.detail(how).target(Target.APPRENTICE_CORDELIA).items(degradedPouches(s)).build();
		}
		if (s.getUnchargedCells() < 10)
		{
			return Instruction.builder().step(Step.PRE_TAKE_CELLS).headline("Collect uncharged cells")
				.detail(when).target(Target.UNCHARGED_CELL_TABLE).build();
		}
		// In the final seconds stop collecting or spending a cell and head to the remains.
		boolean positioning = t >= 0 && t <= POSITION_SECONDS;
		boolean cellReachable = t < 0 || t >= 15 || walkSeconds(s, Target.WEAK_CELL_TABLE, 0) <= t;
		if (!positioning && s.getChargedCell() == null && s.isWeakCellTablePresent() && cellReachable)
		{
			return Instruction.builder().step(Step.PRE_TAKE_WEAK_CELL).headline("Collect a weak cell")
				.detail(when).target(Target.WEAK_CELL_TABLE).build();
		}
		if (!positioning && s.getChargedCell() != null)
		{
			Instruction spend = spendCellBeforeGame(s, c, when);
			if (spend != null)
			{
				return spend;
			}
		}
		// Use the opening target when positioning, not the current top-up size.
		Target remains = miningTarget(s, c.getOpeningFragmentTarget());
		return Instruction.builder().step(Step.PRE_POSITION)
			.headline(positioning ? "Get ready to mine" : "Wait by the " + (remains == Target.LARGE_REMAINS ? "large remains" : "remains"))
			.detail(when).target(remains).urgency(Urgency.INFO).build();
	}

	// Whether a pouch is close enough to degrading that the next game would tip it.
	private static boolean pouchWillDegrade(Snapshot s, PlannerSettings c)
	{
		if (c.getPouchRepairLoads() <= 0 || s.getPouchUsesLeft() < 0)
		{
			return false;
		}
		return s.getPouchUsesLeft() <= (long) c.getPouchRepairLoads() * s.getPouchWorstCapacity();
	}

	@Nullable
	private Instruction spendCellBeforeGame(Snapshot s, PlannerSettings c, String when)
	{
		Set<Integer> cell = ImmutableSet.of(s.getChargedCell().getCellItemId());
		if (c.getStrategy() == Strategy.SOLO)
		{
			if (s.getGuardiansActive() == 0 && s.isChisel())
			{
				return Instruction.builder().step(Step.PRE_BUILD_GUARDIAN).headline("Make a weak elemental guardian")
					.detail("Use the cell on the east essence pile")
					.target(Target.ESSENCE_PILE_ELEMENTAL).items(cell).build();
			}
			WorldPoint tile = soloTile(s);
			if (tile != null)
			{
				return Instruction.builder().step(Step.PRE_BUILD_BARRIER).headline("Build the centre barrier")
					.detail("Between the fire and blood portals")
					.target(Target.CELL_TILE).location(tile).items(cell).build();
			}
			return null;
		}
		if (!s.getEmptyTiles().isEmpty())
		{
			return Instruction.builder().step(Step.PRE_BUILD_BARRIER).headline("Build a barrier")
				.detail(when)
				.target(Target.CELL_TILE).location(emptyTile(s)).items(cell).build();
		}
		if (s.getGuardiansActive() < Math.max(c.getDesiredGuardians(), s.getGuardiansMax()) && s.isChisel()
			&& s.getGuardiansActive() < s.getGuardiansMax())
		{
			Alignment alignment = shortAlignment(s, c);
			return Instruction.builder().step(Step.PRE_BUILD_GUARDIAN)
				.headline("Make a weak " + alignment.getLabel().toLowerCase() + " guardian")
				.detail("Use the cell on the essence pile")
				.target(pileFor(alignment)).items(cell).build();
		}
		return null;
	}

	private Instruction ended(Snapshot s, PlannerSettings c)
	{
		if (s.hasStones() && s.getLocation() == Location.TEMPLE)
		{
			return Instruction.builder().step(Step.POWER_UP).headline("Hand in your stones now")
				.detail("The rift is closing").target(Target.GREAT_GUARDIAN).items(STONE_ITEMS).urgency(Urgency.HIGH).build();
		}
		return preGame(s, c);
	}

	private Instruction active(Snapshot s, PlannerSettings c)
	{
		if (s.getLocation() == Location.ALTAR_ROOM)
		{
			return inAltar(s, c);
		}
		if (s.getLocation() == Location.HUGE_REMAINS)
		{
			return inHugeRemains(s);
		}

		if (!s.getBrokenTiles().isEmpty())
		{
			WorldPoint tile = nearest(s.getBrokenTiles(), s.getPlayerLocation());
			if (s.getFragments() >= TILE_REPAIR_FRAGMENTS)
			{
				return Instruction.builder().step(Step.REPAIR_TILE).headline("Repair the broken tile")
					.detail("").target(Target.CELL_TILE).location(tile)
					.items(FRAGMENT_ITEM).urgency(Urgency.HIGH).build();
			}
			if (!s.hasStones())
			{
				return Instruction.builder().step(Step.MINE_FRAGMENTS_FOR_REPAIR)
					.headline("Mine " + (TILE_REPAIR_FRAGMENTS - s.getFragments()) + " fragments to repair the tile")
					.detail("").target(miningTarget(s)).urgency(Urgency.HIGH).build();
			}
		}

		// Run grace-portal preparation before the portal instruction while there is time.
		if (s.isPortalOpen())
		{
			int close = s.getSecondsToClose();
			int left = s.getPortalSecondsRemaining();
			boolean grace = s.energyCapped() || (close >= 0 && close <= (left >= 0 ? left : 30) + GRACE_MARGIN_SECONDS);
			if (grace)
			{
				Instruction prep = graceResupply(s);
				if (prep != null)
				{
					return prep;
				}
				return Instruction.builder().step(Step.ENTER_PORTAL).headline("Enter the portal and wait inside" + direction(s))
					.detail("Wait inside for the next game")
					.target(Target.PORTAL).urgency(Urgency.HIGH).build();
			}
			if (s.capacity() >= c.getPortalMinCapacity() && !s.energyCapped())
			{
				String leftText = left >= 0 ? left + "s left" : "";
				return Instruction.builder().step(Step.ENTER_PORTAL).headline("Enter the portal" + direction(s))
					.detail(leftText).target(Target.PORTAL).urgency(Urgency.HIGH).build();
			}
		}

		// Apply hold-stones as a modifier so it does not block subsequent instructions.
		boolean holding = s.hasStones() && shouldHoldStones(s, c);
		if (s.hasStones() && !holding)
		{
			return Instruction.builder().step(Step.POWER_UP).headline("Power up the Great Guardian")
				.detail("").target(Target.GREAT_GUARDIAN).items(STONE_ITEMS).build();
		}
		Instruction next = afterStones(s, c);
		if (!holding)
		{
			return next;
		}
		int wait = c.getGracePortalSeconds() - s.getSecondsSinceLastPortal();
		if (next.getStep() == Step.IDLE_DEFEND)
		{
			return Instruction.builder().step(Step.HOLD_STONES).headline("Hold your stones " + wait + "s")
				.detail(next.getHeadline())
				.items(STONE_ITEMS).urgency(Urgency.INFO).build();
		}
		String note = "keep your stones " + wait + "s for the grace portal";
		return next.toBuilder().detail(next.getDetail().isEmpty() ? note : next.getDetail() + " - " + note).build();
	}

	@Nullable
	private Instruction graceResupply(Snapshot s)
	{
		return graceResupply(s, s.getPortalSecondsRemaining() >= 0 ? s.getPortalSecondsRemaining() : 30);
	}

	/** Next-round resupply that fits in {@code left} seconds before the portal must be entered. */
	@Nullable
	private Instruction graceResupply(Snapshot s, int left)
	{
		int budget = left - walkSeconds(s, Target.PORTAL, 15) - 3;
		if (s.hasStones() && walkSeconds(s, Target.GREAT_GUARDIAN, 10) + 2 <= budget)
		{
			return Instruction.builder().step(Step.POWER_UP).headline("Hand in your stones, then enter the portal")
				.detail("").target(Target.GREAT_GUARDIAN).items(STONE_ITEMS).urgency(Urgency.HIGH).build();
		}
		if (s.getDepositableRunes() > 0 && walkSeconds(s, Target.DEPOSIT_POOL, 10) + 2 <= budget)
		{
			return Instruction.builder().step(Step.DEPOSIT_RUNES).headline("Deposit your runes, then enter the portal")
				.detail("").target(Target.DEPOSIT_POOL).urgency(Urgency.HIGH).build();
		}
		if (s.getUnchargedCells() < 10 && walkSeconds(s, Target.UNCHARGED_CELL_TABLE, 10) + 2 <= budget)
		{
			return Instruction.builder().step(Step.PRE_TAKE_CELLS).headline("Collect uncharged cells, then enter the portal")
				.detail("").target(Target.UNCHARGED_CELL_TABLE).urgency(Urgency.HIGH).build();
		}
		if (s.anyPouchDegraded() && walkSeconds(s, Target.APPRENTICE_CORDELIA, 15) + 4 <= budget)
		{
			return Instruction.builder().step(Step.PRE_REPAIR_POUCHES).headline("Repair your pouches, then enter the portal")
				.detail("").target(Target.APPRENTICE_CORDELIA).items(degradedPouches(s)).urgency(Urgency.HIGH).build();
		}
		return null;
	}

	private Instruction afterStones(Snapshot s, PlannerSettings c)
	{
		// A cell kept back at the start is spent after the opening run, not on the way out.
		if (s.getChargedCell() != null && (!openingRunUnderway(s, c) || anyBarrierCritical(s)))
		{
			Instruction cell = cellPlan(s, c);
			if (cell != null)
			{
				return cell;
			}
		}

		if (s.energyCapped())
		{
			return Instruction.builder().step(Step.IDLE_DEFEND).headline("Energy maxed")
				.detail("Keep barriers and guardians up").urgency(Urgency.INFO).build();
		}

		if (c.getStrategy() == Strategy.MASS)
		{
			return massPlan(s, c);
		}

		int essence = s.essenceTotal();
		boolean full = s.capacity() == 0;
		boolean nothingToCraft = s.getFragments() == 0;
		if (essence >= MIN_TRIP_ESSENCE && !craftingUnderway(s)
			&& ((nothingToCraft && s.capacity() < MIN_MINING_LEG) || !topUpWorthIt(s, essence)))
		{
			full = true;
		}
		if (essence > 0 && (full || (nothingToCraft && essence >= MIN_TRIP_ESSENCE)))
		{
			Instruction trip = altarTrip(s, c, essence);
			if (trip.getStep() != Step.WAIT_FOR_ALTAR)
			{
				return trip;
			}
			// Continue mining/crafting while all altars are unavailable.
			if (s.getFragments() > 0 && s.capacity() == 0)
			{
				return mineWhileWaitingForAltar(s, c);
			}
			if (s.getFragments() == 0 && s.capacity() < MIN_MINING_LEG)
			{
				// Preserve the specific weak-altar wait instruction.
				if (AltarChooser.choose(s, c) != null)
				{
					return trip;
				}
				return s.getFreeSlots() > 0 ? mineWhileWaitingForAltar(s, c) : waitForAltar(s);
			}
		}

		if (s.getFragments() > 0 && s.capacity() > 0 && (essence == 0 || topUpWorthIt(s, essence) || craftingUnderway(s)))
		{
			if (craftingUnderway(s))
			{
				return craftEssence(s);
			}
			Instruction mine = openingMine(s, c);
			if (mine == null)
			{
				mine = midGameMine(s, c);
			}
			if (mine != null)
			{
				return mine;
			}
			return craftEssence(s);
		}
		if (s.getFragments() > 0 && s.capacity() == 0)
		{
			return Instruction.builder().step(Step.FREE_SPACE).headline("Make room for essence")
				.detail("Deposit runes or use your cell").target(Target.DEPOSIT_POOL).build();
		}

		Instruction portalWait = portalWait(s, c);
		if (portalWait != null)
		{
			return portalWait;
		}
		return mineFragments(s, c, s.isAnyPortalThisGame() ? s.capacity() : c.getOpeningFragmentTarget());
	}

	private Instruction massPlan(Snapshot s, PlannerSettings c)
	{
		int essence = s.essenceTotal();
		int room = s.capacity();
		int fragments = s.getFragments();
		int close = s.getSecondsToClose();

		if (s.getUnchargedCells() == 0 && s.getChargedCell() == null && essence == 0
			&& walkSeconds(s, Target.UNCHARGED_CELL_TABLE, 30) <= 12)
		{
			return Instruction.builder().step(Step.PRE_TAKE_CELLS).headline("Collect uncharged cells")
				.detail("")
				.target(Target.UNCHARGED_CELL_TABLE).build();
		}

		AltarChoice choice = essence > 0 ? AltarChooser.choose(s, c) : null;
		// Fall through to mining/crafting when no altar can accept the load.
		if (essence > 0 && choice == null && (room == 0 || (fragments == 0 && room < MIN_MINING_LEG)))
		{
			if (fragments > 0 || s.getFreeSlots() > 0)
			{
				return mineWhileWaitingForAltar(s, c);
			}
			return waitForAltar(s);
		}
		if (essence > 0 && choice != null)
		{
			int trip = (int) Math.round(AltarChooser.tripSeconds(s, choice.getAltar()));
			boolean canTopUp = room > 0 && fragments > 0 && (topUpWorthIt(s, essence) || craftingUnderway(s));
			if (canTopUp)
			{
				int craftable = Math.min(fragments, room);
				int craftSeconds = (int) Math.round(craftable / s.getCraftPerTick() * 0.6) + walkSeconds(s, Target.WORKBENCH, 10);
				boolean missesPortal = s.getSecondsToNextPortal() >= 0 && s.getSecondsToNextPortal() < craftSeconds + trip
					&& room + craftable >= c.getPortalMinCapacity();
				boolean missesClose = close >= 0 && close < craftSeconds + trip + 5;
				// An in-progress top-up is interrupted only by the portal or close.
				if (!missesPortal && !missesClose)
				{
					return craftEssence(s);
				}
				int minWorth = craftingUnderway(s) ? 1 : 3;
				if (missesPortal && missesClose)
				{
					Instruction last = lastPortalOrTrip(s, c, essence, craftable, trip, close);
					if (last != null)
					{
						return last;
					}
				}
				if (!missesPortal && missesClose)
				{
					// The whole load does not fit before the close, but part of it may: craft
					// what the time allows rather than leaving the bench with a few essence.
					int affordable = (int) Math.floor(Math.max(0, close - trip - walkSeconds(s, Target.WORKBENCH, 10) - 3) / 0.6 * s.getCraftPerTick());
					if (affordable >= minWorth)
					{
						return craftSome(s, Math.min(affordable, craftable), "then go", "Rift closes in ~" + close + "s");
					}
					if (essence < MIN_TOPUP)
					{
						// A trip with a handful of essence is worth less than preparing for the next round.
						return stayForClose(s, c, close);
					}
				}
				if (missesPortal && !missesClose && essence < MIN_TRIP_ESSENCE)
				{
					// A full load is worth missing the portal for, but a few essence are not:
					// craft for the portal instead and stay near the centre once that is done.
					int eta = s.getSecondsToNextPortal();
					int affordable = (int) Math.floor(Math.max(0, eta - walkSeconds(s, Target.WORKBENCH, 10) - 3) / 0.6 * s.getCraftPerTick());
					if (affordable >= minWorth)
					{
						return craftSome(s, Math.min(affordable, craftable), "then take the portal", "Portal in ~" + eta + "s");
					}
					return portalPrep(s, c, eta);
				}
			}
			if (close >= 0 && close < trip)
			{
				return stayForClose(s, c, close);
			}
			boolean weak = choice.getAltar().getCellTier() == CellTier.WEAK && choice.getCombination() == null;
			int rot = s.getAltarSecondsRemaining();
			if (weak && !choice.isViaTalisman() && rot >= 0 && rot <= c.getWeakAltarWaitSeconds())
			{
				return weakAltarWait(s, choice, rot);
			}
			return goToAltar(s, choice, choice.getReason());
		}

		int portalEta = s.getSecondsToNextPortal();
		boolean portalSoon = portalEta >= 0 && room >= c.getPortalMinCapacity()
			&& portalEta <= walkSeconds(s, Target.PORTAL, 15) + c.getPortalWarningSeconds()
			&& (close < 0 || close > portalEta + 30);
		if (portalSoon)
		{
			// The last portal of the round: resupply for the next round before it opens.
			int trip = (int) Math.round(AltarChooser.tripSeconds(s, Altar.AIR));
			if (close >= 0 && close < portalEta + PORTAL_VISIT_SECONDS + trip)
			{
				Instruction prep = graceResupply(s, portalEta + 30);
				if (prep != null)
				{
					return prep;
				}
			}
			// Exclude the changing walk distance to avoid alternating instructions while moving.
			int craftSeconds = fragments > 0 ? (int) Math.round(Math.min(fragments, room) / s.getCraftPerTick() * 0.6) : Integer.MAX_VALUE;
			if (fragments > 0 && s.getDepositableRunes() > 0 && s.getFreeSlots() < Math.min(fragments, room))
			{
				craftSeconds += walkSeconds(s, Target.DEPOSIT_POOL, 10) + 3;
			}
			if (fragments > 0 && room > 0 && craftSeconds <= portalEta)
			{
				return craftEssence(s);
			}
			return portalPrep(s, c, portalEta);
		}

		if (fragments > 0 && room > 0 && (essence == 0 || topUpWorthIt(s, essence) || craftingUnderway(s)))
		{
			if (craftingUnderway(s))
			{
				return craftEssence(s);
			}
			int craftable = Math.min(fragments, room);
			int craftSeconds = (int) Math.round(craftable / s.getCraftPerTick() * 0.6) + walkSeconds(s, Target.WORKBENCH, 10);
			int trip = (int) Math.round(AltarChooser.tripSeconds(s, s.getActiveCatalytic() != null ? s.getActiveCatalytic() : Altar.AIR));
			int eta = s.getSecondsToNextPortal();
			boolean portalBlocks = eta >= 0 && eta < craftSeconds + trip && room + craftable >= c.getPortalMinCapacity();
			if (portalBlocks && close >= 0 && close < craftSeconds + trip + 5)
			{
				Instruction last = lastPortalOrTrip(s, c, essence, craftable, trip, close);
				if (last != null)
				{
					return last;
				}
			}
			if (close >= 0 && close < craftSeconds + trip)
			{
				int affordable = (int) Math.floor(Math.max(0, close - trip - walkSeconds(s, Target.WORKBENCH, 10) - 3) / 0.6 * s.getCraftPerTick());
				if (affordable < 3 || (preppingForClose(s) && affordable < MIN_MINING_LEG))
				{
					return stayForClose(s, c, close);
				}
				return craftSome(s, Math.min(affordable, craftable), "then go", "Rift closes in ~" + close + "s");
			}
			int rot = s.getAltarSecondsRemaining();
			// Craft for the next altars once they are within the craft window, but no earlier
			// than the load needs: with the pouches already full, a short craft can wait.
			boolean windowOpen = rot >= 0 && rot <= c.getCraftWindowSeconds() && rot <= craftSeconds + 8;
			if (!s.isAnyPortalThisGame() && (fragments >= c.getOpeningFragmentTarget()
				|| (windowOpen && fragments >= c.getMinFragmentsToCraft())))
			{
				return craftEssence(s);
			}
			if (s.isAnyPortalThisGame())
			{
				int remaining = room - fragments;
				int mineSeconds = (int) Math.round(remaining / s.getFragmentsPerTick() * 0.6);
				boolean portalTooSoon = s.getSecondsToNextPortal() >= 0
					&& mineSeconds + craftSeconds + trip > s.getSecondsToNextPortal();
				if (fragments >= room || (fragments >= c.getMinFragmentsToCraft() && portalTooSoon))
				{
					return craftEssence(s);
				}
			}
		}

		// The opening run is the configured size; later runs mine what the rest of the game
		// can use, so the remains are visited as few times as possible.
		boolean opening = !s.isAnyPortalThisGame() && s.getActiveElemental() == null && s.getActiveCatalytic() == null;
		int target = opening ? c.getOpeningFragmentTarget() : Math.max(room, c.getMinFragmentsToCraft());
		if (close >= 0)
		{
			int affordable = fragmentsUntilClose(s, c, target - fragments, close);
			// Use a higher threshold to start mining than to continue an existing run.
			int floor = fragments == 0 ? MIN_MINING_LEG : 5;
			// Use the same hysteresis when leaving next-round preparation.
			if (fragments + affordable < floor || (preppingForClose(s) && fragments + affordable < 2 * MIN_MINING_LEG))
			{
				return stayForClose(s, c, close);
			}
			if (fragments >= 5 && affordable < MIN_MINING_LEG && room > 0)
			{
				return craftEssence(s);
			}
			target = opening ? Math.min(target, Math.max(5, fragments + affordable)) : Math.max(5, fragments + affordable);
		}
		if (fragments > 0 && room > 0 && (fragments >= target || !miningLegWorthIt(s, target - fragments)))
		{
			return craftEssence(s);
		}
		return mineFragments(s, c, target);
	}

	private static boolean preppingForClose(Snapshot s)
	{
		Step last = s.getLastStep();
		return last == Step.DEPOSIT_RUNES || last == Step.PRE_TAKE_CELLS || last == Step.PRE_REPAIR_POUCHES
			|| last == Step.PRE_POSITION;
	}

	/**
	 * How many more fragments can still be mined, crafted and taken to an altar before the
	 * close. The first inventory load costs one altar trip; each further load costs another,
	 * and every portal expected before the close takes its visit and its own trip out of
	 * the budget. Only the walk out and back is measured from the remains, so the answer
	 * does not shrink with every step taken towards them.
	 */
	private static int fragmentsUntilClose(Snapshot s, PlannerSettings c, int needed, int close)
	{
		Target remains = miningTarget(s, needed);
		double rate = remains == Target.LARGE_REMAINS ? s.getFragmentsPerTick() : s.getPartsPerTick();
		double perFragment = 0.6 / rate + 0.6 / s.getCraftPerTick();
		int trip = (int) Math.round(AltarChooser.tripSeconds(s, Altar.AIR));
		int overhead = walkSeconds(s, remains, 15) + returnSeconds(s, remains, 15) + trip + 5;
		int eta = s.getSecondsToNextPortal();
		if (eta >= 0)
		{
			for (int at = eta; at + PORTAL_VISIT_SECONDS < close; at += PORTAL_INTERVAL_SECONDS)
			{
				overhead += PORTAL_VISIT_SECONDS + trip;
			}
		}
		double available = Math.max(0, close - overhead);
		int load = Math.max(MIN_TOPUP, s.capacity());
		int first = (int) Math.floor(available / perFragment);
		if (first <= load)
		{
			return first;
		}
		return load + (int) Math.floor((available - load * perFragment) / (perFragment + (double) trip / load));
	}

	/**
	 * Neither the next portal nor the close leaves time for the whole load and its trip. A trip
	 * with what can still be crafted and taken to an altar before the portal is worth it if
	 * that comes to more than a handful; otherwise this portal is the last of the round, so
	 * the time goes on next-round preparation and the portal is taken. Null means the essence
	 * already in hand justifies the trip on its own.
	 */
	@Nullable
	private Instruction lastPortalOrTrip(Snapshot s, PlannerSettings c, int essence, int craftable, int trip, int close)
	{
		int eta = s.getSecondsToNextPortal();
		int fit = (int) Math.floor(Math.max(0, eta - trip - walkSeconds(s, Target.WORKBENCH, 10) - 3) / 0.6 * s.getCraftPerTick());
		int crafted = Math.min(fit, craftable);
		if (essence + crafted > MIN_PRE_PORTAL_TRIP)
		{
			return crafted > 0 ? craftSome(s, crafted, "then go", "Portal in ~" + eta + "s") : null;
		}
		Instruction prep = graceResupply(s, eta + 30);
		return prep != null ? prep : portalPrep(s, c, eta);
	}

	private Instruction stayForClose(Snapshot s, PlannerSettings c, int close)
	{
		Instruction prep = preGame(s, c);
		return prep.toBuilder().detail("Rift closes in ~" + close + "s").build();
	}

	private static Instruction depositRunes(Snapshot s)
	{
		return Instruction.builder().step(Step.DEPOSIT_RUNES).headline("Deposit your runes")
			.detail(keepBaseRune(s))
			.target(Target.DEPOSIT_POOL).build();
	}

	private static String keepBaseRune(Snapshot s)
	{
		return s.getBaseRuneCount() > 0 ? "Keep your " + s.getBaseRune().getLabel().toLowerCase() + " runes" : "";
	}

	private static Set<Integer> degradedPouches(Snapshot s)
	{
		Set<Integer> items = new HashSet<>();
		for (PouchState pouch : s.getPouches())
		{
			if (pouch.isDegraded())
			{
				items.add(pouch.getItemId());
			}
		}
		return items;
	}

	private static Set<Integer> pouchesWithSpace(Snapshot s)
	{
		Set<Integer> items = new HashSet<>();
		for (PouchState pouch : s.getPouches())
		{
			if (pouch.space() > 0)
			{
				items.add(pouch.getItemId());
			}
		}
		return items;
	}

	// Use fallback distances when pathfinding is unavailable.
	private static int walkSeconds(Snapshot s, Target target, int defaultSeconds)
	{
		Integer ticks = s.getTravelTicks().get(target);
		return ticks == null ? defaultSeconds : (int) Math.round(ticks / 2.0 * 0.6);
	}

	// Seconds from the target back to the workbench.
	private static int returnSeconds(Snapshot s, Target target, int defaultSeconds)
	{
		Integer ticks = s.getReturnTicks().get(target);
		return ticks == null ? walkSeconds(s, target, defaultSeconds) : (int) Math.round(ticks / 2.0 * 0.6);
	}

	private Instruction portalPrep(Snapshot s, PlannerSettings c, int portalEta)
	{
		if (portalEta <= c.getPortalWarningSeconds())
		{
			return Instruction.builder().step(Step.WAIT_FOR_PORTAL).headline("Portal opens in ~" + portalEta + "s")
				.detail("Stay by the Great Guardian").urgency(Urgency.INFO).build();
		}
		return Instruction.builder().step(Step.MINE_FRAGMENTS).headline("Mine the guardian parts by the entrance")
			.detail("Portal in ~" + portalEta + "s")
			.target(Target.GUARDIAN_REMAINS_ENTRANCE).build();
	}

	// Craft part of the load, filling pouches first when the inventory is full.
	private Instruction craftSome(Snapshot s, int count, String then, String detail)
	{
		Instruction craft = craftEssence(s);
		if (craft.getStep() != Step.CRAFT_ESSENCE)
		{
			return craft;
		}
		return craft.toBuilder().headline("Craft " + count + " essence, " + then).detail(detail).build();
	}

	private Instruction inAltar(Snapshot s, PlannerSettings c)
	{
		Altar altar = s.getAltarRoom();
		if (s.essenceTotal() <= 0 || altar == null)
		{
			return Instruction.builder().step(Step.LEAVE_ALTAR).headline("Leave through the portal")
				.detail("")
				.target(Target.ALTAR_EXIT).build();
		}
		Set<Integer> items = new HashSet<>();
		StringBuilder detail = new StringBuilder();
		String headline;
		CombinationRune combo = AltarChooser.combinationAt(altar, s, c);
		if (combo != null)
		{
			headline = "Craft " + combo.getLabel().toLowerCase() + " runes";
			items.add(s.getBaseRune().getRuneItemId());
			if (!s.isMagicImbueActive() && s.isLunarSpellbook())
			{
				detail.append("Cast Magic Imbue, then use ");
			}
			else
			{
				detail.append("Use ");
			}
			detail.append(s.getBaseRune().getLabel().toLowerCase()).append(" runes on the altar");
		}
		else
		{
			headline = "Craft at the " + altar.getLabel().toLowerCase() + " altar";
			items.add(ItemID.GOTR_GUARDIAN_ESSENCE);
		}
		if (s.getEssence() == 0 && s.pouchStored() > 0)
		{
			headline = "Empty your pouches, then craft again";
			items.clear();
			for (PouchState pouch : s.getPouches())
			{
				if (pouch.getStored() > 0)
				{
					items.add(pouch.getItemId());
				}
			}
		}
		else if (s.pouchStored() > 0)
		{
			detail.append(s.pouchStored()).append(" more in pouches");
		}
		return Instruction.builder().step(Step.CRAFT_RUNES).headline(headline).detail(detail.toString())
			.target(Target.RUNIC_ALTAR).altar(altar).items(items).build();
	}

	private Instruction inHugeRemains(Snapshot s)
	{
		// Reserve one inventory slot during the opening altar wait.
		boolean noAltarYet = s.getActiveElemental() == null && s.getActiveCatalytic() == null;
		if (noAltarYet && s.getFreeSlots() <= 1 && s.pouchSpace() == 0 && s.getFragments() == 0)
		{
			return Instruction.builder().step(Step.LEAVE_HUGE_REMAINS).headline("Leave through the portal")
				.detail("").target(Target.PORTAL).build();
		}
		if (s.capacity() > 0 && !s.energyCapped())
		{
			if (s.getFreeSlots() == 0 && s.pouchSpace() > 0)
			{
				return Instruction.builder().step(Step.FILL_POUCHES).headline("Fill your pouches")
					.detail("").items(pouchesWithSpace(s)).build();
			}
			return Instruction.builder().step(Step.MINE_HUGE_REMAINS).headline("Mine the huge remains")
				.detail("").target(Target.HUGE_REMAINS).build();
		}
		return Instruction.builder().step(Step.LEAVE_HUGE_REMAINS).headline("Leave through the portal")
			.detail("").target(Target.PORTAL).build();
	}

	private Instruction altarTrip(Snapshot s, PlannerSettings c, int essence)
	{
		AltarChoice choice = AltarChooser.choose(s, c);
		if (choice == null)
		{
			String why = s.getActiveElemental() == null && s.getActiveCatalytic() == null
				? "No altar is open yet" : "You cannot use either open altar";
			return Instruction.builder().step(Step.WAIT_FOR_ALTAR).headline(why)
				.detail("Wait in the centre")
				.target(Target.CENTRE_WAIT).urgency(Urgency.INFO).build();
		}
		boolean weak = choice.getAltar().getCellTier() == CellTier.WEAK && choice.getCombination() == null;
		int rot = s.getAltarSecondsRemaining();
		if (weak && !choice.isViaTalisman() && rot >= 0 && rot <= c.getWeakAltarWaitSeconds())
		{
			return weakAltarWait(s, choice, rot);
		}
		return goToAltar(s, choice, choice.getReason());
	}

	private static Instruction goToAltar(Snapshot s, AltarChoice choice, String detail)
	{
		return Instruction.builder().step(Step.GO_TO_ALTAR)
			.headline("Enter the " + choice.getAltar().getLabel().toLowerCase() + " portal guardian")
			.detail(detail)
			.target(Target.ALTAR_PORTAL).altar(choice.getAltar())
			.items(choice.isViaTalisman() ? ImmutableSet.of(choice.getAltar().getTalismanItemId()) : ImmutableSet.of()).build();
	}

	@Nullable
	private Instruction openingMine(Snapshot s, PlannerSettings c)
	{
		if (s.isAnyPortalThisGame() || s.getEssence() > 0)
		{
			return null;
		}
		int target = c.getOpeningFragmentTarget();
		if (s.getFragments() >= target)
		{
			return null;
		}
		int rot = s.getAltarSecondsRemaining();
		// Craft no earlier than the load needs: with the pouches already full, a short craft can wait.
		int craftSeconds = (int) Math.round(Math.min(s.getFragments(), s.capacity()) / s.getCraftPerTick() * 0.6)
			+ walkSeconds(s, Target.WORKBENCH, 10);
		boolean windowOpen = rot >= 0 && rot <= c.getCraftWindowSeconds() && rot <= craftSeconds + 8
			&& s.getFragments() >= c.getMinFragmentsToCraft();
		if (windowOpen)
		{
			return null;
		}
		return mineFragments(s, c, target);
	}

	@Nullable
	private Instruction midGameMine(Snapshot s, PlannerSettings c)
	{
		if (!s.isAnyPortalThisGame())
		{
			return null;
		}
		int room = s.capacity();
		int fragments = s.getFragments();
		int remaining = room - fragments;
		if (remaining <= 0 || !miningLegWorthIt(s, remaining))
		{
			return null;
		}
		int mineSeconds = (int) Math.round(remaining / s.getFragmentsPerTick() * 0.6);
		int craftSeconds = (int) Math.round(Math.min(fragments, room) / s.getCraftPerTick() * 0.6);
		int trip = (int) Math.round(AltarChooser.tripSeconds(s, s.getActiveCatalytic() != null ? s.getActiveCatalytic() : Altar.AIR));
		boolean portalTooSoon = s.getSecondsToNextPortal() >= 0
			&& mineSeconds + craftSeconds + trip > s.getSecondsToNextPortal();
		if (portalTooSoon && fragments >= c.getMinFragmentsToCraft())
		{
			return null;
		}
		return mineFragments(s, c, room);
	}

	// Keep crafting through close-time jitter; higher-priority interrupts still apply.
	private static boolean craftingUnderway(Snapshot s)
	{
		return s.getLastStep() == Step.CRAFT_ESSENCE || s.getLastStep() == Step.FILL_POUCHES;
	}

	private static boolean topUpWorthIt(Snapshot s, int essence)
	{
		int craftable = Math.min(s.getFragments(), s.capacity());
		return craftable > 0 && (craftable >= MIN_TOPUP || essence < MIN_TRIP_ESSENCE);
	}

	private static boolean miningLegWorthIt(Snapshot s, int remaining)
	{
		if (remaining >= MIN_MINING_LEG)
		{
			return true;
		}
		Target remains = miningTarget(s, remaining);
		Integer ticks = s.getTravelTicks().get(remains);
		return ticks != null && ticks <= 2;
	}

	private static Instruction weakAltarWait(Snapshot s, AltarChoice choice, int rot)
	{
		StringBuilder why = new StringBuilder(choice.getAltar().getLabel()).append(" is weak");
		for (Altar open : new Altar[]{s.getActiveElemental(), s.getActiveCatalytic()})
		{
			if (open == null || open == choice.getAltar())
			{
				continue;
			}
			String reason = AltarChooser.unusableReason(open, s);
			why.append(" - ").append(open.getLabel()).append(reason != null ? ": " + reason : " is weak");
		}
		return Instruction.builder().step(Step.WAIT_FOR_ALTAR).headline("Wait " + rot + "s for the next altars")
			.detail(why.toString())
			.target(Target.CENTRE_WAIT).urgency(Urgency.INFO).build();
	}

	private Instruction waitForAltar(Snapshot s)
	{
		int rot = s.getAltarSecondsRemaining();
		String when = rot >= 0 ? "Altars change in " + rot + "s" : "";
		if (s.getUnchargedCells() < 10 && (rot < 0 || rot > walkSeconds(s, Target.UNCHARGED_CELL_TABLE, 10) + 5))
		{
			return Instruction.builder().step(Step.PRE_TAKE_CELLS).headline("Collect uncharged cells")
				.detail(when).target(Target.UNCHARGED_CELL_TABLE).build();
		}
		if (s.getFreeSlots() == 0 && s.getFragments() == 0 && s.getEssence() > 0 && (rot < 0 || rot > DROP_ESSENCE_WAIT_SECONDS))
		{
			return Instruction.builder().step(Step.DROP_ESSENCE).headline("Drop one essence")
				.detail(when).items(ESSENCE_ITEM).build();
		}
		boolean none = s.getActiveElemental() == null && s.getActiveCatalytic() == null;
		return Instruction.builder().step(Step.WAIT_FOR_ALTAR).headline(none ? "No altar is open yet" : "You cannot use either open altar")
			.detail("Wait in the centre")
			.target(Target.CENTRE_WAIT).urgency(Urgency.INFO).build();
	}

	private Instruction mineWhileWaitingForAltar(Snapshot s, PlannerSettings c)
	{
		int rot = s.getAltarSecondsRemaining();
		if (rot >= 0 && rot <= c.getPortalWarningSeconds())
		{
			return waitInCentre(rot);
		}
		int target = s.isAnyPortalThisGame() ? s.getFragments() + c.getMinFragmentsToCraft()
			: Math.max(s.getFragments() + c.getMinFragmentsToCraft(), c.getOpeningFragmentTarget());
		Target remains = miningTarget(s, target - s.getFragments());
		if (remains == Target.LARGE_REMAINS && rot >= 0 && rot <= 2 * walkSeconds(s, Target.LARGE_REMAINS, 15) + 10)
		{
			remains = Target.GUARDIAN_REMAINS;
		}
		// Walking over to the parts for a few seconds of mining is not worth it: head for the
		// centre instead. Already there, mining continues until the wait proper.
		int toParts = walkSeconds(s, Target.GUARDIAN_REMAINS, 10);
		if (remains == Target.GUARDIAN_REMAINS && rot >= 0 && toParts > 1
			&& rot <= toParts + MIN_PARTS_STINT_SECONDS + c.getPortalWarningSeconds())
		{
			return waitInCentre(rot);
		}
		return Instruction.builder().step(Step.MINE_FRAGMENTS).headline("Mine while you wait for an altar")
			.detail(s.getFragments() + " / " + target + " fragments").target(remains).build();
	}

	private static Instruction waitInCentre(int rot)
	{
		return Instruction.builder().step(Step.WAIT_FOR_ALTAR).headline("Altars open in " + rot + "s")
			.detail("Wait in the centre")
			.target(Target.CENTRE_WAIT).urgency(Urgency.INFO).build();
	}

	private Instruction mineFragments(Snapshot s, PlannerSettings c, int target)
	{
		StringBuilder detail = new StringBuilder(s.getFragments() + " / " + target + " fragments");
		if (!s.isPickaxe())
		{
			detail.append(" - no pickaxe!");
		}
		Target remains = miningTarget(s, target - s.getFragments());
		return Instruction.builder().step(Step.MINE_FRAGMENTS).headline(miningHeadline(remains)).detail(detail.toString())
			.target(remains).build();
	}

	private Instruction craftEssence(Snapshot s)
	{
		if (s.getFreeSlots() == 0 && s.pouchSpace() > 0)
		{
			return Instruction.builder().step(Step.FILL_POUCHES).headline("Fill your pouches")
				.detail("").target(Target.WORKBENCH).items(pouchesWithSpace(s)).build();
		}
		int craftable = Math.min(s.getFragments(), s.capacity());
		if (s.getDepositableRunes() > 0 && s.getFreeSlots() < craftable)
		{
			return depositRunes(s);
		}
		return Instruction.builder().step(Step.CRAFT_ESSENCE).headline("Craft essence at the workbench")
			.detail("")
			.target(Target.WORKBENCH).items(FRAGMENT_ITEM).build();
	}

	@Nullable
	private Instruction portalWait(Snapshot s, PlannerSettings c)
	{
		int eta = s.getSecondsToNextPortal();
		if (eta < 0 || eta > c.getPortalWarningSeconds() || s.capacity() < c.getPortalMinCapacity())
		{
			return null;
		}
		return Instruction.builder().step(Step.WAIT_FOR_PORTAL).headline("Portal opens in ~" + eta + "s")
			.detail("").urgency(Urgency.INFO).build();
	}

	private boolean shouldHoldStones(Snapshot s, PlannerSettings c)
	{
		if (!s.isAnyPortalThisGame() || s.getMaxPower() <= 0 || s.getSecondsSinceLastPortal() < 0)
		{
			return false;
		}
		int remaining = s.getMaxPower() - s.getPower();
		if (s.stonesTotal() < remaining)
		{
			return false;
		}
		if (s.getSecondsSinceLastPortal() >= c.getGracePortalSeconds())
		{
			return false;
		}
		return !anyBarrierCritical(s);
	}

	private static boolean anyBarrierCritical(Snapshot s)
	{
		for (BarrierState barrier : s.getBarriers())
		{
			if (barrier.getHealthPercent() >= 0 && barrier.getHealthPercent() < 25)
			{
				return true;
			}
		}
		return false;
	}

	// True while the plan was already sending the player out to mine the opening load.
	private static boolean openingRunUnderway(Snapshot s, PlannerSettings c)
	{
		Step last = s.getLastStep();
		return (last == Step.PRE_POSITION || last == Step.MINE_FRAGMENTS)
			&& !s.isAnyPortalThisGame() && s.getEssence() == 0 && s.getFragments() < c.getOpeningFragmentTarget();
	}

	// Target the cell tile; the barrier NPC obscures its own highlight.
	@Nullable
	private Instruction cellPlan(Snapshot s, PlannerSettings c)
	{
		CellTier tier = s.getChargedCell();
		Set<Integer> cell = ImmutableSet.of(tier.getCellItemId());
		boolean slotFree = s.getGuardiansActive() < s.getGuardiansMax();
		boolean needGuardian = s.getGuardiansActive() < c.getDesiredGuardians();
		boolean strongEnough = tier.isAtLeast(CellTier.STRONG) || s.getGuardiansActive() == 0;
		if (slotFree && needGuardian && strongEnough && s.isChisel())
		{
			Alignment alignment = shortAlignment(s, c);
			return Instruction.builder().step(Step.CELL_GUARDIAN)
				.headline("Make a " + tier.getLabel().toLowerCase() + " " + alignment.getLabel().toLowerCase() + " guardian")
				.detail("Use the cell on the essence pile")
				.target(pileFor(alignment)).items(cell).build();
		}
		if (s.getBarriers().isEmpty())
		{
			WorldPoint tile = c.getStrategy() == Strategy.SOLO ? soloTile(s) : emptyTile(s);
			if (tile != null)
			{
				return Instruction.builder().step(Step.CELL_BARRIER_BUILD).headline("Build a barrier")
					.detail("").target(Target.CELL_TILE).location(tile).items(cell).build();
			}
		}
		BarrierState target = barrierToTend(s, c, tier);
		if (target != null)
		{
			if (target.getTier().getRank() < tier.getRank())
			{
				return Instruction.builder().step(Step.CELL_BARRIER_UPGRADE)
					.headline("Strengthen the " + target.getTier().getLabel().toLowerCase() + " barrier")
					.detail(barrierDetail(c, target))
					.target(Target.CELL_TILE).location(target.getLocation()).items(cell).build();
			}
			return Instruction.builder().step(Step.CELL_BARRIER_RECHARGE)
				.headline("Recharge the " + target.getTier().getLabel().toLowerCase() + " barrier")
				.detail(barrierDetail(c, target))
				.target(Target.CELL_TILE).location(target.getLocation()).items(cell).build();
		}
		if (slotFree && s.isChisel() && tier.isAtLeast(CellTier.STRONG))
		{
			Alignment alignment = shortAlignment(s, c);
			return Instruction.builder().step(Step.CELL_GUARDIAN)
				.headline("Make a " + tier.getLabel().toLowerCase() + " " + alignment.getLabel().toLowerCase() + " guardian")
				.detail("Use the cell on the essence pile")
				.target(pileFor(alignment)).items(cell).build();
		}
		return null;
	}

	@Nullable
	private BarrierState barrierToTend(Snapshot s, PlannerSettings c, CellTier tier)
	{
		List<BarrierState> barriers = s.getBarriers();
		if (barriers.isEmpty())
		{
			return null;
		}
		BarrierState fresh = freshBarrierToTend(s, c, tier);
		if (c.getBarrierPriority() == BarrierPriority.CLOSEST)
		{
			// Always the nearest barrier that needs work, so the pick follows the player.
			return fresh;
		}
		// Keep the previous barrier unless a new target is below 60% health.
		BarrierState kept = barrierAt(barriers, s.getChosenTile());
		if (kept == null || kept == fresh)
		{
			return fresh;
		}
		boolean emergency = fresh != null && fresh.getHealthPercent() >= 0 && fresh.getHealthPercent() < 60
			&& !(kept.getHealthPercent() >= 0 && kept.getHealthPercent() < 60);
		return emergency ? fresh : kept;
	}

	@Nullable
	private static BarrierState barrierAt(List<BarrierState> barriers, @Nullable WorldPoint tile)
	{
		if (tile == null)
		{
			return null;
		}
		for (BarrierState barrier : barriers)
		{
			if (barrier.getLocation().equals(tile))
			{
				return barrier;
			}
		}
		return null;
	}

	// Retain the previous empty tile to avoid switching targets while walking.
	@Nullable
	private static WorldPoint emptyTile(Snapshot s)
	{
		if (s.getChosenTile() != null && s.getEmptyTiles().contains(s.getChosenTile()))
		{
			return s.getChosenTile();
		}
		return nearest(s.getEmptyTiles(), s.getPlayerLocation());
	}

	@Nullable
	private BarrierState freshBarrierToTend(Snapshot s, PlannerSettings c, CellTier tier)
	{
		List<BarrierState> barriers = s.getBarriers();
		BarrierState protectedBarrier = protectedBarrier(c, barriers);
		if (c.getBarrierPriority() == BarrierPriority.CLOSEST)
		{
			return closestBarrierToTend(s, barriers, protectedBarrier, preferredSide(s, c));
		}
		BarrierState damaged = null;
		BarrierState upgradable = null;
		BarrierState unknown = null;
		BarrierState any = null;
		for (BarrierState barrier : barriers)
		{
			if (barrier == protectedBarrier)
			{
				continue;
			}
			if (any == null)
			{
				any = barrier;
			}
			int hp = barrier.getHealthPercent();
			if (hp >= 0 && hp < 100 && (damaged == null || hp < damaged.getHealthPercent()))
			{
				damaged = barrier;
			}
			if (barrier.getTier().getRank() < tier.getRank() && (upgradable == null || barrier.getTier().getRank() < upgradable.getTier().getRank()))
			{
				upgradable = barrier;
			}
			if (hp < 0 && unknown == null)
			{
				unknown = barrier;
			}
		}
		if (damaged != null && damaged.getHealthPercent() < 60)
		{
			return damaged;
		}
		if (upgradable != null)
		{
			return upgradable;
		}
		if (damaged != null)
		{
			return damaged;
		}
		if (s.powerPercent() >= RUMBLE_PERCENT && unknown != null)
		{
			return unknown;
		}
		BarrierState spare = unknown != null ? unknown : any;
		if (spare != null && s.getPlayerLocation() != null
			&& spare.getLocation().distanceTo2D(s.getPlayerLocation()) <= SPARE_CELL_TILES)
		{
			return spare;
		}
		return null;
	}

	// Solo: never recharge the right-most barrier so the explosion does less damage.
	@Nullable
	private static BarrierState protectedBarrier(PlannerSettings c, List<BarrierState> barriers)
	{
		if (c.getStrategy() != Strategy.SOLO || !c.isProtectRightBarrier() || barriers.size() <= 1)
		{
			return null;
		}
		BarrierState rightmost = null;
		for (BarrierState barrier : barriers)
		{
			if (rightmost == null || barrier.getLocation().getX() > rightmost.getLocation().getX())
			{
				rightmost = barrier;
			}
		}
		return rightmost;
	}

	/**
	 * Closest-first targeting: a barrier that is not Overcharged, or is at 70% health or less,
	 * needs work. Among those the nearest is taken, but a barrier on the side the plan heads to
	 * next (west for a deposit, east for cells or the remains) is preferred so the route does not
	 * cross the temple. When nothing needs work, the nearest barrier is taken anyway, likewise
	 * side-biased, to spend the cell.
	 */
	@Nullable
	private static BarrierState closestBarrierToTend(Snapshot s, List<BarrierState> barriers,
		@Nullable BarrierState protectedBarrier, Side side)
	{
		WorldPoint me = s.getPlayerLocation();
		double centreX = barrierCentreX(barriers);
		BarrierState sideNeedy = null;
		BarrierState needy = null;
		BarrierState sideAny = null;
		BarrierState any = null;
		int sideNeedyGap = Integer.MAX_VALUE;
		int needyGap = Integer.MAX_VALUE;
		int sideAnyGap = Integer.MAX_VALUE;
		int anyGap = Integer.MAX_VALUE;
		for (BarrierState barrier : barriers)
		{
			if (barrier == protectedBarrier)
			{
				continue;
			}
			int gap = me == null ? 0 : barrier.getLocation().distanceTo2D(me);
			boolean onSide = onSide(barrier, side, centreX);
			if (any == null || gap < anyGap)
			{
				any = barrier;
				anyGap = gap;
			}
			if (onSide && (sideAny == null || gap < sideAnyGap))
			{
				sideAny = barrier;
				sideAnyGap = gap;
			}
			if (needsWork(barrier))
			{
				if (needy == null || gap < needyGap)
				{
					needy = barrier;
					needyGap = gap;
				}
				if (onSide && (sideNeedy == null || gap < sideNeedyGap))
				{
					sideNeedy = barrier;
					sideNeedyGap = gap;
				}
			}
		}
		if (sideNeedy != null)
		{
			return sideNeedy;
		}
		if (needy != null)
		{
			return needy;
		}
		return sideAny != null ? sideAny : any;
	}

	// The side the next instruction after spending the cell targets, or NONE when it does not
	// clearly favour a side. Uses the plan the cell's absence would produce, so it tracks the
	// real route rather than a fixed rule.
	private Side preferredSide(Snapshot s, PlannerSettings c)
	{
		Instruction next = afterStones(s.toBuilder().chargedCell(null).build(), c);
		switch (next.getTarget())
		{
			case DEPOSIT_POOL:
				return Side.WEST;
			case UNCHARGED_CELL_TABLE:
			case WEAK_CELL_TABLE:
			case LARGE_REMAINS:
			case GUARDIAN_REMAINS:
			case GUARDIAN_REMAINS_ENTRANCE:
			case HUGE_REMAINS:
				return Side.EAST;
			default:
				return Side.NONE;
		}
	}

	// West/east of the ring of barriers, whose average x is the guardian's.
	private static double barrierCentreX(List<BarrierState> barriers)
	{
		double sum = 0;
		for (BarrierState barrier : barriers)
		{
			sum += barrier.getLocation().getX();
		}
		return barriers.isEmpty() ? 0 : sum / barriers.size();
	}

	private static boolean onSide(BarrierState barrier, Side side, double centreX)
	{
		if (side == Side.NONE)
		{
			return false;
		}
		double x = barrier.getLocation().getX();
		return side == Side.WEST ? x < centreX : x > centreX;
	}

	private enum Side
	{
		NONE, WEST, EAST
	}

	private static boolean needsWork(BarrierState barrier)
	{
		if (barrier.getTier().getRank() < CellTier.OVERCHARGED.getRank())
		{
			return true;
		}
		int hp = barrier.getHealthPercent();
		return hp >= 0 && hp <= 70;
	}

	// Which case the closest-first pick fell into, spelled out under the instruction.
	private static String barrierDetail(PlannerSettings c, BarrierState target)
	{
		if (c.getBarrierPriority() != BarrierPriority.CLOSEST)
		{
			return "";
		}
		return needsWork(target) ? "nearest low-health barrier" : "closest healthy barrier";
	}

	private static Alignment shortAlignment(Snapshot s, PlannerSettings c)
	{
		int elemental = AltarChooser.projected(Alignment.ELEMENTAL, s, c);
		int catalytic = AltarChooser.projected(Alignment.CATALYTIC, s, c);
		return catalytic < elemental ? Alignment.CATALYTIC : Alignment.ELEMENTAL;
	}

	private static Target pileFor(Alignment alignment)
	{
		return alignment == Alignment.ELEMENTAL ? Target.ESSENCE_PILE_ELEMENTAL : Target.ESSENCE_PILE_CATALYTIC;
	}

	// Compare round-trip costs; fall back to agility when distances are unavailable.
	static Target miningTarget(Snapshot s, int needed)
	{
		if (s.getAgilityLevel() < 56)
		{
			return Target.GUARDIAN_REMAINS;
		}
		Integer toLarge = s.getTravelTicks().get(Target.LARGE_REMAINS);
		Integer toParts = s.getTravelTicks().get(Target.GUARDIAN_REMAINS);
		if (toLarge == null || toParts == null || needed <= 0)
		{
			return Target.LARGE_REMAINS;
		}
		int backLarge = s.getReturnTicks().getOrDefault(Target.LARGE_REMAINS, toLarge);
		int backParts = s.getReturnTicks().getOrDefault(Target.GUARDIAN_REMAINS, toParts);
		double largeSeconds = (toLarge + backLarge) / 2.0 * 0.6 + needed / s.getFragmentsPerTick() * 0.6;
		double partsSeconds = (toParts + backParts) / 2.0 * 0.6 + needed / s.getPartsPerTick() * 0.6;
		return largeSeconds <= partsSeconds ? Target.LARGE_REMAINS : Target.GUARDIAN_REMAINS;
	}

	private static Target miningTarget(Snapshot s)
	{
		return miningTarget(s, TILE_REPAIR_FRAGMENTS);
	}

	private static String miningHeadline(Target target)
	{
		return target == Target.LARGE_REMAINS ? "Mine the large guardian remains" : "Mine the guardian parts nearby";
	}

	private static String direction(Snapshot s)
	{
		return s.getPortalDirection() == null || s.getPortalDirection().isEmpty() ? "" : " (" + s.getPortalDirection() + ")";
	}

	@Nullable
	private static WorldPoint soloTile(Snapshot s)
	{
		if (s.getEmptyTiles().isEmpty())
		{
			return null;
		}
		WorldPoint fire = s.getPortalGuardians().get(Altar.FIRE);
		WorldPoint blood = s.getPortalGuardians().get(Altar.BLOOD);
		if (fire == null || blood == null)
		{
			return nearest(s.getEmptyTiles(), s.getPlayerLocation());
		}
		WorldPoint mid = new WorldPoint((fire.getX() + blood.getX()) / 2, (fire.getY() + blood.getY()) / 2, fire.getPlane());
		return nearest(s.getEmptyTiles(), mid);
	}

	@Nullable
	private static WorldPoint nearest(List<WorldPoint> points, @Nullable WorldPoint from)
	{
		if (points.isEmpty())
		{
			return null;
		}
		if (from == null)
		{
			return points.get(0);
		}
		WorldPoint best = null;
		int bestDist = Integer.MAX_VALUE;
		for (WorldPoint point : points)
		{
			int d = point.distanceTo2D(from);
			if (d < bestDist)
			{
				bestDist = d;
				best = point;
			}
		}
		return best;
	}
}
