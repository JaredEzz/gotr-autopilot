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

import com.fabulousotter.gotr.model.Alignment;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.overlay.InstructionOverlay;
import com.fabulousotter.gotr.overlay.ItemHighlightOverlay;
import com.fabulousotter.gotr.overlay.MagicImbueOverlay;
import com.fabulousotter.gotr.overlay.Pathfinder;
import com.fabulousotter.gotr.overlay.SceneOverlay;
import com.fabulousotter.gotr.plan.AltarChooser;
import com.fabulousotter.gotr.plan.Instruction;
import com.fabulousotter.gotr.plan.Planner;
import com.fabulousotter.gotr.plan.PlannerSettings;
import com.fabulousotter.gotr.plan.Step;
import com.fabulousotter.gotr.plan.Target;
import com.fabulousotter.gotr.plan.Urgency;
import com.fabulousotter.gotr.state.GamePhase;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.Snapshot;
import com.fabulousotter.gotr.state.StateTracker;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.GameObject;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "GOTR Autopilot",
	description = "Tells you exactly what to do next in Guardians of the Rift",
	tags = {"gotr", "guardians of the rift", "runecraft", "minigame"}
)
public class GotrAutopilotPlugin extends Plugin
{
	private static final Instruction NOT_IN_GAME = Instruction.builder().step(Step.NOT_IN_GAME).headline("").build();
	private static final int SHORTCUT_AGILITY_LEVEL = 56;
	private static final int CENTRE_WAIT_SEARCH_TILES = 8;
	// Ticks the previous altar instruction is kept after its talisman is spent, covering the
	// fade before the client reports the altar room.
	private static final int TALISMAN_FADE_TICKS = 5;
	private static final int[] CENTRE_WAIT_COLUMNS = {0, -1, 1, -2, 2};
	private static final int CENTRE_WAIT_BLOCKED = CollisionDataFlag.BLOCK_MOVEMENT_FULL
		| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
		| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;
	private static final Set<Integer> CLIMBABLE_ENDS = ImmutableSet.of(
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP,
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM
	);
	private static final Set<Integer> BOTTOM_PIECES = ImmutableSet.of(
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM,
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM_NOOP
	);
	private static final Set<Integer> TOP_PIECES = ImmutableSet.of(
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP,
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP_NOOP
	);
	// Tiles read off the temple itself (it is not instanced): where the player stands at each
	// end of the rubble shortcut and where the large guardian remains are mined from.
	private static final WorldPoint RUBBLE_TEMPLE_END = new WorldPoint(3633, 9503, 0);
	private static final WorldPoint RUBBLE_REMAINS_END = new WorldPoint(3637, 9503, 0);
	private static final WorldPoint LARGE_REMAINS_STAND = new WorldPoint(3639, 9500, 0);
	private static final int RUBBLE_MATCH_TILES = 4;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private GotrAutopilotConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	// Skips drawing the Great Guardian while a cell is to be placed on a tile or barrier, so
	// its large model beside the tiles cannot take the click instead.
	private final RenderCallback drawListener = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUi)
		{
			return shouldDraw(renderable, drawingUi);
		}
	};
	private boolean hideGuardian;

	@Inject
	@Getter
	private StateTracker tracker;

	@Inject
	private InstructionOverlay instructionOverlay;

	@Inject
	private SceneOverlay sceneOverlay;

	@Inject
	private ItemHighlightOverlay itemHighlightOverlay;

	@Inject
	private MagicImbueOverlay magicImbueOverlay;

	private final Planner planner = new Planner();

	@Getter
	private Snapshot snapshot = Snapshot.OUTSIDE;
	@Getter
	private Instruction instruction = NOT_IN_GAME;
	@Getter
	@Nullable
	private NPC targetNpc;
	@Getter
	@Nullable
	private TileObject targetObject;
	@Getter
	@Nullable
	private TileObject countdownObject;
	@Getter
	private Pathfinder.Path path = Pathfinder.Path.EMPTY;

	private Pathfinder pathfinder;
	private List<Pathfinder.Transport> transports = Collections.emptyList();
	private Step lastStep = Step.NOT_IN_GAME;
	private Altar lastAltar;
	private WorldPoint lastTile;
	private int stepStartTick;
	private Step pathLoggedStep;
	private int talismanHoldUntil = -1;
	private boolean hintArrowSet;
	private int sceneVersion = -1;
	private boolean canUseShortcuts;

	@Override
	protected void startUp()
	{
		pathfinder = new Pathfinder(client);
		overlayManager.add(instructionOverlay);
		overlayManager.add(sceneOverlay);
		overlayManager.add(itemHighlightOverlay);
		overlayManager.add(magicImbueOverlay);
		eventBus.register(tracker);
		renderCallbackManager.register(drawListener);
		clientThread.invokeLater(tracker::primeFromClient);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(tracker);
		renderCallbackManager.unregister(drawListener);
		hideGuardian = false;
		overlayManager.remove(instructionOverlay);
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(itemHighlightOverlay);
		overlayManager.remove(magicImbueOverlay);
		tracker.reset();
		clearInstruction();
		pathfinder = null;
	}

	// Called for every renderable each frame, so it does nothing but compare references.
	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		return !hideGuardian || renderable != tracker.getGreatGuardian();
	}

	private void clearInstruction()
	{
		clearHintArrow();
		snapshot = Snapshot.OUTSIDE;
		instruction = NOT_IN_GAME;
		targetNpc = null;
		targetObject = null;
		countdownObject = null;
		hideGuardian = false;
		path = Pathfinder.Path.EMPTY;
		transports = Collections.emptyList();
		lastStep = Step.NOT_IN_GAME;
		lastAltar = null;
		lastTile = null;
		sceneVersion = -1;
	}

	@Provides
	GotrAutopilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GotrAutopilotConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tracker.tick();
		Snapshot s = tracker.snapshot();
		if (s.getLocation() == Location.OUTSIDE)
		{
			clearInstruction();
			pathfinder.clear();
			return;
		}
		boolean shortcutsAvailable = s.getAgilityLevel() >= SHORTCUT_AGILITY_LEVEL;
		if (sceneVersion != tracker.getSceneVersion() || canUseShortcuts != shortcutsAvailable)
		{
			sceneVersion = tracker.getSceneVersion();
			canUseShortcuts = shortcutsAvailable;
			transports = canUseShortcuts ? shortcutGroups() : Collections.emptyList();
			pathfinder.clear();
		}
		if (s.getLocation() == Location.TEMPLE)
		{
			s = s.toBuilder()
				.travelTicks(travelTicks())
				.guardianTravelTicks(guardianTravelTicks())
				.returnTicks(returnTicks())
				.chosenTile(lastTile)
				.lastStep(lastStep)
				.build();
		}
		Instruction next = planner.plan(s, settingsFor(s));
		next = holdThroughTalismanFade(s, next);
		if (next.getTarget() == Target.CENTRE_WAIT)
		{
			// With no reachable wait tile, the guardian itself is the destination.
			WorldPoint wait = centreWaitTile();
			next = next.toBuilder().location(wait != null ? wait : tracker.getGreatGuardianCentre()).build();
		}
		lastTile = next.getTarget() == Target.CELL_TILE || next.getTarget() == Target.BARRIER ? next.getLocation() : null;
		snapshot = s;
		instruction = next;

		boolean altarSwitched = next.getStep() == Step.GO_TO_ALTAR && lastStep == Step.GO_TO_ALTAR
			&& next.getAltar() != null && next.getAltar() != lastAltar;
		if (next.getStep() != lastStep || altarSwitched)
		{
			int tick = client.getTickCount();
			// A mid-run altar change is always announced: the player is running the wrong way.
			boolean afterLongStint = altarSwitched || (tick - stepStartTick) * 0.6 >= config.notifyAfterSeconds();
			lastStep = next.getStep();
			stepStartTick = tick;
			announce(next, afterLongStint);
		}
		lastAltar = next.getAltar();

		targetNpc = resolveNpc(next);
		targetObject = targetNpc == null ? resolveObject(next) : null;
		countdownObject = resolveCountdownObject(s, next);
		updateHintArrow(next);
		path = config.showPath() ? computePath(next) : Pathfinder.Path.EMPTY;
		hideGuardian = config.hideGuardianForCells()
			&& (next.getTarget() == Target.CELL_TILE || next.getTarget() == Target.BARRIER);
		boolean noPath = config.showPath() && path.isEmpty() && next.getTarget() != Target.NONE;
		// Once per step, then every 30 s while it persists.
		if (noPath && (next.getStep() != pathLoggedStep || client.getTickCount() % 50 == 0))
		{
			Player me = client.getLocalPlayer();
			log.debug("No path to {} at {} from {} (npc {}, object {}, shortcuts {}, transport groups {}, agility {})",
				next.getTarget(), next.getLocation(), me == null ? null : me.getWorldLocation(), targetNpc != null,
				targetObject != null, tracker.getShortcuts().size(), transports.size(), client.getRealSkillLevel(Skill.AGILITY));
		}
		pathLoggedStep = noPath ? next.getStep() : null;
	}

	// Deprioritize the plain craft on an elemental altar when a combination rune is possible, so
	// a left-click there walks instead of wasting the load; the option stays on right-click.
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.comboAltarClickGuard())
		{
			return;
		}
		Altar altar = Altar.fromAltarObject(event.getIdentifier());
		if (altar == null || altar.getAlignment() != Alignment.ELEMENTAL
			|| !"Craft-rune".equals(event.getOption()))
		{
			return;
		}
		if (comboAvailableAt(altar))
		{
			event.getMenuEntry().setDeprioritized(true);
		}
	}

	// True when using the base rune on this altar would make a combination rune right now.
	public boolean comboAvailableAt(@Nullable Altar altar)
	{
		return altar != null && snapshot.getLocation() == Location.ALTAR_ROOM
			&& AltarChooser.combinationAt(altar, snapshot, settingsFor(snapshot)) != null;
	}

	// While dropping an essence is the step, make Drop the item's default left-click option. The
	// top of the menu is the last entry, so the Drop entry is moved there.
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.dropEssenceLeftClick() || instruction.getStep() != Step.DROP_ESSENCE)
		{
			return;
		}
		Set<Integer> items = instruction.getItems();
		MenuEntry[] entries = event.getMenuEntries();
		for (int i = 0; i < entries.length - 1; i++)
		{
			MenuEntry entry = entries[i];
			if ("Drop".equals(entry.getOption()) && items.contains(entry.getItemId()))
			{
				System.arraycopy(entries, i + 1, entries, i, entries.length - i - 1);
				entries[entries.length - 1] = entry;
				client.setMenuEntries(entries);
				return;
			}
		}
	}

	// NPC and object bounds are in scene coordinates.
	private Pathfinder.Path computePath(Instruction next)
	{
		Player player = client.getLocalPlayer();
		if (next.getTarget() == Target.NONE || player == null)
		{
			pathfinder.clear();
			return Pathfinder.Path.EMPTY;
		}
		if (targetNpc != null)
		{
			LocalPoint lp = targetNpc.getLocalLocation();
			if (lp == null)
			{
				return Pathfinder.Path.EMPTY;
			}
			int size = npcSize(targetNpc);
			// getLocalLocation is the centre of the NPC; the footprint's south-west corner is offset.
			int minX = lp.getSceneX() - (size - 1) / 2;
			int minY = lp.getSceneY() - (size - 1) / 2;
			// Approach barriers from the temple side.
			Point toward = next.getTarget() == Target.BARRIER ? templeCentre() : null;
			Pathfinder.Path route = pathfinder.pathTo(minX, minY, minX + size - 1, minY + size - 1, transports, toward);
			return next.getTarget() == Target.BARRIER ? route.endingAt(targetNpc.getWorldLocation()) : route;
		}
		if (targetObject instanceof GameObject)
		{
			GameObject object = (GameObject) targetObject;
			if (next.getTarget() == Target.LARGE_REMAINS)
			{
				LocalPoint stand = LocalPoint.fromWorld(player.getWorldView(), LARGE_REMAINS_STAND);
				if (stand != null && pathfinder.reachable(stand.getSceneX(), stand.getSceneY(), transports))
				{
					return pathfinder.pathOnto(stand.getSceneX(), stand.getSceneY(), transports);
				}
			}
			Point min = object.getSceneMinLocation();
			Point max = object.getSceneMaxLocation();
			// Remains can be mined from a diagonal tile, so the route need not go round to a side.
			return pathfinder.pathTo(min.getX(), min.getY(), max.getX(), max.getY(), transports, null, isRemains(next.getTarget()));
		}
		if (aimedAtGuardian(next))
		{
			int[] box = guardianFootprint();
			return box == null ? Pathfinder.Path.EMPTY : pathfinder.pathTo(box[0], box[1], box[2], box[3], transports);
		}
		WorldPoint at = next.getLocation();
		if (at == null && targetObject != null)
		{
			at = targetObject.getWorldLocation();
		}
		if (at == null)
		{
			return Pathfinder.Path.EMPTY;
		}
		LocalPoint lp = LocalPoint.fromWorld(player.getWorldView(), at);
		if (lp == null)
		{
			return Pathfinder.Path.EMPTY;
		}
		if (next.getTarget() == Target.CELL_TILE)
		{
			Point stand = standingTile(lp.getSceneX(), lp.getSceneY(), transports);
			if (stand != null)
			{
				return pathfinder.pathOnto(stand.getX(), stand.getY(), transports).endingAt(at);
			}
			return pathfinder.pathTo(lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY(), transports, templeCentre()).endingAt(at);
		}
		return pathfinder.pathTo(lp.getSceneX(), lp.getSceneY(), lp.getSceneX(), lp.getSceneY(), transports, null);
	}

	/**
	 * The tile to stand on to use a cell tile: the neighbour straight in from the wall, taken
	 * as the reachable orthogonal neighbour closest to the temple's centre. The cell tile sits
	 * in the wall line, so the tile on the temple side is the one the barrier is worked from.
	 */
	@Nullable
	private Point standingTile(int x, int y, List<Pathfinder.Transport> transports)
	{
		Point centre = templeCentre();
		if (centre == null)
		{
			return null;
		}
		Point best = null;
		long bestGap = Long.MAX_VALUE;
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] side : sides)
		{
			int nx = x + side[0];
			int ny = y + side[1];
			long dx = nx - centre.getX();
			long dy = ny - centre.getY();
			long gap = dx * dx + dy * dy;
			if (gap < bestGap && pathfinder.reachable(nx, ny, transports))
			{
				bestGap = gap;
				best = new Point(nx, ny);
			}
		}
		return best;
	}

	/**
	 * Using a talisman consumes it a few ticks before the client reports the altar room. Without
	 * it, the chooser would send the player to another open altar mid-fade, so the instruction
	 * that was being followed is kept for those ticks.
	 */
	private Instruction holdThroughTalismanFade(Snapshot s, Instruction next)
	{
		int tick = client.getTickCount();
		boolean spent = lastStep == Step.GO_TO_ALTAR && lastAltar != null
			&& snapshot.getTalismans().contains(lastAltar) && !s.getTalismans().contains(lastAltar);
		if (spent)
		{
			talismanHoldUntil = tick + TALISMAN_FADE_TICKS;
		}
		boolean sameAltar = next.getStep() == Step.GO_TO_ALTAR && next.getAltar() == lastAltar;
		if (tick < talismanHoldUntil && s.getLocation() == Location.TEMPLE && !sameAltar && instruction.getStep() == Step.GO_TO_ALTAR)
		{
			return instruction;
		}
		return next;
	}

	// Use a walkable tile outside the guardian footprint.
	@Nullable
	private WorldPoint centreWaitTile()
	{
		int[] box = guardianFootprint();
		Player player = client.getLocalPlayer();
		if (box == null || player == null)
		{
			return null;
		}
		WorldView wv = player.getWorldView();
		CollisionData[] maps = wv.getCollisionMaps();
		int plane = wv.getPlane();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return null;
		}
		int[][] flags = maps[plane].getFlags();
		int minY = box[1];
		int midX = (box[0] + box[2]) / 2;
		// Nearest row first, the middle column first within it.
		for (int dy = 1; dy <= CENTRE_WAIT_SEARCH_TILES; dy++)
		{
			int y = minY - dy;
			for (int dx : CENTRE_WAIT_COLUMNS)
			{
				int x = midX + dx;
				if (x < 0 || y < 0 || x >= flags.length || y >= flags[x].length)
				{
					continue;
				}
				// Walkable by its flags and actually reachable from where the player stands.
				if ((flags[x][y] & CENTRE_WAIT_BLOCKED) == 0 && pathfinder.distanceTo(x, y, x, y, transports) >= 0)
				{
					return WorldPoint.fromScene(wv, x, y, plane);
				}
			}
		}
		log.debug("No reachable tile south of the Great Guardian; routing to the guardian instead");
		return null;
	}

	/**
	 * The middle of the temple, as the centroid of the ring of cell tiles: the side a barrier
	 * is approached from. The guardian stands at the north edge, so it is the wrong reference
	 * for barriers on the east and west, and is used only when no cell tiles are known.
	 */
	@Nullable
	private Point templeCentre()
	{
		Player player = client.getLocalPlayer();
		Set<WorldPoint> tiles = tracker.getCellTiles().keySet();
		if (player == null || tiles.isEmpty())
		{
			return guardianCentre();
		}
		long sx = 0;
		long sy = 0;
		int n = 0;
		for (WorldPoint tile : tiles)
		{
			LocalPoint lp = LocalPoint.fromWorld(player.getWorldView(), tile);
			if (lp != null)
			{
				sx += lp.getSceneX();
				sy += lp.getSceneY();
				n++;
			}
		}
		return n == 0 ? guardianCentre() : new Point((int) Math.round((double) sx / n), (int) Math.round((double) sy / n));
	}

	@Nullable
	private Point guardianCentre()
	{
		int[] box = guardianFootprint();
		return box == null ? null : new Point((box[0] + box[2]) / 2, (box[1] + box[3]) / 2);
	}

	/**
	 * The Great Guardian's footprint in scene coordinates as {minX, minY, maxX, maxY}: from
	 * the live NPC when the client has it, otherwise from where it was last seen, since the
	 * client drops NPCs beyond about 15 tiles and the guardian never moves.
	 */
	@Nullable
	private int[] guardianFootprint()
	{
		NPC guardian = tracker.getGreatGuardian();
		int size;
		int cx;
		int cy;
		if (guardian != null && guardian.getLocalLocation() != null)
		{
			size = npcSize(guardian);
			cx = guardian.getLocalLocation().getSceneX();
			cy = guardian.getLocalLocation().getSceneY();
		}
		else
		{
			Player player = client.getLocalPlayer();
			WorldPoint centre = tracker.getGreatGuardianCentre();
			LocalPoint lp = player == null || centre == null ? null : LocalPoint.fromWorld(player.getWorldView(), centre);
			if (lp == null)
			{
				return null;
			}
			size = Math.max(1, tracker.getGreatGuardianSize());
			cx = lp.getSceneX();
			cy = lp.getSceneY();
		}
		int minX = cx - (size - 1) / 2;
		int minY = cy - (size - 1) / 2;
		return new int[]{minX, minY, minX + size - 1, minY + size - 1};
	}

	// The guardian's footprint is the destination, whether named directly or as the centre wait.
	private boolean aimedAtGuardian(Instruction next)
	{
		if (next.getTarget() == Target.GREAT_GUARDIAN)
		{
			return true;
		}
		return next.getTarget() == Target.CENTRE_WAIT && next.getLocation() != null
			&& next.getLocation().equals(tracker.getGreatGuardianCentre());
	}

	private static int npcSize(NPC npc)
	{
		return npc.getComposition() == null ? 1 : Math.max(1, npc.getComposition().getSize());
	}

	private Map<Target, Integer> travelTicks()
	{
		Map<Target, Integer> out = new EnumMap<>(Target.class);
		putDistance(out, Target.WORKBENCH, tracker.getLandmarks().get(ObjectID.GOTR_WORKBENCH));
		putDistance(out, Target.DEPOSIT_POOL, tracker.getLandmarks().get(ObjectID.GOTR_DEPOSITCHEST));
		putDistance(out, Target.UNCHARGED_CELL_TABLE, tracker.getLandmarks().get(ObjectID.GOTR_UNCHARGED_CELLS));
		putDistance(out, Target.WEAK_CELL_TABLE, tracker.getLandmarks().get(ObjectID.GOTR_WEAK_CELLS));
		putDistance(out, Target.LARGE_REMAINS, nearest(tracker.getLargeRemains()));
		putDistance(out, Target.GUARDIAN_REMAINS, nearest(tracker.getPartsRemains()));
		putDistance(out, Target.GUARDIAN_REMAINS_ENTRANCE, southmost(tracker.getPartsRemains()));
		putDistance(out, Target.PORTAL, tracker.getPortal());
		int[] box = guardianFootprint();
		if (box != null)
		{
			int d = pathfinder.distanceTo(box[0], box[1], box[2], box[3], transports);
			if (d >= 0)
			{
				out.put(Target.GREAT_GUARDIAN, d);
			}
		}
		return out;
	}

	private Map<Target, Integer> returnTicks()
	{
		Map<Target, Integer> out = new EnumMap<>(Target.class);
		GameObject bench = tracker.getLandmarks().get(ObjectID.GOTR_WORKBENCH);
		if (bench == null)
		{
			return out;
		}
		putReturn(out, Target.LARGE_REMAINS, nearest(tracker.getLargeRemains()), bench);
		putReturn(out, Target.GUARDIAN_REMAINS, nearest(tracker.getPartsRemains()), bench);
		return out;
	}

	private void putReturn(Map<Target, Integer> out, Target target, @Nullable GameObject object, GameObject bench)
	{
		if (object == null)
		{
			return;
		}
		int d = pathfinder.distanceBetween(
			bench.getSceneMinLocation().getX(), bench.getSceneMinLocation().getY(),
			bench.getSceneMaxLocation().getX(), bench.getSceneMaxLocation().getY(),
			object.getSceneMinLocation().getX(), object.getSceneMinLocation().getY(),
			object.getSceneMaxLocation().getX(), object.getSceneMaxLocation().getY(), transports);
		if (d >= 0)
		{
			out.put(target, d);
		}
	}

	private Map<Altar, Integer> guardianTravelTicks()
	{
		Map<Altar, Integer> out = new EnumMap<>(Altar.class);
		for (Map.Entry<Altar, GameObject> entry : tracker.getPortalGuardians().entrySet())
		{
			int d = distanceTo(entry.getValue());
			if (d >= 0)
			{
				out.put(entry.getKey(), d);
			}
		}
		return out;
	}

	private void putDistance(Map<Target, Integer> out, Target target, @Nullable GameObject object)
	{
		if (object == null)
		{
			return;
		}
		int d = distanceTo(object);
		if (d >= 0)
		{
			out.put(target, d);
		}
	}

	private int distanceTo(GameObject object)
	{
		Point min = object.getSceneMinLocation();
		Point max = object.getSceneMaxLocation();
		return pathfinder.distanceTo(min.getX(), min.getY(), max.getX(), max.getY(), transports);
	}

	// Group rubble by proximity. Only groups with a climbable end become transports.
	private List<Pathfinder.Transport> shortcutGroups()
	{
		if (tracker.getShortcuts().isEmpty())
		{
			return Collections.emptyList();
		}
		List<List<TileObject>> clusters = new ArrayList<>();
		for (TileObject object : tracker.getShortcuts())
		{
			List<TileObject> home = null;
			for (List<TileObject> cluster : clusters)
			{
				for (TileObject member : cluster)
				{
					if (member.getWorldLocation().distanceTo2D(object.getWorldLocation()) <= 3)
					{
						home = cluster;
						break;
					}
				}
				if (home != null)
				{
					break;
				}
			}
			if (home == null)
			{
				home = new ArrayList<>();
				clusters.add(home);
			}
			home.add(object);
		}
		List<Pathfinder.Transport> transports = new ArrayList<>();
		for (List<TileObject> cluster : clusters)
		{
			boolean climbable = false;
			Set<Integer> bottom = new HashSet<>();
			Set<Integer> top = new HashSet<>();
			WorldPoint bottomAnchor = null;
			WorldPoint topAnchor = null;
			for (TileObject object : cluster)
			{
				boolean end = CLIMBABLE_ENDS.contains(object.getId());
				if (end)
				{
					climbable = true;
				}
				if (BOTTOM_PIECES.contains(object.getId()))
				{
					ring(bottom, object, 1);
					// Prefer the interactive piece as the route anchor.
					if (bottomAnchor == null || end)
					{
						bottomAnchor = object.getWorldLocation();
					}
				}
				else if (TOP_PIECES.contains(object.getId()))
				{
					ring(top, object, 1);
					if (topAnchor == null || end)
					{
						topAnchor = object.getWorldLocation();
					}
				}
			}
			if (!climbable)
			{
				continue;
			}
			if (isTheRubble(cluster))
			{
				// The known shortcut: land exactly on the tiles the player stands on at each end,
				// so the route runs end to end with no step off to the side.
				Integer templeEnd = sceneKey(RUBBLE_TEMPLE_END);
				Integer remainsEnd = sceneKey(RUBBLE_REMAINS_END);
				if (templeEnd != null && remainsEnd != null)
				{
					transports.add(new Pathfinder.Transport(ImmutableSet.of(templeEnd), ImmutableSet.of(remainsEnd),
						RUBBLE_TEMPLE_END, RUBBLE_REMAINS_END));
					continue;
				}
			}
			if (bottom.isEmpty() || top.isEmpty())
			{
				Set<Integer> whole = new HashSet<>();
				for (TileObject object : cluster)
				{
					ring(whole, object, 2);
				}
				bottom = whole;
				top = whole;
				bottomAnchor = null;
				topAnchor = null;
			}
			transports.add(new Pathfinder.Transport(bottom, top, bottomAnchor, topAnchor));
		}
		return transports;
	}

	private static boolean isTheRubble(List<TileObject> cluster)
	{
		for (TileObject object : cluster)
		{
			WorldPoint at = object.getWorldLocation();
			if (at.distanceTo2D(RUBBLE_TEMPLE_END) <= RUBBLE_MATCH_TILES || at.distanceTo2D(RUBBLE_REMAINS_END) <= RUBBLE_MATCH_TILES)
			{
				return true;
			}
		}
		return false;
	}

	@Nullable
	private Integer sceneKey(WorldPoint point)
	{
		Player player = client.getLocalPlayer();
		LocalPoint lp = player == null ? null : LocalPoint.fromWorld(player.getWorldView(), point);
		return lp == null ? null : Pathfinder.tileKey(lp.getSceneX(), lp.getSceneY());
	}

	private static void ring(Set<Integer> tiles, TileObject object, int radius)
	{
		LocalPoint lp = object.getLocalLocation();
		if (lp == null)
		{
			return;
		}
		for (int x = lp.getSceneX() - radius; x <= lp.getSceneX() + radius; x++)
		{
			for (int y = lp.getSceneY() - radius; y <= lp.getSceneY() + radius; y++)
			{
				tiles.add(Pathfinder.tileKey(x, y));
			}
		}
	}

	private PlannerSettings settingsFor(Snapshot s)
	{
		boolean combo = s.isBindingNecklaceWorn() && s.getMagicLevel() >= 82 && s.getBaseRuneCount() > 0
			&& (s.isLunarSpellbook() || (s.getBaseRune() != null && s.getTalismans().contains(s.getBaseRune())));
		return PlannerSettings.builder()
			.strategy(config.strategy())
			.openingFragmentTarget(config.openingFragments())
			.craftWindowSeconds(config.craftWindow())
			.minFragmentsToCraft(config.minFragmentsToCraft())
			.desiredGuardians(config.desiredGuardians())
			.combinationRunes(combo)
			.baseRune(s.getBaseRune())
			.balanceWithSavedPoints(config.balanceWithSaved())
			.maxImbalance(config.maxImbalance())
			.preferredAltars(config.preferredAltars())
			.dispreferredAltars(config.dispreferredAltars())
			.protectRightBarrier(config.protectRightBarrier())
			.barrierPriority(config.barrierPriority())
			.portalMinCapacity(config.portalMinCapacity())
			.build();
	}

	// Suppress notifications for short-lived steps.
	private void announce(Instruction next, boolean afterLongStint)
	{
		if (next.getStep() == Step.NOT_IN_GAME || next.getHeadline().isEmpty())
		{
			return;
		}
		// A quiet step still earns a ping when it sends the player somewhere, such as the
		// walk to the centre for the altars; only a stay-put wait is silent.
		boolean actionable = next.getUrgency() != Urgency.INFO || next.getTarget() != Target.NONE;
		if (actionable && afterLongStint)
		{
			notifier.notify(config.notification(), next.getHeadline());
		}
		if (config.chatSteps())
		{
			String line = next.getDetail().isEmpty() ? next.getHeadline() : next.getHeadline() + ": " + next.getDetail();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=00b3a1>[GOTR]</col> " + line, null);
		}
	}

	private void updateHintArrow(Instruction next)
	{
		if (!config.hintArrow() || next.getTarget() == Target.NONE)
		{
			clearHintArrow();
			return;
		}
		if (targetNpc != null)
		{
			client.setHintArrow(targetNpc);
			hintArrowSet = true;
			return;
		}
		WorldPoint point = next.getLocation();
		if (point == null && targetObject != null)
		{
			point = targetObject.getWorldLocation();
		}
		if (point == null)
		{
			clearHintArrow();
			return;
		}
		client.setHintArrow(point);
		hintArrowSet = true;
	}

	private void clearHintArrow()
	{
		if (hintArrowSet)
		{
			client.clearHintArrow();
			hintArrowSet = false;
		}
	}

	@Nullable
	private TileObject resolveCountdownObject(Snapshot s, Instruction next)
	{
		if (s.getPhase() != GamePhase.COUNTDOWN || s.getSecondsToStart() < 0)
		{
			return null;
		}
		Target remains;
		if (s.getLocation() == Location.HUGE_REMAINS)
		{
			remains = Target.HUGE_REMAINS;
		}
		else if (next.getTarget() == Target.GUARDIAN_REMAINS || next.getTarget() == Target.LARGE_REMAINS)
		{
			remains = next.getTarget();
		}
		else
		{
			remains = s.getAgilityLevel() >= SHORTCUT_AGILITY_LEVEL ? Target.LARGE_REMAINS : Target.GUARDIAN_REMAINS;
		}
		return remains == next.getTarget() ? targetObject : resolveObject(Instruction.builder().step(Step.PRE_POSITION).headline("").target(remains).build());
	}

	@Nullable
	private NPC resolveNpc(Instruction i)
	{
		switch (i.getTarget())
		{
			case GREAT_GUARDIAN:
				return tracker.getGreatGuardian();
			case CENTRE_WAIT:
				return aimedAtGuardian(i) ? tracker.getGreatGuardian() : null;
			case APPRENTICE_CORDELIA:
				return tracker.getCordelia();
			case BARRIER:
				for (NPC npc : tracker.getBarrierNpcs().values())
				{
					if (i.getLocation() == null || npc.getWorldLocation().equals(i.getLocation()))
					{
						return npc;
					}
				}
				return null;
			default:
				return null;
		}
	}

	private static boolean isRemains(Target target)
	{
		return target == Target.LARGE_REMAINS || target == Target.GUARDIAN_REMAINS
			|| target == Target.GUARDIAN_REMAINS_ENTRANCE || target == Target.HUGE_REMAINS;
	}

	@Nullable
	private TileObject resolveObject(Instruction i)
	{
		switch (i.getTarget())
		{
			case LARGE_REMAINS:
				return nearest(tracker.getLargeRemains());
			case GUARDIAN_REMAINS:
				return nearest(tracker.getPartsRemains());
			case GUARDIAN_REMAINS_ENTRANCE:
				return southmost(tracker.getPartsRemains());
			case HUGE_REMAINS:
				return nearest(tracker.getHugeRemains());
			case WORKBENCH:
				return tracker.getLandmarks().get(ObjectID.GOTR_WORKBENCH);
			case DEPOSIT_POOL:
				return tracker.getLandmarks().get(ObjectID.GOTR_DEPOSITCHEST);
			case UNCHARGED_CELL_TABLE:
				return tracker.getLandmarks().get(ObjectID.GOTR_UNCHARGED_CELLS);
			case WEAK_CELL_TABLE:
				return tracker.getLandmarks().get(ObjectID.GOTR_WEAK_CELLS);
			case ESSENCE_PILE_ELEMENTAL:
				return tracker.getLandmarks().get(ObjectID.GOTR_GUARDIAN_SPAWN_EAST);
			case ESSENCE_PILE_CATALYTIC:
				return tracker.getLandmarks().get(ObjectID.GOTR_GUARDIAN_SPAWN_WEST);
			case PORTAL:
				return tracker.getPortal();
			case ALTAR_PORTAL:
				return i.getAltar() == null ? null : tracker.getPortalGuardians().get(i.getAltar());
			case RUNIC_ALTAR:
				return tracker.getAltarObject();
			case ALTAR_EXIT:
				return tracker.getAltarExit();
			case CELL_TILE:
				return cellTileAt(i.getLocation());
			default:
				return null;
		}
	}

	// Fall back to a nearby tile if the exact object has not loaded.
	@Nullable
	private GroundObject cellTileAt(@Nullable WorldPoint location)
	{
		if (location == null)
		{
			return null;
		}
		GroundObject exact = tracker.getCellTiles().get(location);
		if (exact != null)
		{
			return exact;
		}
		GroundObject best = null;
		int bestDist = 4;
		for (GroundObject tile : tracker.getCellTiles().values())
		{
			int d = tile.getWorldLocation().distanceTo2D(location);
			if (d < bestDist)
			{
				bestDist = d;
				best = tile;
			}
		}
		return best;
	}

	// The entrance is on the south edge of the scene.
	@Nullable
	private static GameObject southmost(List<GameObject> objects)
	{
		GameObject best = null;
		for (GameObject object : objects)
		{
			if (best == null || object.getWorldLocation().getY() < best.getWorldLocation().getY())
			{
				best = object;
			}
		}
		return best;
	}

	@Nullable
	private GameObject nearest(List<GameObject> objects)
	{
		Player player = client.getLocalPlayer();
		if (objects.isEmpty() || player == null)
		{
			return null;
		}
		WorldPoint me = player.getWorldLocation();
		GameObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (GameObject object : objects)
		{
			int d = object.getWorldLocation().distanceTo2D(me);
			if (d < bestDist)
			{
				bestDist = d;
				best = object;
			}
		}
		return best;
	}
}
