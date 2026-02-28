package com.cobblemonbingo.bingo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BingoConfig {
    /** GUI title. */
    public String name = "Bingo";

    /** If true, the board is randomized per-player per-game and persisted. */
    public boolean isRandomized = false;

    /** If false, no progress is gained, and /bingo open won’t open it. */
    public boolean isActive = true;

    /**
     * NEW:
     * - true: winner resets the bingo for all players (old behavior)
     * - false: does NOT reset on completion; players can keep completing until admin reset
     */
    public boolean doesResetOnCompletion = true;

    /**
     * Which line types can complete the bingo.
     * Allowed: "horizontal", "vertical", "diagonal"
     * If empty/null => all enabled.
     */
    public List<String> completion = new ArrayList<>();

    /**
     * Broadcast when someone completes. Supports:
     * - & color codes
     * - %player% and %game%
     */
    public String completionMessage = "&a%player% completed &e%game%&a!";

    /** If true, after completion the game is disabled. */
    public boolean disableOnCompletion = false;

    /** Weighted commands run on completion (choose 1 by weight). */
    public List<WeightedCommand> onCompletion = new ArrayList<>();

    /** Must contain >= 25. */
    public List<BingoChallenge> challenges = new ArrayList<>();

    // -----------------
    // Runtime indexes
    // -----------------

    /** challengeId -> challenge (built at load time). Not serialized. */
    public transient Map<String, BingoChallenge> challengeById;

    /** collect itemId string -> resolved Item (built at load time). Not serialized. */
    public transient Map<String, Item> collectItemCache;

    /** True if config contains any "collect" challenge with an item. */
    public transient boolean hasCollectChallenges;

    /** True if config contains any "catch" challenge. */
    public transient boolean hasCatchChallenges;

    /** True if config contains any "enterarea" challenge. */
    public transient boolean hasEnterAreaChallenges;

    /** Build runtime caches/indexes after Gson load (safe to call multiple times). */
    public void buildIndexes() {
        challengeById = new HashMap<>();
        collectItemCache = new HashMap<>();
        hasCollectChallenges = false;
        hasCatchChallenges = false;
        hasEnterAreaChallenges = false;

        if (challenges == null) return;

        for (BingoChallenge ch : challenges) {
            if (ch == null) continue;

            if (ch.id != null && !ch.id.isBlank()) {
                challengeById.put(ch.id.trim(), ch);
            }

            String type = ch.type == null ? "" : ch.type.trim().toLowerCase(Locale.ROOT);

            if (type.equals("collect") && ch.properties != null && ch.properties.item != null && !ch.properties.item.isBlank()) {
                hasCollectChallenges = true;
                ch.properties.resolvedCollectItem = resolveItemOrAir(ch.properties.item);
            }
            if (type.equals("catch")) {
                hasCatchChallenges = true;
            }
            if (type.equals("enterarea")) {
                hasEnterAreaChallenges = true;
            }
        }
    }

    /** Fast lookup; falls back to linear scan if indexes weren't built for some reason. */
    public BingoChallenge getChallengeById(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) return null;
        String key = challengeId.trim();

        if (challengeById != null) {
            BingoChallenge fromMap = challengeById.get(key);
            if (fromMap != null) return fromMap;
        }

        if (challenges == null) return null;
        for (BingoChallenge ch : challenges) {
            if (ch == null || ch.id == null) continue;
            if (ch.id.trim().equals(key)) return ch;
        }
        return null;
    }

    /** Resolve an item id once. Returns AIR on invalid ids (never null). */
    public Item resolveItemOrAir(String itemId) {
        if (itemId == null || itemId.isBlank()) return Items.AIR;
        String key = itemId.trim();

        if (collectItemCache != null) {
            Item cached = collectItemCache.get(key);
            if (cached != null) return cached;
        }

        ResourceLocation rl = ResourceLocation.tryParse(key);
        Item item = (rl == null) ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        if (item == null) item = Items.AIR;

        if (collectItemCache != null) {
            collectItemCache.put(key, item);
        }
        return item;
    }

    public static final class WeightedCommand {
        public String command;
        public Integer weight; // default 1
    }

    public static final class BingoChallenge {
        /** REQUIRED unique-ish id per bingo file. */
        public String id;

        /** Display name for the item/challenge. */
        public String name;

        /** e.g. "catch", "collect", "custom", "enterarea", "placeholder" */
        public String type = "placeholder";

        /** item id (minecraft:diamond) OR head:<playerName> OR headvalue:<base64> */
        public String icon = "minecraft:paper";

        public List<String> lore = new ArrayList<>();

        /** Optional */
        public Integer weight; // default 1
        public Integer slot;   // 0..24 required when isRandomized==false (ignored when true)

        /** Optional object (varies by type). */
        public Properties properties;

        public static final class Properties {
            // catch-only
            public List<String> pokemonType;
            public List<String> pokemon;

            // collect-only
            public String item;
            public transient Item resolvedCollectItem;

            // shared progress goal (catch/collect/custom)
            public Integer number; // default 1

            // OPTIONAL environment filters (apply to ALL types EXCEPT custom)
            public String dimension;
            public Boolean isRaining;

            // enterarea-only
            public Integer x;
            public Integer y;
            public Integer z;
        }
    }
}