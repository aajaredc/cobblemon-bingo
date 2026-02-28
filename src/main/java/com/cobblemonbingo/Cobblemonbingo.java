package com.cobblemonbingo;

import com.cobblemonbingo.bingo.BingoCommand;
import com.cobblemonbingo.bingo.BingoConfigManager;
import com.cobblemonbingo.bingo.BingoEvents;
import com.cobblemonbingo.bingo.BingoRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cobblemonbingo implements ModInitializer {
	public static final String MOD_ID = "cobblemon-bingo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BingoConfigManager.bootstrapDefaultIfMissing();
		BingoRegistry.loadAll();
		BingoEvents.register();
		BingoCommand.register();
		LOGGER.info("Cobblemon Bingo initialized.");
	}
}