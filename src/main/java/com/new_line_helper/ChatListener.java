package com.new_line_helper;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

/** 解析聊天关键词，驱动状态机。 */
public final class ChatListener {

    private ChatListener() {
    }

    public static void register() {
        // 系统消息（副本提示、奖励、离开链接等）
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message));
        // 玩家聊天（带 LV 前缀的玩家信息可能走这里）
        ClientReceiveMessageEvents.CHAT.register(
                (message, signed, sender, params, timestamp) -> onChat(message));
    }

    private static void onChat(Text message) {
        String msg = message.getString();

        if (!ModState.running) {
            return;
        }

        if (msg.contains(HelperConfig.KEY_KILL)) {
            DungeonAutomation.killCount++;
            NewLineHelperClient.LOG.info("[auto] kill (total {})", DungeonAutomation.killCount);
        }
        if (msg.contains(HelperConfig.KEY_REWARD) && DungeonAutomation.isInRun()) {
            DungeonAutomation.rewardSeen = true;
            NewLineHelperClient.LOG.info("[auto] reward detected");
        }
        if (msg.contains(HelperConfig.KEY_BUSY)) {
            DungeonAutomation.busySeen = true;
            NewLineHelperClient.LOG.info("[auto] busy detected");
        }
        if (msg.contains(HelperConfig.KEY_LAUNCH)) {
            DungeonAutomation.launchSeen = true;
            NewLineHelperClient.LOG.info("[auto] launch detected");
        }
        if (msg.contains(HelperConfig.KEY_FREQUENT)) {
            DungeonAutomation.frequentSeen = true;
            NewLineHelperClient.LOG.info("[auto] frequent create detected");
        }

        ClickEvent leave = findClickEvent(message, HelperConfig.KEY_LEAVE);
        if (leave != null && leave.getAction() == ClickEvent.Action.RUN_COMMAND
                && DungeonAutomation.isInRun()) {
            // 先记下来，到 LEAVE 阶段再发；值里通常带前导 "/"，发送时会去掉
            DungeonAutomation.leaveCommand = leave.getValue();
            NewLineHelperClient.LOG.info("[auto] leave link captured: {}", leave.getValue());
        }
    }

    private static ClickEvent findClickEvent(Text comp, String text) {
        ClickEvent own = comp.getStyle().getClickEvent();
        if (own != null && comp.getString().contains(text)) {
            return own;
        }
        for (Text sibling : comp.getSiblings()) {
            ClickEvent found = findClickEvent(sibling, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
