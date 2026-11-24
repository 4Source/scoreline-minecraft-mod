package io.github.foursource.scoreline.server;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.stats.Stat;

/**
 * A player as {@link FakeGameServer} sees it.
 *
 * @param uuid the player's UUID
 * @param channels the payload types the player's client can receive; a test may change it to give or take a channel
 * @param values the player's stat values; a stat without an entry fails the test, so a test states every value it reads
 */
record FakePlayer(UUID uuid, Set<CustomPacketPayload.Type<?>> channels, Map<Stat<?>, Integer> values) {}
