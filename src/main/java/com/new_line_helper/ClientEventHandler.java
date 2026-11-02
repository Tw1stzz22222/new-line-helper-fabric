package com.new_line_helper;

import com.new_line_helper.gui.GuiMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 按键与每 tick 驱动。 */
public final class ClientEventHandler {

    private ClientEventHandler() {
    }

    public static void onClientTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        while (KeyBindings.OPEN_MENU.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new GuiMenu());
            }
        }
        while (KeyBindings.TERMINATE.wasPressed()) {
            terminate(client);
        }
        while (KeyBindings.DEBUG.wasPressed()) {
            DungeonAutomation.dumpScreen(client);
        }

        if (ModState.running) {
            DungeonAutomation.tick(client);
        }
    }

    public static void terminate(MinecraftClient client) {
        ModState.terminate();
        DungeonAutomation.releaseFlightKeys(client);
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[新一线] 自动化已终止").formatted(Formatting.RED), false);
        }
    }
}
