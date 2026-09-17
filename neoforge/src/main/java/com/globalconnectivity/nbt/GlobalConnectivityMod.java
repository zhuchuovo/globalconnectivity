package com.globalconnectivity.nbt;

import com.globalconnectivity.nbt.client.ClientSetup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(GlobalConnectivityMod.MODID)
public final class GlobalConnectivityMod {
    public static final String MODID = "globalconnectivity";

    public GlobalConnectivityMod(IEventBus modEventBus) {
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ClientSetup::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        }
    }
}
