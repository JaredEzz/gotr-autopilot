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
package com.fabulousotter.gotr.model;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

@AllArgsConstructor
@Getter
public enum PouchType
{
	SMALL("Small pouch", VarbitID.SMALL_ESSENCE_POUCH, ItemID.RCU_POUCH_SMALL, -1, 1, 3, 3, 0),
	MEDIUM("Medium pouch", VarbitID.MEDIUM_ESSENCE_POUCH, ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE, 25, 6, 3, 44 * 6),
	LARGE("Large pouch", VarbitID.LARGE_ESSENCE_POUCH, ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE, 50, 9, 7, 31 * 9),
	GIANT("Giant pouch", VarbitID.GIANT_ESSENCE_POUCH, ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE, 75, 12, 9, 10 * 12),
	COLOSSAL("Colossal pouch", VarbitID.COLOSSAL_ESSENCE_POUCH, ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE, 25, 40, 35, 8 * 40);

	private final String label;
	private final int varbitId;
	private final int itemId;
	private final int degradedItemId;
	private final int levelRequired;
	private final int maxCapacity;
	private final int degradedCapacity;
	// Essence the pouch can take before it degrades; 0 for the small pouch, which never degrades.
	private final int degradeEssence;

	public int capacity(int runecraftLevel, boolean degraded)
	{
		if (this == COLOSSAL)
		{
			int full;
			if (runecraftLevel >= 85)
			{
				full = 40;
			}
			else if (runecraftLevel >= 75)
			{
				full = 27;
			}
			else if (runecraftLevel >= 50)
			{
				full = 16;
			}
			else
			{
				full = 8;
			}
			return degraded ? Math.max(0, full - 5) : full;
		}
		if (runecraftLevel < levelRequired)
		{
			return 0;
		}
		return degraded ? degradedCapacity : maxCapacity;
	}

	@Nullable
	public static PouchType fromItem(int itemId)
	{
		for (PouchType pouch : values())
		{
			if (pouch.itemId == itemId || pouch.degradedItemId == itemId)
			{
				return pouch;
			}
		}
		return null;
	}

	public static boolean isDegradedItem(int itemId)
	{
		for (PouchType pouch : values())
		{
			if (pouch.degradedItemId == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
