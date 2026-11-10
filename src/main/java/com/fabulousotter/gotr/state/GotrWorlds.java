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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

/**
 * The official Guardians of the Rift worlds, with their player counts and your ping to each,
 * so the lobby can suggest where to hop. Pings block, so they run on the executor and the
 * last result is read from the client thread.
 */
@Slf4j
@Singleton
public class GotrWorlds
{
	private static final String ACTIVITY = "Guardians of the Rift";
	private static final int REFRESH_MILLIS = 90_000;

	@Value
	public static class Entry
	{
		int id;
		int players;
		/** Round-trip in ms, or -1 when the ping failed. */
		int ping;
	}

	private final Client client;
	private final WorldService worldService;
	private final ScheduledExecutorService executor;

	private volatile List<Entry> entries = new ArrayList<>();
	private volatile boolean refreshing;
	private long lastRefresh;

	@Inject
	GotrWorlds(Client client, WorldService worldService, ScheduledExecutorService executor)
	{
		this.client = client;
		this.worldService = worldService;
		this.executor = executor;
	}

	/** Starts a background refresh when the last one is stale. Call from the client thread. */
	public void refreshIfStale()
	{
		long now = System.currentTimeMillis();
		if (refreshing || now - lastRefresh < REFRESH_MILLIS)
		{
			return;
		}
		WorldResult result = worldService.getWorlds();
		if (result == null)
		{
			return;
		}
		Set<String> mine = client.getWorldType().stream().map(Enum::name).collect(Collectors.toSet());
		List<World> candidates = result.getWorlds().stream()
			.filter(w -> w.getActivity() != null && w.getActivity().contains(ACTIVITY))
			.filter(w -> joinable(w.getTypes(), mine))
			.collect(Collectors.toList());
		refreshing = true;
		lastRefresh = now;
		executor.execute(() ->
		{
			try
			{
				List<Entry> out = new ArrayList<>();
				for (World w : candidates)
				{
					out.add(new Entry(w.getId(), w.getPlayers(), Ping.ping(w)));
				}
				entries = out;
			}
			catch (RuntimeException e)
			{
				log.debug("GOTR world refresh failed", e);
			}
			finally
			{
				refreshing = false;
			}
		});
	}

	// A world is joinable when every restriction it has, other than members, is one the
	// current world has too (a hardcore GOTR world only for those already on one).
	private static boolean joinable(EnumSet<WorldType> types, Set<String> mine)
	{
		for (WorldType t : types)
		{
			if (t != WorldType.MEMBERS && !mine.contains(t.name()))
			{
				return false;
			}
		}
		return true;
	}

	public boolean isGotrWorld(int world)
	{
		return entries.stream().anyMatch(e -> e.getId() == world);
	}

	@Nullable
	public Entry lowestPing()
	{
		return entries.stream().filter(e -> e.getPing() >= 0).min(Comparator.comparingInt(Entry::getPing)).orElse(null);
	}

	@Nullable
	public Entry mostPlayers()
	{
		return entries.stream().max(Comparator.comparingInt(Entry::getPlayers)).orElse(null);
	}

	public void reset()
	{
		entries = new ArrayList<>();
		lastRefresh = 0;
	}
}
