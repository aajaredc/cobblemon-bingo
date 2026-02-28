package com.cobblemonbingo.bingo;

import com.cobblemonbingo.Cobblemonbingo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BingoRegistry {

    private static volatile Set<String> IDS = Set.of("default");
    private static final Map<String, BingoConfig> CACHE = new ConcurrentHashMap<>();

    private BingoRegistry() {}

    public static Set<String> ids() {
        return IDS;
    }

    public static BingoConfig get(String bingoName) {
        if (bingoName == null) return null;
        String key = sanitize(bingoName);
        return CACHE.get(key);
    }

    public static void loadAll() {
        reload();
    }

    public static void reload() {
        Map<String, BingoConfig> next = new HashMap<>();
        Set<String> ids = new HashSet<>();

        Path dir = BingoConfigManager.getConfigDir();
        try {
            if (!Files.exists(dir)) {
                IDS = Set.of("default");
                CACHE.clear();
                return;
            }

            try (var stream = Files.list(dir)) {
                stream
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> {
                            String file = p.getFileName().toString();
                            String id = file.substring(0, file.length() - 5); // remove .json
                            BingoConfig cfg = BingoConfigManager.loadIfExists(id);
                            if (cfg != null) {
                                // Safety: ensure runtime indexes exist even if loaded elsewhere.
                                cfg.buildIndexes();
                                ids.add(id);
                                next.put(id, cfg);
                            }
                        });
            }

            if (ids.isEmpty()) ids.add("default");

            CACHE.clear();
            CACHE.putAll(next);
            IDS = Collections.unmodifiableSet(ids);

            Cobblemonbingo.LOGGER.info("Loaded bingo games: {}", IDS);

        } catch (Exception e) {
            Cobblemonbingo.LOGGER.error("Failed to load bingo configs", e);
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "default";
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[^a-z0-9._-]", "_");
        if (cleaned.length() > 64) cleaned = cleaned.substring(0, 64);
        return cleaned;
    }
}