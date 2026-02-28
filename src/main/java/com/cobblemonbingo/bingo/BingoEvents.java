package com.cobblemonbingo.bingo;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

public final class BingoEvents {
    private BingoEvents() {}

    // Inventory-based collect checks don't need to run every tick.
    private static final int COLLECT_TICK_INTERVAL = 10; // ~0.5s at 20 TPS
    private static int collectTickCounter = 0;

    // Enter-area checks can be frequent but should still be throttled.
    private static final int ENTERAREA_TICK_INTERVAL = 5; // ~0.25s
    private static int enterAreaTickCounter = 0;

    // Track last known block position to detect "entering" the area (pos change -> now equals target).
    private static final Map<UUID, BlockPos> LAST_BLOCK_POS = new HashMap<>();

    public static void register() {

        // Catch challenges
        CobblemonEvents.POKEMON_CAPTURED.subscribe((PokemonCapturedEvent event) -> {
            ServerPlayer player = event.getPlayer();
            if (player == null) return;

            String caughtIdRaw = getCaughtPokemonId(event);
            String caughtNorm = (caughtIdRaw == null) ? null : normalizeSpeciesId(caughtIdRaw);
            Pokemon pokemon = event.getPokemon();
            Set<String> caughtTypes = (pokemon == null) ? Set.of() : new HashSet<>(getPokemonTypeNames(pokemon));

            for (String gameId : BingoRegistry.ids()) {
                BingoConfig cfg = BingoRegistry.get(gameId);
                if (cfg == null || !cfg.isActive || cfg.challenges == null) continue;
                if (!cfg.hasCatchChallenges) continue;

                BingoState state = BingoState.get(player.serverLevel());
                List<String> board = BingoService.ensureBoard(player, state, gameId, cfg);

                boolean changedAny = false;
                boolean completedAny = false;

                for (String challengeId : board) {
                    if (challengeId == null || challengeId.isBlank()) continue;

                    BingoConfig.BingoChallenge ch = BingoService.findChallenge(cfg, challengeId);
                    if (ch == null || ch.id == null || ch.id.isBlank()) continue;

                    if (!"catch".equalsIgnoreCase(ch.type)) continue;
                    if (ch.properties == null) continue;

                    if (!BingoService.environmentMatches(player, ch)) continue;

                    boolean hasPokemon = ch.properties.pokemon != null && !ch.properties.pokemon.isEmpty();
                    boolean hasTypes = ch.properties.pokemonType != null && !ch.properties.pokemonType.isEmpty();
                    if (hasPokemon && hasTypes) continue;

                    int goal = BingoService.goalFor(ch);
                    String key = BingoState.key(gameId, ch.id);
                    if (state.isCompleted(player.getUUID(), key)) continue;

                    boolean matched = false;

                    if (hasPokemon) {
                        if (caughtNorm == null) continue;
                        for (String entry : ch.properties.pokemon) {
                            String want = normalizeSpeciesId(entry);
                            if (want != null && want.equals(caughtNorm)) {
                                matched = true;
                                break;
                            }
                        }
                    } else if (hasTypes) {
                        for (String wantRaw : ch.properties.pokemonType) {
                            String want = normalizeType(wantRaw);
                            if (want == null) continue;
                            if (caughtTypes.contains(want)) {
                                matched = true;
                                break;
                            }
                        }
                    } else {
                        continue;
                    }

                    if (!matched) continue;

                    int before = state.getProgress(player.getUUID(), key);
                    if (before >= goal) continue;

                    state.addProgress(player.getUUID(), key, 1);
                    changedAny = true;

                    int after = state.getProgress(player.getUUID(), key);
                    if (after >= goal) {
                        state.markCompleted(player.getUUID(), key);
                        completedAny = true;
                    }
                }

                if (changedAny) {
                    BingoService.refreshIfOpen(player, gameId);
                }
                if (completedAny) {
                    BingoService.checkAndHandleWin(player.getServer(), player, gameId, cfg, state);
                }
            }
        });

        // Collect + EnterArea challenges (tick based)
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {

            // --------------------
            // ENTERAREA (throttled)
            // --------------------
            enterAreaTickCounter++;
            if (enterAreaTickCounter % ENTERAREA_TICK_INTERVAL == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    BlockPos current = player.blockPosition();
                    BlockPos last = LAST_BLOCK_POS.put(player.getUUID(), current);

                    // If last == null (first time seen), treat as "entered" if already standing on the target.
                    // Otherwise require a position change to count as entering.
                    boolean moved = (last == null) || !last.equals(current);

                    if (!moved) continue;

                    for (String gameId : BingoRegistry.ids()) {
                        BingoConfig cfg = BingoRegistry.get(gameId);
                        if (cfg == null || !cfg.isActive || cfg.challenges == null) continue;
                        if (!cfg.hasEnterAreaChallenges) continue;

                        BingoState state = BingoState.get(player.serverLevel());
                        List<String> board = BingoService.ensureBoard(player, state, gameId, cfg);

                        boolean changedAny = false;
                        boolean completedAny = false;

                        for (String challengeId : board) {
                            if (challengeId == null || challengeId.isBlank()) continue;

                            BingoConfig.BingoChallenge ch = BingoService.findChallenge(cfg, challengeId);
                            if (ch == null || ch.id == null || ch.id.isBlank()) continue;

                            if (!"enterarea".equalsIgnoreCase(ch.type)) continue;
                            if (ch.properties == null) continue;

                            // env filters apply (non-custom)
                            if (!BingoService.environmentMatches(player, ch)) continue;

                            Integer x = ch.properties.x;
                            Integer y = ch.properties.y;
                            Integer z = ch.properties.z;
                            if (x == null || y == null || z == null) continue;

                            String key = BingoState.key(gameId, ch.id);
                            if (state.isCompleted(player.getUUID(), key)) continue;

                            // "Enter area" is an exact block position match.
                            if (current.getX() == x && current.getY() == y && current.getZ() == z) {
                                int goal = BingoService.goalFor(ch);
                                // Complete immediately
                                state.setProgress(player.getUUID(), key, goal);
                                state.markCompleted(player.getUUID(), key);

                                changedAny = true;
                                completedAny = true;
                            }
                        }

                        if (changedAny) {
                            BingoService.refreshIfOpen(player, gameId);
                        }
                        if (completedAny) {
                            BingoService.checkAndHandleWin(server, player, gameId, cfg, state);
                        }
                    }
                }
            }

            // ----------------
            // COLLECT (throttled)
            // ----------------
            collectTickCounter++;
            if (collectTickCounter % COLLECT_TICK_INTERVAL != 0) return;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {

                Map<Item, Integer> invCounts = buildInventoryCounts(player);

                for (String gameId : BingoRegistry.ids()) {
                    BingoConfig cfg = BingoRegistry.get(gameId);
                    if (cfg == null || !cfg.isActive || cfg.challenges == null) continue;
                    if (!cfg.hasCollectChallenges) continue;

                    BingoState state = BingoState.get(player.serverLevel());
                    List<String> board = BingoService.ensureBoard(player, state, gameId, cfg);

                    boolean changedAny = false;
                    boolean completedAny = false;

                    for (String challengeId : board) {
                        if (challengeId == null || challengeId.isBlank()) continue;

                        BingoConfig.BingoChallenge ch = BingoService.findChallenge(cfg, challengeId);
                        if (ch == null || ch.id == null || ch.id.isBlank()) continue;

                        if (!"collect".equalsIgnoreCase(ch.type)) continue;
                        if (ch.properties == null) continue;
                        if (ch.properties.item == null || ch.properties.item.isBlank()) continue;

                        if (!BingoService.environmentMatches(player, ch)) continue;

                        String key = BingoState.key(gameId, ch.id);
                        if (state.isCompleted(player.getUUID(), key)) continue;

                        int goal = BingoService.goalFor(ch);
                        Item wantItem = ch.properties.resolvedCollectItem;
                        if (wantItem == null) {
                            wantItem = cfg.resolveItemOrAir(ch.properties.item);
                            ch.properties.resolvedCollectItem = wantItem;
                        }
                        if (wantItem == Items.AIR) continue;

                        int have = invCounts.getOrDefault(wantItem, 0);

                        int prev = state.getProgress(player.getUUID(), key);

                        int clamped = Math.min(goal, Math.max(0, have));
                        int next = Math.max(prev, clamped);
                        if (next == prev) continue;

                        state.setProgress(player.getUUID(), key, next);
                        changedAny = true;

                        if (next >= goal) {
                            state.markCompleted(player.getUUID(), key);
                            completedAny = true;
                        }
                    }

                    if (changedAny) {
                        BingoService.refreshIfOpen(player, gameId);
                    }

                    if (completedAny) {
                        BingoService.checkAndHandleWin(server, player, gameId, cfg, state);
                    }
                }
            }
        });
    }

    private static Map<Item, Integer> buildInventoryCounts(ServerPlayer player) {
        Map<Item, Integer> out = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            out.merge(item, stack.getCount(), Integer::sum);
        }
        return out;
    }

    private static String normalizeType(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private static List<String> getPokemonTypeNames(Pokemon cobblemonPokemon) {
        List<String> out = new ArrayList<>();
        var types = cobblemonPokemon.getTypes();
        for (ElementalType t : types) {
            out.add(t.getName().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String normalizeSpeciesId(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.contains(":")) return s;
        return "cobblemon:" + s;
    }

    private static String getCaughtPokemonId(PokemonCapturedEvent event) {
        try {
            Object pokemon = event.getClass().getMethod("getPokemon").invoke(event);
            if (pokemon == null) return null;

            Object species = null;
            try {
                species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            } catch (Exception ignored) {}

            if (species == null) return null;

            Object idObj = null;
            String[] methods = new String[] {
                    "getResourceLocation",
                    "getResourceIdentifier",
                    "getIdentifier",
                    "getId",
                    "resourceLocation",
                    "resourceIdentifier"
            };

            for (String m : methods) {
                try {
                    idObj = species.getClass().getMethod(m).invoke(species);
                    if (idObj != null) break;
                } catch (Exception ignored) {}
            }

            if (idObj instanceof net.minecraft.resources.ResourceLocation rl) {
                return rl.toString();
            }
            if (idObj != null) {
                String s = idObj.toString();
                return (s == null || s.isBlank()) ? null : s.trim();
            }

            String s = species.toString();
            return (s == null || s.isBlank()) ? null : s.trim();
        } catch (Exception ignored) {}

        return null;
    }
}