package com.cobblemonbingo.bingo;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public final class BingoState extends SavedData {

    private static final String NAME = "cobblemon_bingo";

    // UUID -> (key -> progress) where key = "gameId|challengeId"
    private final Map<UUID, Map<String, Integer>> progress = new HashMap<>();

    // UUID -> completed keys (same key format)
    private final Map<UUID, Set<String>> completed = new HashMap<>();

    // UUID -> (gameId -> board[25] challengeIds)
    private final Map<UUID, Map<String, List<String>>> boards = new HashMap<>();

    // NEW: UUID -> set(gameId) where player has already received completion reward for that game
    private final Map<UUID, Set<String>> claimedRewards = new HashMap<>();

    public static BingoState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(BingoState::new, BingoState::load, DataFixTypes.LEVEL),
                NAME
        );
    }

    // -----------------
    // Progress / completion
    // -----------------

    public int getProgress(UUID playerId, String key) {
        Map<String, Integer> m = progress.get(playerId);
        if (m == null) return 0;
        return m.getOrDefault(key, 0);
    }

    public void addProgress(UUID playerId, String key, int amount) {
        if (amount <= 0) return;
        progress.computeIfAbsent(playerId, k -> new HashMap<>()).merge(key, amount, Integer::sum);
        setDirty();
    }

    public void setProgress(UUID playerId, String key, int value) {
        if (value < 0) value = 0;
        progress.computeIfAbsent(playerId, k -> new HashMap<>()).put(key, value);
        setDirty();
    }

    public boolean isCompleted(UUID playerId, String key) {
        Set<String> s = completed.get(playerId);
        return s != null && s.contains(key);
    }

    public void markCompleted(UUID playerId, String key) {
        completed.computeIfAbsent(playerId, k -> new HashSet<>()).add(key);
        setDirty();
    }

    // -----------------
    // Boards
    // -----------------

    public List<String> getBoard(UUID playerId, String gameId) {
        Map<String, List<String>> per = boards.get(playerId);
        if (per == null) return null;
        return per.get(gameId);
    }

    public void setBoard(UUID playerId, String gameId, List<String> board25) {
        if (board25 == null || board25.size() != 25) return;
        boards.computeIfAbsent(playerId, k -> new HashMap<>()).put(gameId, new ArrayList<>(board25));
        setDirty();
    }

    // -----------------
    // Reward claim tracking (NEW)
    // -----------------

    public boolean hasClaimedReward(UUID playerId, String gameId) {
        if (playerId == null || gameId == null || gameId.isBlank()) return false;
        String gid = gameId.trim().toLowerCase(Locale.ROOT);
        Set<String> s = claimedRewards.get(playerId);
        return s != null && s.contains(gid);
    }

    public void markClaimedReward(UUID playerId, String gameId) {
        if (playerId == null || gameId == null || gameId.isBlank()) return;
        String gid = gameId.trim().toLowerCase(Locale.ROOT);
        claimedRewards.computeIfAbsent(playerId, k -> new HashSet<>()).add(gid);
        setDirty();
    }

    public void clearClaimedReward(UUID playerId, String gameId) {
        if (playerId == null || gameId == null || gameId.isBlank()) return;
        String gid = gameId.trim().toLowerCase(Locale.ROOT);
        Set<String> s = claimedRewards.get(playerId);
        if (s == null) return;
        if (s.remove(gid)) {
            if (s.isEmpty()) claimedRewards.remove(playerId);
            setDirty();
        }
    }

    // -----------------
    // Reset helpers (bingo-level)
    // -----------------

    public Set<UUID> allKnownPlayers() {
        Set<UUID> out = new HashSet<>();
        out.addAll(progress.keySet());
        out.addAll(completed.keySet());
        out.addAll(boards.keySet());
        out.addAll(claimedRewards.keySet());
        return out;
    }

    public void resetGameForPlayer(UUID playerId, String gameId) {
        if (playerId == null) return;
        if (gameId == null || gameId.isBlank()) return;

        String gid = gameId.trim().toLowerCase(Locale.ROOT);
        String prefix = gid + "|";

        Map<String, Integer> pm = progress.get(playerId);
        if (pm != null) {
            pm.keySet().removeIf(k -> k.startsWith(prefix));
            if (pm.isEmpty()) progress.remove(playerId);
        }

        Set<String> cs = completed.get(playerId);
        if (cs != null) {
            cs.removeIf(k -> k.startsWith(prefix));
            if (cs.isEmpty()) completed.remove(playerId);
        }

        Map<String, List<String>> b = boards.get(playerId);
        if (b != null) {
            b.remove(gid);
            if (b.isEmpty()) boards.remove(playerId);
        }

        // IMPORTANT: resetbingo should allow re-earning completion reward
        clearClaimedReward(playerId, gid);

        setDirty();
    }

    public void resetAllGamesForPlayer(UUID playerId) {
        if (playerId == null) return;
        progress.remove(playerId);
        completed.remove(playerId);
        boards.remove(playerId);
        claimedRewards.remove(playerId);
        setDirty();
    }

    public void resetGameForAllPlayers(String gameId) {
        if (gameId == null || gameId.isBlank()) return;
        for (UUID id : allKnownPlayers()) {
            resetGameForPlayer(id, gameId);
        }
        setDirty();
    }

    // -----------------
    // Reset helpers (challenge-level)  ✅ THESE ARE WHAT YOUR COMMANDS NEED
    // -----------------

    /**
     * Resets a single challenge's progress/completion for one player.
     * If gameId == null: applies across ALL games.
     *
     * NOTE: does NOT clear claimedRewards (that belongs to resetbingo).
     */
    public void resetChallengeForPlayer(UUID playerId, String challengeId, String gameIdOrNull) {
        if (playerId == null) return;
        if (challengeId == null || challengeId.isBlank()) return;

        String cid = challengeId.trim();
        String suffix = "|" + cid;

        if (gameIdOrNull != null && !gameIdOrNull.isBlank()) {
            String gid = gameIdOrNull.trim().toLowerCase(Locale.ROOT);
            String key = gid + "|" + cid;

            Map<String, Integer> pm = progress.get(playerId);
            if (pm != null) {
                if (pm.remove(key) != null) {
                    if (pm.isEmpty()) progress.remove(playerId);
                }
            }

            Set<String> cs = completed.get(playerId);
            if (cs != null) {
                if (cs.remove(key)) {
                    if (cs.isEmpty()) completed.remove(playerId);
                }
            }

            setDirty();
            return;
        }

        // Across ALL games: remove any key that ends with "|<cid>"
        Map<String, Integer> pm = progress.get(playerId);
        if (pm != null) {
            pm.keySet().removeIf(k -> k.endsWith(suffix));
            if (pm.isEmpty()) progress.remove(playerId);
        }

        Set<String> cs = completed.get(playerId);
        if (cs != null) {
            cs.removeIf(k -> k.endsWith(suffix));
            if (cs.isEmpty()) completed.remove(playerId);
        }

        setDirty();
    }

    /**
     * Resets a single challenge for ALL players.
     * If gameId == null: applies across ALL games.
     */
    public void resetChallengeForAllPlayers(String challengeId, String gameIdOrNull) {
        if (challengeId == null || challengeId.isBlank()) return;
        for (UUID id : allKnownPlayers()) {
            resetChallengeForPlayer(id, challengeId, gameIdOrNull);
        }
        setDirty();
    }

    // -----------------
    // NBT persistence
    // -----------------

    private static BingoState load(CompoundTag tag, HolderLookup.Provider provider) {
        BingoState state = new BingoState();

        // progress
        CompoundTag pRoot = tag.getCompound("progress");
        for (String uuidStr : pRoot.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag per = pRoot.getCompound(uuidStr);
                Map<String, Integer> map = new HashMap<>();
                for (String k : per.getAllKeys()) {
                    map.put(k, per.getInt(k));
                }
                if (!map.isEmpty()) state.progress.put(uuid, map);
            } catch (Exception ignored) {}
        }

        // completed
        CompoundTag cRoot = tag.getCompound("completed");
        for (String uuidStr : cRoot.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag list = cRoot.getList(uuidStr, Tag.TAG_STRING);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    set.add(list.getString(i));
                }
                if (!set.isEmpty()) state.completed.put(uuid, set);
            } catch (Exception ignored) {}
        }

        // boards
        CompoundTag bRoot = tag.getCompound("boards");
        for (String uuidStr : bRoot.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag per = bRoot.getCompound(uuidStr);

                Map<String, List<String>> games = new HashMap<>();
                for (String gameId : per.getAllKeys()) {
                    ListTag list = per.getList(gameId, Tag.TAG_STRING);
                    if (list.size() != 25) continue;
                    List<String> board = new ArrayList<>(25);
                    for (int i = 0; i < 25; i++) board.add(list.getString(i));
                    games.put(gameId, board);
                }

                if (!games.isEmpty()) state.boards.put(uuid, games);
            } catch (Exception ignored) {}
        }

        // claimedRewards (NEW)
        CompoundTag rRoot = tag.getCompound("claimedRewards");
        for (String uuidStr : rRoot.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                ListTag list = rRoot.getList(uuidStr, Tag.TAG_STRING);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    String gid = list.getString(i);
                    if (gid != null && !gid.isBlank()) set.add(gid.trim().toLowerCase(Locale.ROOT));
                }
                if (!set.isEmpty()) state.claimedRewards.put(uuid, set);
            } catch (Exception ignored) {}
        }

        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag pRoot = new CompoundTag();
        for (var e : progress.entrySet()) {
            CompoundTag per = new CompoundTag();
            for (var pe : e.getValue().entrySet()) {
                per.putInt(pe.getKey(), pe.getValue());
            }
            pRoot.put(e.getKey().toString(), per);
        }
        tag.put("progress", pRoot);

        CompoundTag cRoot = new CompoundTag();
        for (var e : completed.entrySet()) {
            ListTag list = new ListTag();
            for (String k : e.getValue()) {
                list.add(StringTag.valueOf(k));
            }
            cRoot.put(e.getKey().toString(), list);
        }
        tag.put("completed", cRoot);

        CompoundTag bRoot = new CompoundTag();
        for (var e : boards.entrySet()) {
            CompoundTag per = new CompoundTag();
            for (var ge : e.getValue().entrySet()) {
                ListTag list = new ListTag();
                for (String cid : ge.getValue()) list.add(StringTag.valueOf(cid));
                per.put(ge.getKey(), list);
            }
            bRoot.put(e.getKey().toString(), per);
        }
        tag.put("boards", bRoot);

        // claimedRewards (NEW)
        CompoundTag rRoot = new CompoundTag();
        for (var e : claimedRewards.entrySet()) {
            ListTag list = new ListTag();
            for (String gid : e.getValue()) {
                list.add(StringTag.valueOf(gid));
            }
            rRoot.put(e.getKey().toString(), list);
        }
        tag.put("claimedRewards", rRoot);

        return tag;
    }

    public static String key(String gameId, String challengeId) {
        String gid = (gameId == null ? "default" : gameId.trim().toLowerCase(Locale.ROOT));
        String cid = (challengeId == null ? "" : challengeId.trim());
        return gid + "|" + cid;
    }
}