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

import com.fabulousotter.gotr.model.Altar;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PlannerSettings
{
	@Builder.Default
	Strategy strategy = Strategy.GENERAL;
	@Builder.Default
	int openingFragmentTarget = 120;
	@Builder.Default
	int craftWindowSeconds = 50;
	@Builder.Default
	int minFragmentsToCraft = 30;
	@Builder.Default
	int desiredGuardians = 2;
	@Builder.Default
	boolean combinationRunes = false;
	@Builder.Default
	Altar baseRune = Altar.AIR;
	@Builder.Default
	boolean balanceWithSavedPoints = true;
	@Builder.Default
	int maxImbalance = 200;
	@Builder.Default
	Set<Altar> preferredAltars = ImmutableSet.of();
	@Builder.Default
	Set<Altar> dispreferredAltars = ImmutableSet.of();
	@Builder.Default
	int pouchRepairLoads = 2;
	@Builder.Default
	boolean protectRightBarrier = true;
	@Builder.Default
	BarrierPriority barrierPriority = BarrierPriority.LOWEST_HEALTH;
	@Builder.Default
	int portalMinCapacity = 10;
	@Builder.Default
	int gracePortalSeconds = 95;
	// How long to wait for a better altar when only a weak one is usable; 0 never waits. Every
	// altar pays two energy per essence, so the wait only buys a better cell, and points come
	// first: a weak altar is an altar.
	@Builder.Default
	int weakAltarWaitSeconds = 0;
	@Builder.Default
	int portalWarningSeconds = 10;
}
