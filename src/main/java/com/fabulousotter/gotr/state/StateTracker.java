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

import com.fabulousotter.gotr.GotrAutopilotConfig;
import com.fabulousotter.gotr.model.Alignment;
import com.fabulousotter.gotr.model.Altar;
import com.fabulousotter.gotr.model.CellTier;
import com.fabulousotter.gotr.model.CombinationRune;
import com.fabulousotter.gotr.model.PouchType;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.DynamicObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.Widget;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * The plugin calls tick() before snapshot() so polling follows the tick's client events.
 */
@Slf4j
@Singleton
public class StateTracker
{
	static final int TEMPLE_REGION = 14484;
	private static final int HUD_SCRIPT = 5980;
	private static final int HUGE_REMAINS_WEST = 3587;
	private static final int HUGE_REMAINS_EAST = 3594;
	private static final int HUGE_REMAINS_NORTH = 9516;
	private static final int HUGE_REMAINS_SOUTH = 9496;
	private static final int FIRST_PORTAL_SECONDS = 160;
	private static final int PORTAL_INTERVAL_SECONDS = 140;
	private static final int INVENTORY_SIZE = 28;
	private static final int QUEST_REFRESH_TICKS = 100;

	private static final Pattern REWARD_TOTAL = Pattern.compile(
		"Total elemental energy:[^>]+>([\\d,]+).*Total catalytic energy:[^>]+>([\\d,]+)");
	private static final Pattern CHECK_POINTS = Pattern.compile(
		"You have (\\d+) catalytic energy and (\\d+) elemental energy");
	private static final Pattern COUNTDOWN = Pattern.compile("The rift will become active in (\\d+) seconds");

	private static final Set<Integer> RUNE_POUCH_ITEMS = ImmutableSet.of(
		ItemID.BH_RUNE_POUCH,
		ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH,
		ItemID.DIVINE_RUNE_POUCH_TROUVER
	);
	private static final Set<Integer> PARTS_OBJECTS = ImmutableSet.of(
		ObjectID.GOTR_ESSENCE_TIER_1,
		ObjectID.GOTR_ESSENCE_TIER_1B,
		ObjectID.GOTR_ESSENCE_TIER_1_LARGE,
		ObjectID.GOTR_ESSENCE_TIER_1_LARGE_B
	);
	// The timed portal in the temple and its exit inside the huge remains.
	private static final Set<Integer> PORTAL_OBJECTS = ImmutableSet.of(
		ObjectID.GOTR_AGILITY_PORTAL_TOP,
		ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_CHILD,
		ObjectID.GOTR_AGILITY_PORTAL_BOTTOM_PARENT
	);
	private static final Set<Integer> SHORTCUT_OBJECTS = ImmutableSet.of(
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP,
		ObjectID.GOTR_AGILITY_SHORTCUT_MIDDLE,
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM,
		ObjectID.GOTR_AGILITY_SHORTCUT_TOP_NOOP,
		ObjectID.GOTR_AGILITY_SHORTCUT_BOTTOM_NOOP
	);
	private static final Set<Integer> LANDMARK_OBJECTS = ImmutableSet.of(
		ObjectID.GOTR_WORKBENCH,
		ObjectID.GOTR_DEPOSITCHEST,
		ObjectID.GOTR_UNCHARGED_CELLS,
		ObjectID.GOTR_WEAK_CELLS,
		ObjectID.GOTR_GUARDIAN_SPAWN_EAST,
		ObjectID.GOTR_GUARDIAN_SPAWN_WEST,
		ObjectID.GOTR_BARRIER,
		ObjectID.GOTR_BARRIER_CLOSED,
		ObjectID.GOTR_BARRIER_NOENTRY
	);
	private static final Set<Integer> ENTRANCE_BARRIERS = ImmutableSet.of(
		ObjectID.GOTR_BARRIER,
		ObjectID.GOTR_BARRIER_CLOSED,
		ObjectID.GOTR_BARRIER_NOENTRY
	);
	private static final int CLOSING_SECONDS = 30;
	// How far from the entrance barrier or the Rewards Guardian still counts as the lobby.
	private static final int LOBBY_RADIUS = 40;
	// Fallback until a complete intermission has been observed.
	private static final int DEFAULT_BREAK_SECONDS = 120;
	private static final String BREAK_KEY = "learnedBreakSeconds";
	private static final String ELEMENTAL_POINTS_KEY = "savedElementalPoints";
	private static final String CATALYTIC_POINTS_KEY = "savedCatalyticPoints";
	private static final Set<Integer> CORDELIA_NPCS = ImmutableSet.of(NpcID.GOTR_CORDELIA, NpcID.GOTR_CORDELIA_1OP, NpcID.GOTR_CORDELIA_2OPS);
	private static final Set<Integer> BROKEN_TILES = ImmutableSet.of(ObjectID.GOTR_CELL_TILE_BROKEN_REPAIRABLE, ObjectID.GOTR_CELL_TILE_BROKEN);
	private static final Set<Integer> EMPTY_TILES = ImmutableSet.of(ObjectID.GOTR_CELL_TILE_INACTIVE, ObjectID.GOTR_CELL_TILE_INACTIVE_NOOP);

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ConfigManager configManager;

	// HUD script arguments and the tick they were last seen on.
	private int hudElemental;
	private int hudCatalytic;
	private int hudPower;
	private int hudMaxPower;
	private int hudPortalLocation;
	private int hudElementalIndex;
	private int hudCatalyticIndex;
	private int hudGuardians;
	private int hudGuardiansMax = 10;
	private int hudGuardianTicks = -1;
	private int hudPortalTicks = -1;
	private int hudTick = -1;
	private Altar observedElemental;
	private Altar observedCatalytic;
	private boolean guardiansObserved;

	private GamePhase phase = GamePhase.WAITING;
	private int gameStartTick = -1;
	private int startAtTick = -1;
	private int closingTick = -1;
	private int gameEndTick = -1;
	private int learnedBreakSeconds = -1;
	private int lastPortalSpawnTick = -1;
	private boolean anyPortalThisGame;
	private boolean portalOpen;
	private String portalDirection;
	private boolean scanPending;
	private boolean scannedOnce;
	@Getter
	private int sceneVersion;
	private Set<Altar> questLockedAltars = ImmutableSet.of();
	private int questCheckedTick = -1;

	// Scene.
	@Getter
	private final Map<Altar, GameObject> portalGuardians = new EnumMap<>(Altar.class);
	@Getter
	private final Map<Integer, GameObject> landmarks = new HashMap<>();
	@Getter
	private final List<GameObject> partsRemains = new ArrayList<>();
	@Getter
	private final List<GameObject> largeRemains = new ArrayList<>();
	@Getter
	private final List<GameObject> hugeRemains = new ArrayList<>();
	@Getter
	private final List<TileObject> shortcuts = new ArrayList<>();
	// Both portal objects can be loaded at once.
	private GameObject portal;
	private GameObject hugeExitPortal;
	@Getter
	private GameObject altarObject;
	// Some altar rooms contain multiple exits; retain the arrival position for selection.
	private final List<GameObject> altarExits = new ArrayList<>();
	private WorldPoint altarArrival;
	private Location lastLocation = Location.OUTSIDE;
	private Altar altarRoom;
	@Getter
	private NPC greatGuardian;
	// The client only keeps NPCs within about 15 tiles, and the guardian never moves, so its
	// position is remembered from the last spawn for use from the far side of the temple.
	@Getter
	private WorldPoint greatGuardianCentre;
	@Getter
	private int greatGuardianSize;
	// The Rewards Guardian in the waiting area outside the barrier.
	private NPC rewardTrader;
	@Getter
	private NPC cordelia;
	@Getter
	private final Map<WorldPoint, GroundObject> cellTiles = new LinkedHashMap<>();
	@Getter
	private final Map<Integer, NPC> barrierNpcs = new HashMap<>();
	private final Map<WorldPoint, Integer> barrierHealth = new HashMap<>();

	// Inventory and equipment.
	private Item[] inventoryItems;
	private int fragments;
	private int essence;
	private int elementalStones;
	private int catalyticStones;
	private int polyElementalStones;
	private int polyCatalyticStones;
	private CellTier chargedCell;
	private int unchargedCells;
	private boolean chisel;
	private boolean pickaxeInInventory;
	private boolean pickaxeWorn;
	private int freeSlots = INVENTORY_SIZE;
	private final Set<Altar> talismans = new HashSet<>();
	private final Map<Integer, Integer> runeStacks = new HashMap<>();
	private final Map<PouchType, Boolean> pouchesPresent = new EnumMap<>(PouchType.class);
	// Essence added to each pouch since it was last repaired, and the last seen stored/degraded
	// state, so a repair can zero the count.
	private final Map<PouchType, Integer> pouchUsed = new EnumMap<>(PouchType.class);
	private final Map<PouchType, Integer> lastPouchStored = new EnumMap<>(PouchType.class);
	private final Map<PouchType, Boolean> lastPouchDegraded = new EnumMap<>(PouchType.class);
	private boolean bindingNecklaceWorn;
	private boolean runePouch;

	private int savedElementalPoints = -1;
	private int savedCatalyticPoints = -1;
	private boolean pointsCredited;

	private final RateTracker rates = new RateTracker();
	private int lastFragments;
	private int lastEssence;
	private int lastStones;

	@Inject
	StateTracker(Client client, ClientThread clientThread, ItemManager itemManager, ConfigManager configManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.configManager = configManager;
	}

	public void reset()
	{
		resetScene();
		readInventory(null);
		readEquipment(null);
		scanPending = false;
		scannedOnce = false;
		lastLocation = Location.OUTSIDE;
		altarArrival = null;
		phase = GamePhase.WAITING;
		gameStartTick = -1;
		startAtTick = -1;
		closingTick = -1;
		gameEndTick = -1;
		loadBreak();
		pouchUsed.clear();
		lastPouchStored.clear();
		lastPouchDegraded.clear();
		lastPortalSpawnTick = -1;
		anyPortalThisGame = false;
		portalOpen = false;
		portalDirection = null;
		hudTick = -1;
		hudGuardianTicks = -1;
		hudPortalTicks = -1;
		hudMaxPower = 0;
		questCheckedTick = -1;
	}

	private void resetScene()
	{
		sceneVersion++;
		portalGuardians.clear();
		landmarks.clear();
		partsRemains.clear();
		largeRemains.clear();
		hugeRemains.clear();
		shortcuts.clear();
		portal = null;
		hugeExitPortal = null;
		altarObject = null;
		altarExits.clear();
		altarRoom = null;
		greatGuardian = null;
		greatGuardianCentre = null;
		greatGuardianSize = 0;
		rewardTrader = null;
		cordelia = null;
		cellTiles.clear();
		barrierNpcs.clear();
		barrierHealth.clear();
	}

	public void primeFromClient()
	{
		loadBreak();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		readInventory(client.getItemContainer(InventoryID.INV));
		readEquipment(client.getItemContainer(InventoryID.WORN));
		// LOGGED_IN can precede the local player. Defer the initial scan until the next tick.
		if (!scannedOnce)
		{
			scanPending = true;
		}
	}

	// Seed objects when enabled mid-session; subsequent scene loads supply spawn events.
	private void scanScene()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldView wv = player.getWorldView();
		Scene scene = wv.getScene();
		if (scene == null)
		{
			return;
		}
		resetScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = wv.getPlane();
		if (plane < 0 || plane >= tiles.length)
		{
			return;
		}
		for (Tile[] row : tiles[plane])
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}
				GameObject[] objects = tile.getGameObjects();
				if (objects != null)
				{
					for (GameObject object : objects)
					{
						// Multi-tile objects appear on every tile they cover: keep one entry.
						if (object != null && object.getSceneMinLocation().getX() == tile.getSceneLocation().getX()
							&& object.getSceneMinLocation().getY() == tile.getSceneLocation().getY())
						{
							addGameObject(object);
						}
					}
				}
				GroundObject ground = tile.getGroundObject();
				if (ground != null)
				{
					addGroundObject(ground);
				}
				if (tile.getWallObject() != null)
				{
					addShortcutIfRubble(tile.getWallObject());
				}
				if (tile.getDecorativeObject() != null)
				{
					addShortcutIfRubble(tile.getDecorativeObject());
				}
			}
		}
		for (NPC npc : wv.npcs())
		{
			if (npc != null)
			{
				addNpc(npc);
			}
		}
		log.debug("Scene scan: {} shortcuts, {} large remains, {} parts, {} huge, {} guardians, {} landmarks, {} tiles, {} barriers",
			shortcuts.size(), largeRemains.size(), partsRemains.size(), hugeRemains.size(), portalGuardians.size(),
			landmarks.size(), cellTiles.size(), barrierNpcs.size());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				resetScene();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				reset();
				break;
			case LOGGED_IN:
				clientThread.invokeLater(this::primeFromClient);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != HUD_SCRIPT || event.getScriptEvent() == null)
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 12)
		{
			return;
		}
		try
		{
			hudElemental = toInt(args[1]);
			hudCatalytic = toInt(args[2]);
			hudPower = toInt(args[3]);
			hudMaxPower = toInt(args[4]);
			int portalLocation = toInt(args[5]);
			hudElementalIndex = toInt(args[6]);
			hudCatalyticIndex = toInt(args[7]);
			hudGuardians = toInt(args[8]);
			hudGuardiansMax = toInt(args[9]);
			hudGuardianTicks = toInt(args[10]);
			hudPortalTicks = toInt(args[11]);
			hudTick = client.getTickCount();
			if (portalLocation > 0 && hudPortalLocation <= 0)
			{
				lastPortalSpawnTick = hudTick;
				anyPortalThisGame = true;
			}
			hudPortalLocation = portalLocation;
			portalOpen = portalLocation > 0;
			updatePhaseFromHud();
		}
		catch (NumberFormatException ex)
		{
			log.debug("Unparseable HUD args", ex);
		}
	}

	private static int toInt(Object o)
	{
		return o instanceof Integer ? (Integer) o : Integer.parseInt(String.valueOf(o));
	}

	// Recover from missed chat messages or a mid-game enable.
	private void updatePhaseFromHud()
	{
		if (hudMaxPower <= 0)
		{
			return;
		}
		boolean complete = hudPower >= hudMaxPower;
		boolean lost = hudPower == 0 && hudPortalLocation == -1;
		boolean closed = hudPortalLocation == 0 && hudPower == 0 && hudPortalTicks == -1;
		if (complete || lost)
		{
			if (phase == GamePhase.ACTIVE)
			{
				phase = GamePhase.CLOSING;
				closingTick = client.getTickCount();
			}
		}
		else if (closed)
		{
			if (phase == GamePhase.ACTIVE || phase == GamePhase.CLOSING)
			{
				phase = GamePhase.ENDED;
				gameEndTick = client.getTickCount();
			}
		}
		else if ((phase == GamePhase.WAITING || phase == GamePhase.ENDED)
			&& (hudPower > 0 || hudElementalIndex > 0 || hudCatalyticIndex > 0))
		{
			phase = GamePhase.ACTIVE;
			pointsCredited = false;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String msg = event.getMessage();
		int tick = client.getTickCount();
		Matcher countdown = COUNTDOWN.matcher(msg);
		if (countdown.find())
		{
			startAtTick = tick + Integer.parseInt(countdown.group(1)) * 10 / 6;
			if (phase != GamePhase.ACTIVE)
			{
				phase = GamePhase.COUNTDOWN;
			}
			learnBreak(startAtTick);
			return;
		}
		if (msg.contains("The rift becomes active!"))
		{
			learnBreak(tick);
			rates.reset();
			pointsCredited = false;
			phase = GamePhase.ACTIVE;
			gameStartTick = tick;
			startAtTick = -1;
			closingTick = -1;
			gameEndTick = -1;
			lastPortalSpawnTick = -1;
			anyPortalThisGame = false;
			portalOpen = false;
			barrierHealth.clear();
			return;
		}
		if (msg.contains("The Portal Guardians will keep their rifts open for another 30 seconds"))
		{
			phase = GamePhase.CLOSING;
			closingTick = tick;
			return;
		}
		if (msg.contains("You found some loot:"))
		{
			if (savedElementalPoints > 0)
			{
				savedElementalPoints--;
			}
			if (savedCatalyticPoints > 0)
			{
				savedCatalyticPoints--;
			}
			savePoints();
			return;
		}
		Matcher total = REWARD_TOTAL.matcher(msg);
		if (total.find())
		{
			savedElementalPoints = Integer.parseInt(total.group(1).replace(",", ""));
			savedCatalyticPoints = Integer.parseInt(total.group(2).replace(",", ""));
			pointsCredited = true;
			savePoints();
			log.debug("Reward totals E {} / C {}; rewards varp {}", savedElementalPoints, savedCatalyticPoints,
				client.getVarpValue(VarPlayerID.GOTR_REWARDS_PERM));
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		sceneVersion++;
		addGameObject(event.getGameObject());
	}

	private void addGameObject(GameObject object)
	{
		int id = object.getId();
		Altar guardian = Altar.fromPortalGuardian(id);
		if (guardian != null)
		{
			portalGuardians.put(guardian, object);
			return;
		}
		Altar altar = Altar.fromAltarObject(id);
		if (altar != null)
		{
			altarObject = object;
			altarRoom = altar;
			return;
		}
		if (Altar.fromExitPortal(id) != null)
		{
			altarExits.add(object);
			return;
		}
		if (PORTAL_OBJECTS.contains(id))
		{
			if (inHugeRemains(object.getWorldLocation()))
			{
				hugeExitPortal = object;
				return;
			}
			portal = object;
			if (!anyPortalThisGame && phase == GamePhase.ACTIVE)
			{
				lastPortalSpawnTick = client.getTickCount();
				anyPortalThisGame = true;
			}
			return;
		}
		if (addShortcutIfRubble(object))
		{
			return;
		}
		if (PARTS_OBJECTS.contains(id))
		{
			partsRemains.add(object);
		}
		else if (id == ObjectID.GOTR_ESSENCE_TIER_2)
		{
			largeRemains.add(object);
		}
		else if (id == ObjectID.GOTR_ESSENCE_TIER_3 || id == ObjectID.GOTR_FALLEN_GUARDIAN)
		{
			hugeRemains.add(object);
		}
		else if (LANDMARK_OBJECTS.contains(id))
		{
			landmarks.put(id, object);
		}
		else if (isPortalNamed(object))
		{
			altarExits.add(object);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		sceneVersion++;
		GameObject object = event.getGameObject();
		int id = object.getId();
		Altar guardian = Altar.fromPortalGuardian(id);
		if (guardian != null)
		{
			portalGuardians.remove(guardian, object);
			return;
		}
		if (altarObject == object)
		{
			altarObject = null;
			altarRoom = null;
		}
		else if (altarExits.remove(object))
		{
			return;
		}
		else if (portal == object)
		{
			portal = null;
		}
		else if (hugeExitPortal == object)
		{
			hugeExitPortal = null;
		}
		else
		{
			partsRemains.remove(object);
			largeRemains.remove(object);
			hugeRemains.remove(object);
			shortcuts.remove(object);
			landmarks.remove(id, object);
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		sceneVersion++;
		addGroundObject(event.getGroundObject());
	}

	private void addGroundObject(GroundObject object)
	{
		addShortcutIfRubble(object);
		if (isCellTile(object.getId()))
		{
			cellTiles.put(object.getWorldLocation(), object);
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		sceneVersion++;
		GroundObject object = event.getGroundObject();
		shortcuts.remove(object);
		if (isCellTile(object.getId()))
		{
			cellTiles.remove(object.getWorldLocation(), object);
		}
	}

	// Wall and decorative scenery can be the rubble too.
	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		sceneVersion++;
		addShortcutIfRubble(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		sceneVersion++;
		shortcuts.remove(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		sceneVersion++;
		addShortcutIfRubble(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		sceneVersion++;
		shortcuts.remove(event.getDecorativeObject());
	}

	private boolean addShortcutIfRubble(TileObject object)
	{
		int id = object.getId();
		if (!SHORTCUT_OBJECTS.contains(id))
		{
			return false;
		}
		if (!shortcuts.contains(object))
		{
			log.debug("Shortcut object {} at {}", id, object.getWorldLocation());
			shortcuts.add(object);
		}
		return true;
	}

	// Some altar rooms have additional exit objects. Limit the name fallback to GOTR visits.
	private boolean isPortalNamed(GameObject object)
	{
		if (object.getWorldLocation().getRegionID() == TEMPLE_REGION
			|| (phase != GamePhase.ACTIVE && phase != GamePhase.CLOSING && !hudVisible()))
		{
			return false;
		}
		ObjectComposition def = client.getObjectDefinition(object.getId());
		return def != null && "Portal".equals(def.getName());
	}

	// Use the arrival position to avoid switching exits as the player moves.
	@Nullable
	public GameObject getAltarExit()
	{
		if (altarExits.isEmpty())
		{
			return null;
		}
		WorldPoint from = altarArrival;
		if (from == null && client.getLocalPlayer() != null)
		{
			from = client.getLocalPlayer().getWorldLocation();
		}
		GameObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (GameObject exit : altarExits)
		{
			int d = from == null ? 0 : exit.getWorldLocation().distanceTo2D(from);
			if (d < bestDist)
			{
				bestDist = d;
				best = exit;
			}
		}
		return best;
	}

	private static boolean isCellTile(int id)
	{
		return BROKEN_TILES.contains(id) || EMPTY_TILES.contains(id) || CellTier.fromTileObject(id) != null;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		addNpc(event.getNpc());
	}

	private void addNpc(NPC npc)
	{
		int id = npc.getId();
		if (id == NpcID.GOTR_REWARD_TRADER)
		{
			rewardTrader = npc;
		}
		if (id == NpcID.GOTR_GREAT_GUARDIAN)
		{
			greatGuardian = npc;
			greatGuardianCentre = npc.getWorldLocation();
			greatGuardianSize = npc.getComposition() == null ? 1 : Math.max(1, npc.getComposition().getSize());
		}
		else if (CORDELIA_NPCS.contains(id))
		{
			cordelia = npc;
		}
		else if (CellTier.fromBarrierNpc(id) != null)
		{
			barrierNpcs.put(npc.getIndex(), npc);
			barrierHealth.remove(npc.getWorldLocation());
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc == rewardTrader)
		{
			rewardTrader = null;
		}
		if (npc == greatGuardian)
		{
			greatGuardian = null;
		}
		else if (npc == cordelia)
		{
			cordelia = null;
		}
		else if (barrierNpcs.remove(npc.getIndex()) != null)
		{
			barrierHealth.remove(npc.getWorldLocation());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			readEquipment(event.getItemContainer());
		}
	}

	private void readInventory(@Nullable ItemContainer container)
	{
		Item[] items = container == null ? null : container.getItems();
		if (Arrays.equals(inventoryItems, items))
		{
			return;
		}
		inventoryItems = items;
		fragments = 0;
		essence = 0;
		elementalStones = 0;
		catalyticStones = 0;
		polyElementalStones = 0;
		polyCatalyticStones = 0;
		chargedCell = null;
		unchargedCells = 0;
		chisel = false;
		pickaxeInInventory = false;
		runePouch = false;
		talismans.clear();
		runeStacks.clear();
		pouchesPresent.clear();
		int used = 0;
		if (items == null)
		{
			freeSlots = INVENTORY_SIZE;
			return;
		}
		for (Item item : items)
		{
			int id = item.getId();
			int qty = item.getQuantity();
			if (id <= 0 || qty <= 0)
			{
				continue;
			}
			used++;
			switch (id)
			{
				case ItemID.GOTR_GUARDIAN_FRAGMENT:
					fragments += qty;
					continue;
				case ItemID.GOTR_GUARDIAN_ESSENCE:
					essence += qty;
					continue;
				case ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL:
					elementalStones += qty;
					continue;
				case ItemID.GOTR_GUARDIAN_STONE_CATALYTIC:
					catalyticStones += qty;
					continue;
				case ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL:
					polyElementalStones += qty;
					continue;
				case ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC:
					polyCatalyticStones += qty;
					continue;
				case ItemID.GOTR_CELL_UNCHARGED:
					unchargedCells += qty;
					continue;
				case ItemID.CHISEL:
					chisel = true;
					continue;
				default:
					break;
			}
			CellTier cell = CellTier.fromCellItem(id);
			if (cell != null)
			{
				chargedCell = cell;
				continue;
			}
			Altar talisman = Altar.fromTalisman(id);
			if (talisman != null)
			{
				talismans.add(talisman);
				continue;
			}
			PouchType pouch = PouchType.fromItem(id);
			if (pouch != null)
			{
				pouchesPresent.put(pouch, PouchType.isDegradedItem(id));
				continue;
			}
			if (RUNE_POUCH_ITEMS.contains(id))
			{
				runePouch = true;
				continue;
			}
			if (Altar.fromRune(id) != null || CombinationRune.isCombinationRune(id))
			{
				runeStacks.merge(id, qty, Integer::sum);
				continue;
			}
			String name = itemName(id);
			if (name.endsWith(" rune"))
			{
				runeStacks.merge(id, qty, Integer::sum);
			}
			else if (name.contains("pickaxe"))
			{
				pickaxeInInventory = true;
			}
		}
		freeSlots = Math.max(0, INVENTORY_SIZE - used);
	}

	private void readEquipment(@Nullable ItemContainer container)
	{
		bindingNecklaceWorn = false;
		pickaxeWorn = false;
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			int id = item.getId();
			if (id <= 0)
			{
				continue;
			}
			if (id == ItemID.MAGIC_EMERALD_NECKLACE)
			{
				bindingNecklaceWorn = true;
			}
			else if (itemName(id).contains("pickaxe"))
			{
				pickaxeWorn = true;
			}
		}
	}

	private String itemName(int id)
	{
		ItemComposition comp = itemManager.getItemComposition(id);
		return comp.getName() == null ? "" : comp.getName().toLowerCase(Locale.ROOT);
	}

	public void tick()
	{
		int tick = client.getTickCount();
		if (scanPending && client.getLocalPlayer() != null)
		{
			scanPending = false;
			scannedOnce = true;
			scanScene();
		}
		if (locatePlayer() == Location.OUTSIDE)
		{
			return;
		}
		// Poll once after container events, before sampling rates, to recover missed updates.
		readInventory(client.getItemContainer(InventoryID.INV));
		if (questCheckedTick < 0 || tick - questCheckedTick >= QUEST_REFRESH_TICKS)
		{
			refreshQuestLocks();
			questCheckedTick = tick;
		}
		observeRates();
		observeActiveGuardians();
		for (NPC npc : barrierNpcs.values())
		{
			int ratio = npc.getHealthRatio();
			int scale = npc.getHealthScale();
			if (ratio >= 0 && scale > 0)
			{
				barrierHealth.put(npc.getWorldLocation(), ratio * 100 / scale);
			}
		}
		Widget text = client.getWidget(InterfaceID.Messagebox.TEXT);
		if (text != null && text.getText() != null)
		{
			Matcher m = CHECK_POINTS.matcher(text.getText());
			if (m.find())
			{
				savedCatalyticPoints = Integer.parseInt(m.group(1));
				savedElementalPoints = Integer.parseInt(m.group(2));
				savePoints();
			}
		}
		Widget portalText = client.getWidget(InterfaceID.GotrHud.PORTAL_POSITION);
		if (portalText != null && !portalText.isHidden() && portalText.getText() != null)
		{
			portalDirection = portalText.getText().trim();
		}
		else
		{
			portalDirection = null;
		}
		if (phase == GamePhase.COUNTDOWN && startAtTick >= 0 && tick > startAtTick + 20)
		{
			// The start message was missed (chat filtered): treat the game as running.
			phase = GamePhase.ACTIVE;
			gameStartTick = startAtTick;
			startAtTick = -1;
		}
		int closeTick = closingTick >= 0 ? closingTick + CLOSING_SECONDS * 10 / 6 : -1;
		if (phase == GamePhase.CLOSING && closeTick >= 0 && tick > closeTick + 10)
		{
			// The rifts have shut whether or not the HUD showed it: the round is over and its
			// points have been credited.
			phase = GamePhase.ENDED;
			gameEndTick = closeTick;
		}
	}

	// Quest.getState runs a client script; cache the results.
	private void refreshQuestLocks()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Set<Altar> locked = new HashSet<>();
		for (Altar altar : Altar.values())
		{
			Quest quest = altar.getRequiredQuest();
			if (quest != null && quest.getState(client) != QuestState.FINISHED)
			{
				locked.add(altar);
			}
		}
		questLockedAltars = ImmutableSet.copyOf(locked);
	}

	// Object animations update before the HUD script on altar rotations.
	private void observeActiveGuardians()
	{
		Altar elemental = null;
		Altar catalytic = null;
		boolean any = false;
		for (Map.Entry<Altar, GameObject> entry : portalGuardians.entrySet())
		{
			Renderable renderable = entry.getValue().getRenderable();
			if (!(renderable instanceof DynamicObject))
			{
				continue;
			}
			any = true;
			Animation anim = ((DynamicObject) renderable).getAnimation();
			if (anim != null && anim.getId() == AnimationID.STATUE_RUNESTONE01_ACTIVE)
			{
				if (entry.getKey().getAlignment() == Alignment.ELEMENTAL)
				{
					elemental = entry.getKey();
				}
				else
				{
					catalytic = entry.getKey();
				}
			}
		}
		guardiansObserved = any;
		observedElemental = elemental;
		observedCatalytic = catalytic;
	}

	// Ignore negative inventory deltas caused by pouch transfers.
	private void observeRates()
	{
		Player player = client.getLocalPlayer();
		int fragDelta = fragments - lastFragments;
		int essDelta = essence - lastEssence;
		lastFragments = fragments;
		lastEssence = essence;
		if (player == null)
		{
			return;
		}
		int stones = elementalStones + catalyticStones + polyElementalStones + polyCatalyticStones;
		if (stones < lastStones)
		{
			rates.noteOwnHandIn(client.getTickCount());
		}
		lastStones = stones;
		if (hudTick >= 0 && hudMaxPower > 0 && phase == GamePhase.ACTIVE)
		{
			rates.samplePower(client.getTickCount(), hudPower);
		}
		RateTracker.Activity activity = RateTracker.Activity.NONE;
		if (player.getAnimation() != -1)
		{
			WorldPoint at = player.getWorldLocation();
			if (near(at, landmarks.get(ObjectID.GOTR_WORKBENCH), 3))
			{
				activity = RateTracker.Activity.CRAFTING_ESSENCE;
			}
			else if (nearAny(at, hugeRemains, 4))
			{
				activity = RateTracker.Activity.MINING_HUGE;
			}
			else if (nearAny(at, largeRemains, 3))
			{
				activity = RateTracker.Activity.MINING_LARGE;
			}
			else if (nearAny(at, partsRemains, 3))
			{
				activity = RateTracker.Activity.MINING_PARTS;
			}
		}
		rates.tick(activity, fragDelta, essDelta);
	}

	private static boolean near(WorldPoint at, @Nullable GameObject object, int tiles)
	{
		return object != null && object.getWorldLocation().distanceTo2D(at) <= tiles + 1;
	}

	private static boolean nearAny(WorldPoint at, List<GameObject> objects, int tiles)
	{
		for (GameObject object : objects)
		{
			if (near(at, object, tiles))
			{
				return true;
			}
		}
		return false;
	}

	public Snapshot snapshot()
	{
		Player player = client.getLocalPlayer();
		WorldPoint where = player == null ? null : player.getWorldLocation();
		Location location = locate(where);
		if (location == Location.OUTSIDE)
		{
			lastLocation = location;
			return Snapshot.OUTSIDE;
		}
		if (location == Location.ALTAR_ROOM && lastLocation != Location.ALTAR_ROOM)
		{
			altarArrival = where;
		}
		lastLocation = location;
		int tick = client.getTickCount();
		int rc = client.getBoostedSkillLevel(Skill.RUNECRAFT);

		int elemental = hudTick >= 0 ? hudElemental : client.getVarbitValue(VarbitID.GOTR_ELEMENTAL_EARNED_THIS_GAME);
		int catalytic = hudTick >= 0 ? hudCatalytic : client.getVarbitValue(VarbitID.GOTR_CATALYTIC_EARNED_THIS_GAME);

		List<PouchState> pouches = new ArrayList<>();
		for (Map.Entry<PouchType, Boolean> entry : pouchesPresent.entrySet())
		{
			PouchType type = entry.getKey();
			boolean degraded = entry.getValue();
			pouches.add(new PouchState(type, client.getVarbitValue(type.getVarbitId()), type.capacity(rc, degraded), degraded));
		}

		// Track essence fed to each pouch since it was last repaired, so the next-game degrade can
		// be predicted. A repair is the Dark Mage's dialogue line, or a degraded pouch turning
		// intact; the cast animation is deliberately not used, as the contact can fail.
		boolean pouchRepaired = darkMageRepairResponse();
		int pouchUsesLeft = -1;
		int pouchWorstCapacity = 0;
		for (PouchState pouch : pouches)
		{
			PouchType type = pouch.getType();
			boolean wasDegraded = lastPouchDegraded.getOrDefault(type, pouch.isDegraded());
			int previousStored = lastPouchStored.getOrDefault(type, pouch.getStored());
			boolean justRepaired = pouchRepaired || (wasDegraded && !pouch.isDegraded());
			int used = justRepaired ? 0
				: Math.max(0, pouchUsed.getOrDefault(type, 0) + Math.max(0, pouch.getStored() - previousStored));
			pouchUsed.put(type, used);
			lastPouchStored.put(type, pouch.getStored());
			lastPouchDegraded.put(type, pouch.isDegraded());
			int limit = type.getDegradeEssence();
			if (limit > 0 && !pouch.isDegraded())
			{
				int left = limit - used;
				if (pouchUsesLeft < 0 || left < pouchUsesLeft)
				{
					pouchUsesLeft = left;
					pouchWorstCapacity = pouch.getCapacity();
				}
			}
		}

		List<BarrierState> barriers = new ArrayList<>();
		for (NPC npc : barrierNpcs.values())
		{
			CellTier tier = CellTier.fromBarrierNpc(npc.getId());
			WorldPoint at = npc.getWorldLocation();
			barriers.add(new BarrierState(cellTileNear(at), tier, barrierHealth.getOrDefault(at, -1)));
		}
		List<WorldPoint> emptyTiles = new ArrayList<>();
		List<WorldPoint> brokenTiles = new ArrayList<>();
		for (GroundObject tile : cellTiles.values())
		{
			if (EMPTY_TILES.contains(tile.getId()))
			{
				emptyTiles.add(tile.getWorldLocation());
			}
			else if (BROKEN_TILES.contains(tile.getId()))
			{
				brokenTiles.add(tile.getWorldLocation());
			}
		}
		Map<Altar, WorldPoint> guardianPoints = new EnumMap<>(Altar.class);
		for (Map.Entry<Altar, GameObject> entry : portalGuardians.entrySet())
		{
			guardianPoints.put(entry.getKey(), entry.getValue().getWorldLocation());
		}

		int depositable = 0;
		int baseCount = 0;
		Altar baseRune = baseRuneCarried();
		for (Map.Entry<Integer, Integer> stack : runeStacks.entrySet())
		{
			if (baseRune != null && stack.getKey() == baseRune.getRuneItemId())
			{
				baseCount = stack.getValue();
			}
			else
			{
				depositable++;
			}
		}

		int sinceStart = phase == GamePhase.ACTIVE && gameStartTick >= 0 ? ticksToSeconds(tick - gameStartTick) : -1;
		int toStart = phase == GamePhase.COUNTDOWN && startAtTick >= 0 ? Math.max(0, ticksToSeconds(startAtTick - tick)) : -1;
		int toNextGame = secondsToNextGame(tick, toStart);
		int sinceLastPortal = lastPortalSpawnTick >= 0 ? ticksToSeconds(tick - lastPortalSpawnTick) : -1;
		int toNextPortal = -1;
		if (phase == GamePhase.ACTIVE && !portalOpen)
		{
			if (!anyPortalThisGame && sinceStart >= 0)
			{
				toNextPortal = Math.max(0, FIRST_PORTAL_SECONDS - sinceStart);
			}
			else if (sinceLastPortal >= 0)
			{
				toNextPortal = Math.max(0, PORTAL_INTERVAL_SECONDS - sinceLastPortal);
			}
		}
		int hudAge = hudTick >= 0 ? tick - hudTick : 0;
		int altarSeconds = hudGuardianTicks >= 0 ? Math.max(0, ticksToSeconds(hudGuardianTicks - hudAge)) : -1;
		int portalSeconds = portalOpen && hudPortalTicks >= 0 ? Math.max(0, ticksToSeconds(hudPortalTicks - hudAge)) : -1;
		boolean useObserved = guardiansObserved && location == Location.TEMPLE;

		return Snapshot.builder()
			.location(location)
			.altarRoom(location == Location.ALTAR_ROOM ? altarRoom : null)
			.playerLocation(where)
			.phase(phase)
			.secondsToStart(toStart)
			.secondsSinceStart(sinceStart)
			.secondsToNextGame(toNextGame)
			.elementalEnergy(elemental)
			.catalyticEnergy(catalytic)
			.power(hudPower)
			.maxPower(hudMaxPower)
			.activeElemental(useObserved ? observedElemental : Altar.fromHud(Alignment.ELEMENTAL, hudElementalIndex))
			.activeCatalytic(useObserved ? observedCatalytic : Altar.fromHud(Alignment.CATALYTIC, hudCatalyticIndex))
			.altarSecondsRemaining(altarSeconds)
			// The portal object remains authoritative after HUD updates stop.
			.portalOpen(portalOpen || portal != null)
			.portalSecondsRemaining(portalSeconds)
			.portalDirection(portalDirection)
			.secondsSinceLastPortal(sinceLastPortal)
			.secondsToNextPortal(toNextPortal)
			.anyPortalThisGame(anyPortalThisGame)
			.guardiansActive(hudGuardians)
			.guardiansMax(hudGuardiansMax > 0 ? hudGuardiansMax : 10)
			.fragments(fragments)
			.essence(essence)
			.elementalStones(elementalStones)
			.catalyticStones(catalyticStones)
			.polyElementalStones(polyElementalStones)
			.polyCatalyticStones(polyCatalyticStones)
			.chargedCell(chargedCell)
			.unchargedCells(unchargedCells)
			.chisel(chisel)
			.pickaxe(pickaxeInInventory || pickaxeWorn)
			.freeSlots(freeSlots)
			.talismans(ImmutableSet.copyOf(talismans))
			.depositableRunes(depositable)
			.baseRune(baseRune)
			.baseRuneCount(baseCount)
			.pouches(pouches)
			.pouchUsesLeft(pouchUsesLeft)
			.pouchWorstCapacity(pouchWorstCapacity)
			.bindingNecklaceWorn(bindingNecklaceWorn)
			.necklaceCharges(client.getVarpValue(VarPlayerID.NECKLACE_OF_BINDING))
			.magicImbueActive(client.getVarbitValue(VarbitID.MAGIC_IMBUE_ACTIVE) > 0)
			.lunarSpellbook(client.getVarbitValue(VarbitID.SPELLBOOK) == 2)
			.runePouch(runePouch)
			.runecraftLevel(rc)
			.agilityLevel(client.getRealSkillLevel(Skill.AGILITY))
			.magicLevel(client.getBoostedSkillLevel(Skill.MAGIC))
			.questLockedAltars(questLockedAltars)
			.barriers(barriers)
			.emptyTiles(emptyTiles)
			.brokenTiles(brokenTiles)
			.portalGuardians(guardianPoints)
			.weakCellTablePresent(landmarks.containsKey(ObjectID.GOTR_WEAK_CELLS))
			.savedElementalPoints(savedElementalPoints)
			.pointsCredited(pointsCredited)
			.savedCatalyticPoints(savedCatalyticPoints)
			.fragmentsPerTick(rates.largePerTick())
			.partsPerTick(rates.partsPerTick())
			.craftPerTick(rates.craftPerTick())
			.hugePerTick(rates.hugePerTick())
			.secondsToClose(phase == GamePhase.ACTIVE ? rates.secondsToClose(hudPower, hudMaxPower) : -1)
			.build();
	}

	// The Dark Mage's line confirming a pouch repair in the NPC Contact dialogue.
	private boolean darkMageRepairResponse()
	{
		Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
		return text != null && text.getText() != null && text.getText().contains("transfiguration spell");
	}

	@Nullable
	public GameObject getPortal()
	{
		Player player = client.getLocalPlayer();
		if (player != null && inHugeRemains(player.getWorldLocation()) && hugeExitPortal != null)
		{
			return hugeExitPortal;
		}
		return portal;
	}

	private Altar baseRuneCarried()
	{
		int air = runeStacks.getOrDefault(Altar.AIR.getRuneItemId(), 0);
		int water = runeStacks.getOrDefault(Altar.WATER.getRuneItemId(), 0);
		return water > air ? Altar.WATER : Altar.AIR;
	}

	private int endTick()
	{
		if (gameEndTick >= 0)
		{
			return gameEndTick;
		}
		return closingTick >= 0 ? closingTick + CLOSING_SECONDS * 10 / 6 : -1;
	}

	private void loadBreak()
	{
		Integer saved = configManager.getConfiguration(GotrAutopilotConfig.GROUP, BREAK_KEY, Integer.class);
		learnedBreakSeconds = saved == null ? -1 : saved;
		// Banked points are only announced at the end of a game or by the Rewards Guardian, so
		// the last known totals carry over between sessions.
		Integer elemental = configManager.getConfiguration(GotrAutopilotConfig.GROUP, ELEMENTAL_POINTS_KEY, Integer.class);
		Integer catalytic = configManager.getConfiguration(GotrAutopilotConfig.GROUP, CATALYTIC_POINTS_KEY, Integer.class);
		if (elemental != null && catalytic != null)
		{
			savedElementalPoints = elemental;
			savedCatalyticPoints = catalytic;
		}
	}

	private void savePoints()
	{
		configManager.setConfiguration(GotrAutopilotConfig.GROUP, ELEMENTAL_POINTS_KEY, savedElementalPoints);
		configManager.setConfiguration(GotrAutopilotConfig.GROUP, CATALYTIC_POINTS_KEY, savedCatalyticPoints);
	}

	private int breakSeconds()
	{
		return learnedBreakSeconds > 0 ? learnedBreakSeconds : DEFAULT_BREAK_SECONDS;
	}

	// Persist one intermission measurement per cycle.
	private void learnBreak(int startTick)
	{
		int end = endTick();
		if (end < 0 || startTick <= end)
		{
			return;
		}
		int seconds = ticksToSeconds(startTick - end);
		if (seconds != learnedBreakSeconds)
		{
			learnedBreakSeconds = seconds;
			configManager.setConfiguration(GotrAutopilotConfig.GROUP, BREAK_KEY, seconds);
			log.debug("Learned break between games: {}s", seconds);
		}
		closingTick = -1;
		gameEndTick = -1;
	}

	// Returns -1 if neither a countdown nor a close estimate is available.
	private int secondsToNextGame(int tick, int toStart)
	{
		switch (phase)
		{
			case COUNTDOWN:
				return toStart;
			case ACTIVE:
			{
				int toClose = rates.secondsToClose(hudPower, hudMaxPower);
				return toClose >= 0 ? toClose + breakSeconds() : -1;
			}
			case CLOSING:
				if (closingTick < 0)
				{
					return -1;
				}
				return Math.max(0, CLOSING_SECONDS - ticksToSeconds(tick - closingTick)) + breakSeconds();
			default:
			{
				int end = endTick();
				return end >= 0 ? Math.max(0, breakSeconds() - ticksToSeconds(tick - end)) : -1;
			}
		}
	}

	@Nullable
	/**
	 * The cell tile a barrier stands on. A wide barrier NPC reports its south-west corner,
	 * which is one tile along the wall from the tile the cell goes on, so the nearest known
	 * tile is used and the NPC's own location only when none is loaded nearby.
	 */
	private WorldPoint cellTileNear(WorldPoint npcAt)
	{
		WorldPoint best = npcAt;
		int bestDist = 3;
		for (WorldPoint tile : cellTiles.keySet())
		{
			int d = tile.distanceTo2D(npcAt);
			if (d < bestDist)
			{
				bestDist = d;
				best = tile;
			}
		}
		return best;
	}

	public GameObject getEntranceBarrier()
	{
		for (int id : ENTRANCE_BARRIERS)
		{
			GameObject barrier = landmarks.get(id);
			if (barrier != null)
			{
				return barrier;
			}
		}
		return null;
	}

	private boolean inLobby(WorldPoint where)
	{
		GameObject barrier = getEntranceBarrier();
		if (barrier == null)
		{
			return false;
		}
		int barrierY = barrier.getWorldLocation().getY();
		boolean templeNorth = greatGuardian == null || greatGuardian.getWorldLocation().getY() > barrierY;
		return templeNorth ? where.getY() < barrierY : where.getY() > barrierY;
	}

	private static boolean inHugeRemains(@Nullable WorldPoint where)
	{
		return where != null && where.getRegionID() == TEMPLE_REGION
			&& where.getX() > HUGE_REMAINS_WEST && where.getX() < HUGE_REMAINS_EAST
			&& where.getY() > HUGE_REMAINS_SOUTH && where.getY() < HUGE_REMAINS_NORTH;
	}

	private Location locate(@Nullable WorldPoint where)
	{
		if (where == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return Location.OUTSIDE;
		}
		if (where.getRegionID() == TEMPLE_REGION)
		{
			if (inHugeRemains(where))
			{
				return Location.HUGE_REMAINS;
			}
			return inLobby(where) ? Location.LOBBY : Location.TEMPLE;
		}
		// Shared altar scenes require a GOTR HUD or an active round.
		if (altarObject != null && altarRoom != null && (hudVisible() || phase == GamePhase.ACTIVE || phase == GamePhase.CLOSING))
		{
			return Location.ALTAR_ROOM;
		}
		// The waiting area by the Rewards Guardian lies outside the minigame region but is
		// part of the same scene: near the entrance barrier or the trader counts as the lobby.
		GameObject barrier = getEntranceBarrier();
		if (barrier != null && where.distanceTo2D(barrier.getWorldLocation()) <= LOBBY_RADIUS)
		{
			return Location.LOBBY;
		}
		if (rewardTrader != null && where.distanceTo2D(rewardTrader.getWorldLocation()) <= LOBBY_RADIUS)
		{
			return Location.LOBBY;
		}
		return Location.OUTSIDE;
	}

	private Location locatePlayer()
	{
		Player player = client.getLocalPlayer();
		return locate(player == null ? null : player.getWorldLocation());
	}

	private boolean hudVisible()
	{
		Widget hud = client.getWidget(InterfaceID.GotrHud.CONTENT);
		return hud != null && !hud.isHidden();
	}

	private static int ticksToSeconds(int ticks)
	{
		return (int) Math.round(ticks * 0.6);
	}
}
