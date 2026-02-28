package com.cobblemonbingo.bingo;

import com.cobblemonbingo.Cobblemonbingo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

public final class BingoConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("bingo");

    private BingoConfigManager() {}

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    /** Called on mod init. */
    public static void bootstrapDefaultIfMissing() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            Path defaultFile = CONFIG_DIR.resolve("default.json");
            if (!Files.exists(defaultFile)) {
                BingoConfig cfg = createDefault();
                Files.writeString(defaultFile, GSON.toJson(cfg), StandardCharsets.UTF_8);
                Cobblemonbingo.LOGGER.info("Created /config/bingo/default.json with 25 placeholders.");
            }

        } catch (Exception e) {
            Cobblemonbingo.LOGGER.error("Failed to bootstrap /config/bingo/default.json", e);
        }
    }

    /** Returns null if file doesn't exist or is unreadable. */
    public static BingoConfig loadIfExists(String bingoName) {
        String safeName = sanitize(bingoName);
        Path file = CONFIG_DIR.resolve(safeName + ".json");

        if (!Files.exists(file)) return null;

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            BingoConfig cfg = GSON.fromJson(json, BingoConfig.class);
            if (cfg == null) return null;

            if (cfg.completion == null) cfg.completion = new ArrayList<>();
            if (cfg.onCompletion == null) cfg.onCompletion = new ArrayList<>();
            if (cfg.challenges == null) cfg.challenges = new ArrayList<>();
            for (var ch : cfg.challenges) {
                if (ch == null) continue;
                if (ch.lore == null) ch.lore = new ArrayList<>();
            }

            // Build fast lookup indexes + resolve collect item ids once.
            cfg.buildIndexes();
            return cfg;
        } catch (Exception e) {
            Cobblemonbingo.LOGGER.error("Failed to read bingo config: {}", file.getFileName(), e);
            return null;
        }
    }

    private static BingoConfig createDefault() {
        BingoConfig cfg = new BingoConfig();
        cfg.name = "Default Bingo";
        cfg.isRandomized = false;
        cfg.isActive = true;
        cfg.completionMessage = "&a%player% completed &e%game%&a!";
        cfg.disableOnCompletion = false;

        cfg.completion = new ArrayList<>();
        cfg.completion.add("horizontal");
        cfg.completion.add("vertical");
        cfg.completion.add("diagonal");

        cfg.onCompletion = new ArrayList<>();

        cfg.challenges = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            BingoConfig.BingoChallenge ch = new BingoConfig.BingoChallenge();
            ch.id = "placeholder_" + (i + 1);
            ch.name = "Placeholder #" + (i + 1);
            ch.type = "placeholder";
            ch.icon = "minecraft:paper";
            ch.slot = i;
            ch.weight = 1;
            ch.lore = new ArrayList<>();
            ch.lore.add("This is a placeholder challenge.");
            ch.lore.add("Edit default.json to change me.");
            cfg.challenges.add(ch);
        }

        return cfg;
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "default";
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[^a-z0-9._-]", "_");
        if (cleaned.length() > 64) cleaned = cleaned.substring(0, 64);
        return cleaned;
    }
}