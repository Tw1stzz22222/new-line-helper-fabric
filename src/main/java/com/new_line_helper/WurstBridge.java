package com.new_line_helper;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * 通过反射调用 Wurst Client 的命令处理器。
 *
 * Wurst 只在自己聊天界面的 ChatOutputEvent 里解析 "." 命令，直接 sendChatCommand 绕不过去，
 * 所以这里拿到 WurstClient.INSTANCE.getCmdProcessor().process(input)（input 不带前导点）。
 * Wurst 未安装时 isAvailable() 返回 false，调用方回退到按键飞行。
 */
public final class WurstBridge {

    private static boolean initialized = false;
    private static boolean available = false;
    private static Object processor = null;
    private static Method processMethod = null;

    private WurstBridge() {
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /** 执行一条 Wurst 命令（不带前导 "."），例如 "t Flight on"、"goto -45 14 10"。 */
    public static boolean run(String command) {
        init();
        if (!available) {
            return false;
        }
        try {
            processMethod.invoke(processor, command);
            return true;
        } catch (Throwable t) {
            NewLineHelperClient.LOG.warn("[wurst] command failed: .{} ({})", command, t.toString());
            return false;
        }
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("wurst")) {
            NewLineHelperClient.LOG.info("[wurst] not installed, falling back to key-based flight");
            return;
        }
        try {
            Class<?> wurstClass = Class.forName("net.wurstclient.WurstClient");
            Object wurst = wurstClass.getField("INSTANCE").get(null);
            Object cmdProcessor = wurstClass.getMethod("getCmdProcessor").invoke(wurst);
            processMethod = cmdProcessor.getClass().getMethod("process", String.class);
            processor = cmdProcessor;
            available = true;
            NewLineHelperClient.LOG.info("[wurst] command bridge ready");
        } catch (Throwable t) {
            NewLineHelperClient.LOG.warn("[wurst] bridge unavailable: {}", t.toString());
        }
    }
}
