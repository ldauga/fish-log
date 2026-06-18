package com.fishlog;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Point d'entrée Forge — mod client-only, délègue directement à FishLogForgeClient. */
@Mod("fishlog")
public class FishLogForge {
    public FishLogForge() {
        FishLogForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
