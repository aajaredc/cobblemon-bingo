package com.cobblemonbingo.bingo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class BingoService {
    private BingoService() {}

    private static final int ROWS = 5;
    private static final int CHEST_SIZE = ROWS * 9;

    public static void open(ServerPlayer player, String bingoName) {
        String gameId = sanitize(bingoName);
        BingoConfig cfg = BingoRegistry.get(gameId);

        if (cfg == null) {
            player.sendSystemMessage(Component.literal("No bingo game named '" + bingoName + "'."));
            return;
        }
        if (!cfg.isActive) {
            player.sendSystemMessage(Component.literal("That bingo is currently disabled."));
            return;
        }
        if (cfg.challenges == null || cfg.challenges.size() < 25) {
            player.sendSystemMessage(Component.literal("Bingo config '" + gameId + "' must have at least 25 challenges."));
            return;
        }

        BingoState state = BingoState.get(player.serverLevel());
        ensureBoard(player, state, gameId, cfg);

        SimpleContainer container = buildContainer(player, gameId, cfg);
        Component title = Component.literal((cfg.name == null || cfg.name.isBlank()) ? "Bingo" : cfg.name);

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inv, net.minecraft.world.entity.player.Player p) {
                return new BingoScreenHandler(syncId, inv, container, gameId);
            }
        });
    }

    public static void refreshIfOpen(ServerPlayer player, String gameId) {
        if (player == null || gameId == null) return;
        if (!(player.containerMenu instanceof BingoScreenHandler handler)) return;
        if (!gameId.equals(handler.bingoId)) return;

        BingoConfig cfg = BingoRegistry.get(gameId);
        if (cfg == null) return;

        SimpleContainer container = handler.containerRef;
        if (container == null) return;

        applyContents(player, gameId, cfg, container);
        player.containerMenu.broadcastChanges();
    }

    public static List<String> ensureBoard(ServerPlayer player, BingoState state, String gameId, BingoConfig cfg) {
        String gid = sanitize(gameId);
        List<String> existing = state.getBoard(player.getUUID(), gid);
        if (existing != null && existing.size() == 25) return existing;

        List<String> board = createBoardForPlayer(player, cfg);
        state.setBoard(player.getUUID(), gid, board);
        return board;
    }

    public static boolean environmentMatches(ServerPlayer player, BingoConfig.BingoChallenge ch) {
        if (player == null || ch == null) return true;

        if ("custom".equalsIgnoreCase(nullToEmpty(ch.type))) return true;
        if (ch.properties == null) return true;

        if (ch.properties.dimension != null && !ch.properties.dimension.isBlank()) {
            String currentDim = player.level().dimension().location().toString();
            if (!currentDim.equals(ch.properties.dimension.trim())) return false;
        }

        if (ch.properties.isRaining != null) {
            boolean raining = player.serverLevel().isRaining();
            if (raining != ch.properties.isRaining) return false;
        }

        return true;
    }

    public static BingoConfig.BingoChallenge findChallenge(BingoConfig cfg, String challengeId) {
        if (cfg == null) return null;
        return cfg.getChallengeById(challengeId);
    }

    public static int goalFor(BingoConfig.BingoChallenge ch) {
        if (ch == null) return 1;
        if (ch.properties != null && ch.properties.number != null && ch.properties.number > 0) {
            return ch.properties.number;
        }
        return 1;
    }

    public static boolean addAdminProgress(ServerPlayer target, String gameId, String challengeId, int amount) {
        if (target == null) return false;
        if (amount <= 0) return false;

        String gid = sanitize(gameId);
        BingoConfig cfg = BingoRegistry.get(gid);
        if (cfg == null || !cfg.isActive) return false;

        BingoConfig.BingoChallenge ch = findChallenge(cfg, challengeId);
        if (ch == null || ch.id == null || ch.id.isBlank()) return false;

        BingoState state = BingoState.get(target.serverLevel());

        ensureBoard(target, state, gid, cfg);

        String key = BingoState.key(gid, ch.id);
        int goal = goalFor(ch);

        if (state.isCompleted(target.getUUID(), key)) {
            refreshIfOpen(target, gid);
            return false;
        }

        int before = state.getProgress(target.getUUID(), key);
        int after = Math.min(goal, before + amount);
        state.setProgress(target.getUUID(), key, after);

        boolean completedNow = false;
        if (after >= goal) {
            state.markCompleted(target.getUUID(), key);
            completedNow = true;
        }

        refreshIfOpen(target, gid);

        if (completedNow) {
            checkAndHandleWin(target.getServer(), target, gid, cfg, state);
        }

        return true;
    }

    /**
     * Winner handling:
     * - always broadcasts completion message when player has a valid line
     * - if cfg.doesResetOnCompletion == true:
     *     reward winner, reset game for all players (old behavior)
     * - else:
     *     no reset; reward winner only once per player until resetbingo/resetbingoall
     */
    public static void checkAndHandleWin(MinecraftServer server, ServerPlayer triggeringPlayer, String gameId, BingoConfig cfg, BingoState state) {
        if (server == null || triggeringPlayer == null || cfg == null || state == null) return;

        String gid = sanitize(gameId);

        boolean allowH = false, allowV = false, allowD = false;
        if (cfg.completion == null || cfg.completion.isEmpty()) {
            allowH = allowV = allowD = true;
        } else {
            for (String s : cfg.completion) {
                if (s == null) continue;
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (t.equals("horizontal")) allowH = true;
                if (t.equals("vertical")) allowV = true;
                if (t.equals("diagonal")) allowD = true;
            }
        }

        List<String> board = state.getBoard(triggeringPlayer.getUUID(), gid);
        if (board == null || board.size() != 25) return;

        boolean[] done = new boolean[25];
        for (int i = 0; i < 25; i++) {
            String cid = board.get(i);
            if (cid == null || cid.isBlank()) {
                done[i] = false;
                continue;
            }
            done[i] = state.isCompleted(triggeringPlayer.getUUID(), BingoState.key(gid, cid));
        }

        if (!hasLine(done, allowH, allowV, allowD)) return;

        // Default behavior: reset on completion (old behavior)
        if (cfg.doesResetOnCompletion) {
            runWeightedCompletionCommand(server, triggeringPlayer, cfg);

            // Reset for ALL players (online + offline) for this game
            state.resetGameForAllPlayers(gid);

            if (cfg.disableOnCompletion) {
                cfg.isActive = false;
            }

            // Refresh open menus for online players
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                refreshIfOpen(p, gid);
            }
            return;
        }

        // Non-reset mode: only reward once per player until that player is reset.
        if (!state.hasClaimedReward(triggeringPlayer.getUUID(), gid)) {
            state.markClaimedReward(triggeringPlayer.getUUID(), gid);
            state.setDirty();

            runWeightedCompletionCommand(server, triggeringPlayer, cfg);
            broadcastCompletion(server, triggeringPlayer, gid, cfg);
        }

        if (cfg.disableOnCompletion) {
            cfg.isActive = false;
        }

        // Minimal refresh
        refreshIfOpen(triggeringPlayer, gid);
    }

    private static void broadcastCompletion(MinecraftServer server, ServerPlayer winner, String gameId, BingoConfig cfg) {
        if (cfg.completionMessage == null || cfg.completionMessage.isBlank()) return;

        String msg = cfg.completionMessage
                .replace("%player%", winner.getName().getString())
                .replace("%game%", gameId);

        msg = msg.replace("&", "§");
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    private static void runWeightedCompletionCommand(MinecraftServer server, ServerPlayer winner, BingoConfig cfg) {
        if (cfg.onCompletion == null || cfg.onCompletion.isEmpty()) return;

        int total = 0;
        for (BingoConfig.WeightedCommand wc : cfg.onCompletion) {
            if (wc == null) continue;
            total += Math.max(1, wc.weight == null ? 1 : wc.weight);
        }
        if (total <= 0) return;

        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;

        for (BingoConfig.WeightedCommand wc : cfg.onCompletion) {
            if (wc == null || wc.command == null || wc.command.isBlank()) continue;
            acc += Math.max(1, wc.weight == null ? 1 : wc.weight);
            if (r < acc) {
                server.getCommands().performPrefixedCommand(
                        winner.createCommandSourceStack(),
                        wc.command
                );
                return;
            }
        }
    }

    private static boolean hasLine(boolean[] done, boolean allowH, boolean allowV, boolean allowD) {
        if (allowH) {
            for (int r = 0; r < 5; r++) {
                boolean ok = true;
                for (int c = 0; c < 5; c++) ok &= done[r * 5 + c];
                if (ok) return true;
            }
        }
        if (allowV) {
            for (int c = 0; c < 5; c++) {
                boolean ok = true;
                for (int r = 0; r < 5; r++) ok &= done[r * 5 + c];
                if (ok) return true;
            }
        }
        if (allowD) {
            boolean d1 = true, d2 = true;
            for (int i = 0; i < 5; i++) {
                d1 &= done[i * 5 + i];
                d2 &= done[i * 5 + (4 - i)];
            }
            return d1 || d2;
        }
        return false;
    }

    private static SimpleContainer buildContainer(ServerPlayer player, String gameId, BingoConfig cfg) {
        SimpleContainer container = new SimpleContainer(CHEST_SIZE);
        applyContents(player, gameId, cfg, container);
        return container;
    }

    private static void applyContents(ServerPlayer player, String gameId, BingoConfig cfg, SimpleContainer container) {
        for (int i = 0; i < CHEST_SIZE; i++) container.setItem(i, ItemStack.EMPTY);

        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.ITEM_NAME, Component.literal(" "));

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < 9; c++) {
                if (c <= 1 || c >= 7) {
                    container.setItem(r * 9 + c, filler.copy());
                }
            }
        }

        BingoState state = BingoState.get(player.serverLevel());
        List<String> board = ensureBoard(player, state, gameId, cfg);

        for (int boardSlot = 0; boardSlot < 25; boardSlot++) {
            String challengeId = board.get(boardSlot);
            if (challengeId == null || challengeId.isBlank()) continue;

            BingoConfig.BingoChallenge ch = findChallenge(cfg, challengeId);
            if (ch == null || ch.id == null || ch.id.isBlank()) continue;

            int chestSlot = boardSlotToChestSlot(boardSlot);

            String key = BingoState.key(gameId, ch.id);
            boolean completed = state.isCompleted(player.getUUID(), key);

            int goal = goalFor(ch);
            int prog = state.getProgress(player.getUUID(), key);
            if (completed) prog = Math.max(prog, goal);

            List<Component> extraLore = List.of(Component.literal("Progress: " + prog + " / " + goal));
            ItemStack icon = BingoIconUtil.createIconStack(ch, extraLore);

            if (completed) {
                icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            container.setItem(chestSlot, icon);
        }
    }

    private static int boardSlotToChestSlot(int boardSlot) {
        int row = boardSlot / 5;
        int col = boardSlot % 5;
        int chestCol = col + 2;
        return row * 9 + chestCol;
    }

    private static List<String> createBoardForPlayer(ServerPlayer player, BingoConfig cfg) {
        net.minecraft.util.RandomSource rand = player.getRandom();

        if (cfg.isRandomized) {
            List<BingoConfig.BingoChallenge> chosen = chooseTwentyFive(cfg, rand);
            Map<Integer, BingoConfig.BingoChallenge> placed = placeRandom(chosen, rand);

            List<String> board = new ArrayList<>(Collections.nCopies(25, ""));
            for (int i = 0; i < 25; i++) {
                BingoConfig.BingoChallenge ch = placed.get(i);
                board.set(i, (ch == null || ch.id == null) ? "" : ch.id);
            }
            return board;
        }

        @SuppressWarnings("unchecked")
        List<BingoConfig.BingoChallenge>[] bySlot = new List[25];
        for (int i = 0; i < 25; i++) bySlot[i] = new ArrayList<>();

        for (var ch : cfg.challenges) {
            if (ch == null || ch.slot == null) continue;
            if (ch.slot >= 0 && ch.slot <= 24) bySlot[ch.slot].add(ch);
        }

        List<String> board = new ArrayList<>(Collections.nCopies(25, ""));
        for (int s = 0; s < 25; s++) {
            List<BingoConfig.BingoChallenge> options = bySlot[s];
            if (options.isEmpty()) continue;
            BingoConfig.BingoChallenge picked = (options.size() == 1) ? options.get(0) : pickWeighted(options, rand);
            board.set(s, picked.id == null ? "" : picked.id);
        }
        return board;
    }

    private static List<BingoConfig.BingoChallenge> chooseTwentyFive(BingoConfig cfg, net.minecraft.util.RandomSource rand) {
        List<BingoConfig.BingoChallenge> all = new ArrayList<>();
        for (var ch : cfg.challenges) if (ch != null && ch.id != null && !ch.id.isBlank()) all.add(ch);

        if (all.size() <= 25) return all.subList(0, Math.min(25, all.size()));

        List<BingoConfig.BingoChallenge> pool = new ArrayList<>(all);
        List<BingoConfig.BingoChallenge> picked = new ArrayList<>(25);

        for (int i = 0; i < 25; i++) {
            int total = 0;
            for (var c : pool) total += Math.max(1, (c.weight == null ? 1 : c.weight));
            int r = rand.nextInt(total);

            int acc = 0;
            int idx = 0;
            for (; idx < pool.size(); idx++) {
                var c = pool.get(idx);
                acc += Math.max(1, (c.weight == null ? 1 : c.weight));
                if (r < acc) break;
            }
            if (idx >= pool.size()) idx = pool.size() - 1;

            picked.add(pool.remove(idx));
        }

        return picked;
    }

    private static Map<Integer, BingoConfig.BingoChallenge> placeRandom(List<BingoConfig.BingoChallenge> chosen, net.minecraft.util.RandomSource rand) {
        List<Integer> slots = new ArrayList<>(25);
        for (int i = 0; i < 25; i++) slots.add(i);
        Collections.shuffle(slots, new Random(rand.nextLong()));

        Map<Integer, BingoConfig.BingoChallenge> out = new HashMap<>();
        for (int i = 0; i < 25 && i < chosen.size(); i++) {
            out.put(slots.get(i), chosen.get(i));
        }
        return out;
    }

    private static BingoConfig.BingoChallenge pickWeighted(List<BingoConfig.BingoChallenge> options, net.minecraft.util.RandomSource rand) {
        int total = 0;
        for (var c : options) total += Math.max(1, (c.weight == null ? 1 : c.weight));
        int r = rand.nextInt(total);

        int acc = 0;
        for (var c : options) {
            acc += Math.max(1, (c.weight == null ? 1 : c.weight));
            if (r < acc) return c;
        }
        return options.get(options.size() - 1);
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "default";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9._-]", "_");
        if (t.length() > 64) t = t.substring(0, 64);
        return t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}