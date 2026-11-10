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
import com.fabulousotter.gotr.plan.Target;
import com.fabulousotter.gotr.plan.Urgency;
import com.fabulousotter.gotr.state.GamePhase;
import com.fabulousotter.gotr.state.GotrWorlds;
import com.fabulousotter.gotr.state.Location;
import com.fabulousotter.gotr.state.Snapshot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import com.fabulousotter.gotr.model.Altar;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

@Slf4j
public class SceneOverlay extends Overlay
{
	private static final Stroke PATH_STROKE = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke CLIMB_STROKE = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
		new float[]{8f, 8f}, 0f);
	private static final int PATH_ALPHA = 160;
	private static final int PATH_RESYNC_TILES = 2;
	private static final int TILE_LABEL_HEIGHT = 40;
	private static final int OBJECT_LABEL_HEIGHT = 120;
	private static final int LOBBY_INFO_HEIGHT = 220;
	// Height above a portal guardian for its rune sprite, clear of the model.
	private static final int RUNE_ICON_HEIGHT = 260;

	private final Client client;
	private final GotrAutopilotPlugin plugin;
	private final GotrAutopilotConfig config;
	private final ModelOutlineRenderer modelOutlineRenderer;
	private final ItemManager itemManager;
	// Rune sprites by altar, fetched once and drawn over the active portal guardians.
	private final Map<Altar, BufferedImage> runeImages = new EnumMap<>(Altar.class);
	private final Map<Altar, BufferedImage> talismanImages = new EnumMap<>(Altar.class);

	@Inject
	SceneOverlay(Client client, GotrAutopilotPlugin plugin, GotrAutopilotConfig config, ModelOutlineRenderer modelOutlineRenderer,
		ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Instruction instruction = plugin.getInstruction();
		if (instruction.getStep() == Step.NOT_IN_GAME)
		{
			return null;
		}
		TileObject countdownAt = drawStartCountdown(graphics, instruction);
		drawSafeToLeave(graphics);
		drawLobbyWorldInfo(graphics);
		drawActiveAltarRunes(graphics);
		if (instruction.getTarget() == Target.NONE)
		{
			return null;
		}
		Color color = instruction.getUrgency() == Urgency.HIGH ? config.urgentColor() : config.highlightColor();
		drawPath(graphics, plugin.getPath(), color);
		NPC npc = plugin.getTargetNpc();
		if (npc != null)
		{
			modelOutlineRenderer.drawOutline(npc, 3, color, 3);
			if (config.showTargetLabel())
			{
				label(graphics, npc.getLocalLocation(), instruction.getHeadline(), color, npc.getLogicalHeight() + 40);
			}
			return null;
		}
		TileObject object = plugin.getTargetObject();
		if (object != null)
		{
			outline(object, color);
			if (object != countdownAt)
			{
				int height = object instanceof GroundObject ? TILE_LABEL_HEIGHT : OBJECT_LABEL_HEIGHT;
				if (config.showTargetLabel())
				{
					label(graphics, object.getLocalLocation(), instruction.getHeadline(), color, height);
				}
			}
			return null;
		}
		if (instruction.getLocation() != null)
		{
			LocalPoint lp = LocalPoint.fromWorld(client, instruction.getLocation());
			if (lp != null)
			{
				Polygon poly = Perspective.getCanvasTilePoly(client, lp);
				if (poly != null)
				{
					OverlayUtil.renderPolygon(graphics, poly, color);
				}
				if (config.showTargetLabel())
				{
					label(graphics, lp, instruction.getHeadline(), color, TILE_LABEL_HEIGHT);
				}
			}
		}
		return null;
	}

	// The rune of each open altar over its portal guardian, so the pair can be read from the
	// floor without the HUD.
	private void drawActiveAltarRunes(Graphics2D graphics)
	{
		Snapshot s = plugin.getSnapshot();
		if (s.getPhase() != GamePhase.ACTIVE)
		{
			return;
		}
		drawRuneOver(graphics, s.getActiveElemental(), s.getTalismans());
		drawRuneOver(graphics, s.getActiveCatalytic(), s.getTalismans());
		// A talisman in the bag opens its altar regardless of the rotation: it takes the rune's place.
		for (Altar altar : s.getTalismans())
		{
			drawOverGuardian(graphics, altar, talismanImages.computeIfAbsent(altar, a -> itemManager.getImage(a.getTalismanItemId())));
		}
	}

	private void drawRuneOver(Graphics2D graphics, @Nullable Altar altar, Set<Altar> talismans)
	{
		if (altar == null || talismans.contains(altar))
		{
			return;
		}
		drawOverGuardian(graphics, altar, runeImages.computeIfAbsent(altar, a -> itemManager.getImage(a.getRuneItemId())));
	}

	private void drawOverGuardian(Graphics2D graphics, Altar altar, @Nullable BufferedImage image)
	{
		GameObject guardian = plugin.getTracker().getPortalGuardians().get(altar);
		if (guardian == null || image == null)
		{
			return;
		}
		Point p = Perspective.getCanvasImageLocation(client, guardian.getLocalLocation(), image, RUNE_ICON_HEIGHT);
		if (p != null)
		{
			OverlayUtil.renderImageLocation(graphics, p, image);
		}
	}

	private void drawSafeToLeave(Graphics2D graphics)
	{
		Snapshot s = plugin.getSnapshot();
		// Only while still inside: once through the barrier the question is answered.
		// Points are credited when the rift closes, so a finished round counts even when the
		// total message was filtered out of chat.
		boolean credited = s.isPointsCredited() || s.getPhase() == GamePhase.ENDED;
		if (!credited || s.getPhase() == GamePhase.ACTIVE || s.getLocation() != Location.TEMPLE)
		{
			return;
		}
		GameObject barrier = plugin.getTracker().getEntranceBarrier();
		if (barrier != null)
		{
			label(graphics, barrier.getLocalLocation(), "Safe to exit", config.highlightColor(), OBJECT_LABEL_HEIGHT);
		}
	}

	// Over the entrance barrier from the lobby: whether this world has a game on, and the
	// Guardians of the Rift worlds worth hopping to.
	private void drawLobbyWorldInfo(Graphics2D graphics)
	{
		Snapshot s = plugin.getSnapshot();
		if (!config.lobbyWorldInfo() || s.getLocation() != Location.LOBBY)
		{
			return;
		}
		GameObject barrier = plugin.getTracker().getEntranceBarrier();
		if (barrier == null)
		{
			return;
		}
		GotrWorlds worlds = plugin.getGotrWorlds();
		int here = client.getWorld();
		List<String> lines = new ArrayList<>();
		lines.add(gameStatus(s));
		GotrWorlds.Entry ping = worlds.lowestPing();
		GotrWorlds.Entry busy = worlds.mostPlayers();
		if (ping == null && busy == null)
		{
			lines.add("Checking GOTR worlds...");
		}
		else
		{
			if (!worlds.isGotrWorld(here))
			{
				lines.add("W" + here + " is not a GOTR world");
			}
			if (ping != null)
			{
				lines.add("Lowest ping: W" + ping.getId() + " (" + ping.getPing() + " ms)" + (ping.getId() == here ? " - here" : ""));
			}
			if (busy != null)
			{
				lines.add("Most players: W" + busy.getId() + " (" + busy.getPlayers() + ")" + (busy.getId() == here ? " - here" : ""));
			}
		}
		// The block grows upwards from its last line, clear of the start countdown below it.
		String last = lines.get(lines.size() - 1);
		Point bottom = Perspective.getCanvasTextLocation(client, graphics, barrier.getLocalLocation(), last, LOBBY_INFO_HEIGHT);
		if (bottom == null)
		{
			return;
		}
		int lineHeight = graphics.getFontMetrics().getHeight();
		int centreX = bottom.getX() + graphics.getFontMetrics().stringWidth(last) / 2;
		for (int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);
			int y = bottom.getY() - (lines.size() - 1 - i) * lineHeight;
			Point p = new Point(centreX - graphics.getFontMetrics().stringWidth(line) / 2, y);
			OverlayUtil.renderTextLocation(graphics, p, line, i == 0 ? config.highlightColor() : Color.WHITE);
		}
	}

	private static String gameStatus(Snapshot s)
	{
		int next = s.getSecondsToNextGame();
		String when = next >= 0 ? String.format(" - next in ~%d:%02d", next / 60, next % 60) : "";
		// Just after a hop the phase is unknown until the HUD or chat says, but the portal
		// guardian statues in the temple show an open rift from the lobby.
		if (s.getPhase() == GamePhase.WAITING && next < 0 && s.getRiftActiveSeen() != null)
		{
			return s.getRiftActiveSeen() ? "Game in progress" : "No game in progress";
		}
		switch (s.getPhase())
		{
			case ACTIVE:
			case CLOSING:
				return "Game in progress" + when;
			case COUNTDOWN:
				return s.getSecondsToStart() >= 0 ? "Game starting in " + s.getSecondsToStart() + "s" : "Game starting soon";
			case ENDED:
				return "No game in progress" + when;
			default:
				return next >= 0 ? "No game in progress" + when : "No game seen yet on this world";
		}
	}

	/**
	 * The client throws from inside the outline when an object's model is gone, as with an object
	 * of a scene just left (the plugin drops those when the scene loads). Should one slip through,
	 * that frame goes without the outline, but keeps the label and leaves no warning in the log.
	 */
	private void outline(TileObject object, Color color)
	{
		try
		{
			modelOutlineRenderer.drawOutline(object, 3, color, 3);
		}
		catch (NullPointerException ex)
		{
			log.debug("No outline this frame for object {} at {}", object.getId(), object.getWorldLocation());
		}
	}

	@Nullable
	private TileObject drawStartCountdown(Graphics2D graphics, Instruction instruction)
	{
		Snapshot s = plugin.getSnapshot();
		TileObject object = plugin.getCountdownObject();
		if (object == null || s.getPhase() != GamePhase.COUNTDOWN || s.getSecondsToStart() < 0)
		{
			return null;
		}
		String text = "Starts in " + s.getSecondsToStart() + "s";
		if (object == plugin.getTargetObject())
		{
			text = config.showTargetLabel() ? instruction.getHeadline() + " - " + text : text;
		}
		Color color = s.getSecondsToStart() <= 5 ? config.urgentColor() : config.highlightColor();
		label(graphics, object.getLocalLocation(), text, color, OBJECT_LABEL_HEIGHT);
		return object;
	}

	private void drawPath(Graphics2D graphics, Pathfinder.Path path, Color color)
	{
		Player player = client.getLocalPlayer();
		if (path.isEmpty() || player == null)
		{
			return;
		}
		List<WorldPoint> points = path.getPoints();
		WorldPoint here = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		if (here != null && withinOneTile(here, points.get(points.size() - 1)))
		{
			return;
		}
		Stroke old = graphics.getStroke();
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), PATH_ALPHA));
		int plane = player.getWorldView().getPlane();
		Point prev = Perspective.localToCanvas(client, player.getLocalLocation(), plane);
		int start = resyncIndex(points, path.getClimbs(), here);
		for (int i = start; i < points.size(); i++)
		{
			WorldPoint wp = points.get(i);
			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				break;
			}
			Point p = Perspective.localToCanvas(client, lp, wp.getPlane());
			if (p != null && prev != null)
			{
				// Only the unskipped first leg can be a player-to-waypoint climb.
				boolean climb = i == start ? start == 0 && path.getClimbs().contains(-1) : path.getClimbs().contains(i - 1);
				graphics.setStroke(climb ? CLIMB_STROKE : PATH_STROKE);
				graphics.drawLine(prev.getX(), prev.getY(), p.getX(), p.getY());
			}
			prev = p;
		}
		graphics.setStroke(old);
	}

	private static boolean withinOneTile(WorldPoint a, WorldPoint b)
	{
		return a.getPlane() == b.getPlane() && Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1;
	}

	// Skip completed walking legs when client movement has advanced beyond the tick snapshot.
	private static int resyncIndex(List<WorldPoint> points, Set<Integer> climbs, @Nullable WorldPoint here)
	{
		if (here == null)
		{
			return 0;
		}
		int start = 0;
		double nearest = PATH_RESYNC_TILES;
		for (int k = 0; k + 1 < points.size(); k++)
		{
			if (climbs.contains(k) || climbs.contains(k - 1))
			{
				continue;
			}
			double d = distanceToLeg(here, points.get(k), points.get(k + 1));
			if (d <= nearest)
			{
				nearest = d;
				start = k + 1;
			}
		}
		return start;
	}

	private static double distanceToLeg(WorldPoint p, WorldPoint a, WorldPoint b)
	{
		double abx = b.getX() - a.getX();
		double aby = b.getY() - a.getY();
		double apx = p.getX() - a.getX();
		double apy = p.getY() - a.getY();
		double len = abx * abx + aby * aby;
		double t = len == 0 ? 0 : Math.max(0, Math.min(1, (apx * abx + apy * aby) / len));
		double dx = apx - t * abx;
		double dy = apy - t * aby;
		return Math.sqrt(dx * dx + dy * dy);
	}

	private void label(Graphics2D graphics, @Nullable LocalPoint at, String text, Color color, int height)
	{
		if (at == null)
		{
			return;
		}
		Point p = Perspective.getCanvasTextLocation(client, graphics, at, text, height);
		if (p != null)
		{
			OverlayUtil.renderTextLocation(graphics, p, text, color);
		}
	}
}
