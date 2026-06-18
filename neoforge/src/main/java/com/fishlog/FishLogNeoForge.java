package com.fishlog;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Point d'entrée NeoForge — délègue toute la logique client à FishLogNeoForgeClient. */
@Mod("fishlog")
public class FishLogNeoForge {
    public FishLogNeoForge(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            FishLogNeoForgeClient.init(modEventBus);
        }
    }
}
