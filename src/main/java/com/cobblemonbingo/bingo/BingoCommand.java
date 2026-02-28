package com.cobblemonbingo.bingo;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BingoCommand {
    private BingoCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("bingo")

                            // /bingo open <game>
                            .then(Commands.literal("open")
                                    .then(Commands.argument("game", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                            .executes(ctx -> {
                                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                String game = StringArgumentType.getString(ctx, "game");
                                                BingoService.open(player, game);
                                                return 1;
                                            })
                                    )
                            )

                            // /bingo reload
                            .then(Commands.literal("reload")
                                    .requires(src -> src.hasPermission(2))
                                    .executes(ctx -> {
                                        BingoRegistry.reload();
                                        ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reloaded configs."), false);
                                        return 1;
                                    })
                            )

                            // /bingo enable <game>
                            .then(Commands.literal("enable")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("game", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                            .executes(ctx -> {
                                                String game = StringArgumentType.getString(ctx, "game");
                                                BingoConfig cfg = BingoRegistry.get(game);
                                                if (cfg == null) {
                                                    ctx.getSource().sendFailure(Component.literal("[Bingo] Unknown game: " + game));
                                                    return 0;
                                                }
                                                cfg.isActive = true;
                                                final String msg = "[Bingo] Enabled " + game;
                                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                return 1;
                                            })
                                    )
                            )

                            // /bingo disable <game> (wipe progress so it won’t carry over)
                            .then(Commands.literal("disable")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("game", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                            .executes(ctx -> {
                                                String game = StringArgumentType.getString(ctx, "game");
                                                BingoConfig cfg = BingoRegistry.get(game);
                                                if (cfg == null) {
                                                    ctx.getSource().sendFailure(Component.literal("[Bingo] Unknown game: " + game));
                                                    return 0;
                                                }

                                                cfg.isActive = false;

                                                BingoState state = BingoState.get(ctx.getSource().getLevel());
                                                state.resetGameForAllPlayers(game);

                                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                    BingoService.refreshIfOpen(p, game);
                                                }

                                                final String msg = "[Bingo] Disabled " + game + " (progress wiped).";
                                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                return 1;
                                            })
                                    )
                            )

                            // /bingo addprogress <player> <count> <challengeId> [gameId]
                            .then(Commands.literal("addprogress")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                    .then(Commands.argument("challengeId", StringArgumentType.word())

                                                            // WITH gameId
                                                            .then(Commands.argument("gameId", StringArgumentType.word())
                                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                                                    .executes(ctx -> {
                                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                        int count = IntegerArgumentType.getInteger(ctx, "count");
                                                                        String challengeId = StringArgumentType.getString(ctx, "challengeId");
                                                                        String gameId = StringArgumentType.getString(ctx, "gameId");

                                                                        boolean changed = BingoService.addAdminProgress(target, gameId, challengeId, count);

                                                                        final String msg = changed
                                                                                ? "[Bingo] Added " + count + " to " + challengeId + " in " + gameId + " for " + target.getName().getString()
                                                                                : "[Bingo] No change (wrong id/type, disabled game, already complete, or not on board): " + challengeId + " in " + gameId;

                                                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                                        return 1;
                                                                    })
                                                            )

                                                            // NO gameId => ALL games
                                                            .executes(ctx -> {
                                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                                                String challengeId = StringArgumentType.getString(ctx, "challengeId");

                                                                int attempted = 0;
                                                                int matchedCustom = 0;
                                                                int applied = 0;

                                                                for (String gid : BingoRegistry.ids()) {
                                                                    attempted++;

                                                                    BingoConfig cfg = BingoRegistry.get(gid);
                                                                    if (cfg == null) continue;
                                                                    if (!cfg.isActive) continue;

                                                                    BingoConfig.BingoChallenge ch = BingoService.findChallenge(cfg, challengeId);
                                                                    if (ch == null) continue;

                                                                    matchedCustom++;

                                                                    boolean changed = BingoService.addAdminProgress(target, gid, challengeId, count);
                                                                    if (changed) applied++;
                                                                }

                                                                final int attemptedF = attempted;
                                                                final int matchedF = matchedCustom;
                                                                final int appliedF = applied;
                                                                final String msg =
                                                                        "[Bingo] addprogress " + challengeId +
                                                                                " matched " + matchedF + "/" + attemptedF +
                                                                                " active game(s), applied in " + appliedF + ".";

                                                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )

                            // /bingo incrementprogress <player> <challengeId> [gameId]
                            .then(Commands.literal("incrementprogress")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("challengeId", StringArgumentType.word())

                                                    // WITH gameId
                                                    .then(Commands.argument("gameId", StringArgumentType.word())
                                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                                            .executes(ctx -> {
                                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                String challengeId = StringArgumentType.getString(ctx, "challengeId");
                                                                String gameId = StringArgumentType.getString(ctx, "gameId");

                                                                boolean changed = BingoService.addAdminProgress(target, gameId, challengeId, 1);

                                                                final String msg = changed
                                                                        ? "[Bingo] Incremented " + challengeId + " in " + gameId + " for " + target.getName().getString()
                                                                        : "[Bingo] No change (wrong id/type, disabled game, already complete, or not on board): " + challengeId + " in " + gameId;

                                                                ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                                return 1;
                                                            })
                                                    )

                                                    // NO gameId => ALL games
                                                    .executes(ctx -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                        String challengeId = StringArgumentType.getString(ctx, "challengeId");

                                                        int attempted = 0;
                                                        int matchedCustom = 0;
                                                        int applied = 0;

                                                        for (String gid : BingoRegistry.ids()) {
                                                            attempted++;

                                                            BingoConfig cfg = BingoRegistry.get(gid);
                                                            if (cfg == null) continue;
                                                            if (!cfg.isActive) continue;

                                                            BingoConfig.BingoChallenge ch = BingoService.findChallenge(cfg, challengeId);
                                                            if (ch == null) continue;

                                                            matchedCustom++;

                                                            boolean changed = BingoService.addAdminProgress(target, gid, challengeId, 1);
                                                            if (changed) applied++;
                                                        }

                                                        final int attemptedF = attempted;
                                                        final int matchedF = matchedCustom;
                                                        final int appliedF = applied;
                                                        final String msg =
                                                                "[Bingo] incrementprogress " + challengeId +
                                                                        " matched " + matchedF + "/" + attemptedF +
                                                                        " active game(s), applied in " + appliedF + ".";

                                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // /bingo resetbingo <player> <gameId>
                            .then(Commands.literal("resetbingo")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("gameId", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                                    .executes(ctx -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                        String gameId = StringArgumentType.getString(ctx, "gameId");

                                                        BingoState state = BingoState.get(target.serverLevel());
                                                        state.resetGameForPlayer(target.getUUID(), gameId);

                                                        BingoService.refreshIfOpen(target, gameId);

                                                        ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset bingo '" + gameId + "' for " + target.getName().getString()), false);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // /bingo resetbingoall <gameId>
                            .then(Commands.literal("resetbingoall")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("gameId", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                            .executes(ctx -> {
                                                String gameId = StringArgumentType.getString(ctx, "gameId");
                                                BingoState state = BingoState.get(ctx.getSource().getLevel());
                                                state.resetGameForAllPlayers(gameId);

                                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                    BingoService.refreshIfOpen(p, gameId);
                                                }

                                                ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset bingo '" + gameId + "' for ALL players."), false);
                                                return 1;
                                            })
                                    )
                            )

                            // /bingo resetchallenge <player> <challengeId> [gameId]
                            .then(Commands.literal("resetchallenge")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("challengeId", StringArgumentType.word())

                                                    .then(Commands.argument("gameId", StringArgumentType.word())
                                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                                            .executes(ctx -> {
                                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                String cid = StringArgumentType.getString(ctx, "challengeId");
                                                                String gid = StringArgumentType.getString(ctx, "gameId");

                                                                BingoState state = BingoState.get(target.serverLevel());
                                                                state.resetChallengeForPlayer(target.getUUID(), cid, gid);
                                                                BingoService.refreshIfOpen(target, gid);

                                                                ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset challenge '" + cid + "' in '" + gid + "' for " + target.getName().getString()), false);
                                                                return 1;
                                                            })
                                                    )

                                                    .executes(ctx -> {
                                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                        String cid = StringArgumentType.getString(ctx, "challengeId");

                                                        BingoState state = BingoState.get(target.serverLevel());
                                                        state.resetChallengeForPlayer(target.getUUID(), cid, null);

                                                        for (String gid : BingoRegistry.ids()) {
                                                            BingoService.refreshIfOpen(target, gid);
                                                        }

                                                        ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset challenge '" + cid + "' across ALL games for " + target.getName().getString()), false);
                                                        return 1;
                                                    })
                                            )
                                    )
                            )

                            // /bingo resetchallengeall <challengeId> [gameId]
                            .then(Commands.literal("resetchallengeall")
                                    .requires(src -> src.hasPermission(2))
                                    .then(Commands.argument("challengeId", StringArgumentType.word())

                                            .then(Commands.argument("gameId", StringArgumentType.word())
                                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BingoRegistry.ids(), builder))
                                                    .executes(ctx -> {
                                                        String cid = StringArgumentType.getString(ctx, "challengeId");
                                                        String gid = StringArgumentType.getString(ctx, "gameId");

                                                        BingoState state = BingoState.get(ctx.getSource().getLevel());
                                                        state.resetChallengeForAllPlayers(cid, gid);

                                                        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                            BingoService.refreshIfOpen(p, gid);
                                                        }

                                                        ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset challenge '" + cid + "' in '" + gid + "' for ALL players."), false);
                                                        return 1;
                                                    })
                                            )

                                            .executes(ctx -> {
                                                String cid = StringArgumentType.getString(ctx, "challengeId");
                                                BingoState state = BingoState.get(ctx.getSource().getLevel());
                                                state.resetChallengeForAllPlayers(cid, null);

                                                for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                    for (String gid : BingoRegistry.ids()) {
                                                        BingoService.refreshIfOpen(p, gid);
                                                    }
                                                }

                                                ctx.getSource().sendSuccess(() -> Component.literal("[Bingo] Reset challenge '" + cid + "' across ALL games for ALL players."), false);
                                                return 1;
                                            })
                                    )
                            )
            );
        });
    }
}