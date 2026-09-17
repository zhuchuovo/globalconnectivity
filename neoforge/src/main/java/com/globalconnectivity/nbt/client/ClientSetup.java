package com.globalconnectivity.nbt.client;

import com.globalconnectivity.nbt.client.screen.NbtManagerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private static final KeyMapping OPEN_MANAGER = new KeyMapping(
            "key.globalconnectivity.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.globalconnectivity");

    private ClientSetup() {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MANAGER);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_MANAGER.consumeClick()) {
            Minecraft.getInstance().setScreen(new NbtManagerScreen());
        }
    }
}
