package io.github.foursource.scoreline;

import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point. NeoForge creates this class when it loads the mod.
 */
@Mod(Constants.MOD_ID)
public final class ScorelineNeoForge {

    public ScorelineNeoForge() {
        // Use NeoForge to bootstrap the Common mod.
        ScorelineCommon.LOG.info("Hello NeoForge world!");
        ScorelineCommon.init();
    }
}
