package com.new_line_helper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewLineHelperClient implements ClientModInitializer {

    public static final String MOD_ID = "new_line_helper";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        KeyBindings.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEventHandler.onClientTick(client));
        ChatListener.register();

        LOG.info("[new_line_helper] 客户端已加载（MC 1.21.1 / Fabric）");
    }
}
