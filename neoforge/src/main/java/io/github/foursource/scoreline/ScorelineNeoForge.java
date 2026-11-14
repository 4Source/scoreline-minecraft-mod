package io.github.foursource.scoreline;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point. NeoForge creates this class when it loads the mod and passes in the mod's container.
 */
@Mod(Constants.MOD_ID)
public final class ScorelineNeoForge {

    public ScorelineNeoForge(ModContainer modContainer) {
        // The version comes from neoforge.mods.toml, which the build fills from gradle.properties.
        ScorelineCommon.init(modContainer.getModInfo().getVersion().toString());
    }
}
