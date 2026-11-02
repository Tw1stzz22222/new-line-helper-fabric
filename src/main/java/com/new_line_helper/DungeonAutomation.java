package com.new_line_helper;

import net.minecraft.block.AbstractSignBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 15c 副本自动化的状态机。
 * 逻辑与 1.12.2 版一致，只是换成了 1.21.1 的客户端 API：
 *  - 反射挖 buttonList → 直接遍历 Screen.children()
 *  - displayGuiScreen(null) → client.setScreen(null)
 *  - processRightClickBlock → interactionManager.interactBlock
 *  - windowClick → interactionManager.clickSlot
 */
public final class DungeonAutomation {

    private DungeonAutomation() {
    }

    public enum State {
        FIND_SIGN, SIGN_MENU, WAIT_BUSY, CHALLENGE_FLOOR, CHALLENGE_DIFFICULTY, CHALLENGE_ENTER,
        ENTERING, WAIT_RETRY, FLY_TO_A, FLY_TO_B, FLY_TO_C, LEAVE, FLY_OFF
    }

    private static State state = State.FIND_SIGN;
    private static long stateStartTime = 0L;
    private static long lastClickTime = 0L;
    private static long leaveSentTime = 0L;
    private static long arrivedTime = 0L;
    private static long lastSignScanTime = 0L;
    private static int signMissCount = 0;

    // 聊天信号，由 ChatListener 更新
    public static int killCount = 0;
    public static boolean rewardSeen = false;
    public static boolean leaveSent = false;
    public static boolean busySeen = false;
    public static boolean launchSeen = false;
    public static boolean frequentSeen = false;

    private static boolean flyingKnown = false;
    private static boolean lastFlying = false;
    private static String lastDumpTarget = null;
    private static long lastLoreScan = 0L;

    public static void reset() {
        state = State.FIND_SIGN;
        stateStartTime = 0L;
        lastClickTime = 0L;
        leaveSentTime = 0L;
        arrivedTime = 0L;
        lastSignScanTime = 0L;
        signMissCount = 0;
        killCount = 0;
        rewardSeen = false;
        leaveSent = false;
        busySeen = false;
        launchSeen = false;
        frequentSeen = false;
        NewLineHelperClient.LOG.info("[auto] reset -> FIND_SIGN");
        HudOverlay.setState(describeState(State.FIND_SIGN));
    }

    private static void setState(State s) {
        if (s != state) {
            NewLineHelperClient.LOG.info("[auto] state {} -> {}", state, s);
            HudOverlay.setState(describeState(s));
        }
        state = s;
        stateStartTime = System.currentTimeMillis();
    }

    private static String describeState(State s) {
        return switch (s) {
            case FIND_SIGN -> "寻找副本告示牌...";
            case SIGN_MENU -> "打开副本挑战菜单";
            case WAIT_BUSY -> "副本繁忙，等待重试";
            case CHALLENGE_FLOOR -> "选择楼层：" + HelperConfig.BTN_FLOOR;
            case CHALLENGE_DIFFICULTY -> "选择难度：" + HelperConfig.difficulty();
            case CHALLENGE_ENTER -> "点击进入副本";
            case ENTERING -> "等待进入副本结果...";
            case WAIT_RETRY -> "进入失败，等待重试";
            case FLY_TO_A -> "飞行前往 A 点";
            case FLY_TO_B -> "飞行前往 B 点";
            case FLY_TO_C -> "飞行前往 C 点";
            case LEAVE -> "等待离开副本";
            case FLY_OFF -> "本轮结束，准备下一轮";
        };
    }

    /** 只有到 C 点/离开阶段才接受奖励和离开链接，避免上一轮的迟到消息串到下一轮。 */
    public static boolean isAtRewardPhase() {
        return state == State.FLY_TO_C || state == State.LEAVE;
    }

    private static boolean isFlyingState() {
        return state == State.FLY_TO_A || state == State.FLY_TO_B || state == State.FLY_TO_C;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        long now = System.currentTimeMillis();

        // 飞行阶段自动关闭意外打开的界面（背包/设置/聊天），否则移动会被暂停
        if (isFlyingState() && client.currentScreen != null) {
            NewLineHelperClient.LOG.info("[auto] closing unexpected GUI {} during flight",
                    client.currentScreen.getClass().getSimpleName());
            client.setScreen(null);
        }

        switch (state) {
            case FIND_SIGN: {
                if (client.currentScreen != null) {
                    setState(State.SIGN_MENU);
                    break;
                }
                if (now - lastSignScanTime < HelperConfig.SIGN_SCAN_INTERVAL) {
                    break;
                }
                lastSignScanTime = now;
                BlockPos sign = findNearestSign(client);
                if (sign != null) {
                    signMissCount = 0;
                    NewLineHelperClient.LOG.info("[auto] right-clicking sign at {}", sign);
                    rightClickBlock(client, sign);
                    setState(State.SIGN_MENU);
                    break;
                }
                signMissCount++;
                NewLineHelperClient.LOG.warn("[auto] sign not found ({}/{}), will assume already inside at limit",
                        signMissCount, HelperConfig.SIGN_MISS_LIMIT);
                if (signMissCount >= HelperConfig.SIGN_MISS_LIMIT) {
                    // 已经进副本但没等到成功信号时，人被传送走，附近就没有告示牌了。
                    NewLineHelperClient.LOG.warn("[auto] no sign after {} scans, assume already inside -> FLY_TO_A",
                            HelperConfig.SIGN_MISS_LIMIT);
                    signMissCount = 0;
                    rewardSeen = false;
                    leaveSent = false;
                    killCount = 0;
                    launchSeen = false;
                    frequentSeen = false;
                    arrivedTime = 0L;
                    notifyPlayer(client, "未找到告示牌，按已进入副本处理，开始飞往 A 点", Formatting.YELLOW);
                    setState(State.FLY_TO_A);
                    break;
                }
                HudOverlay.setState("未找到告示牌 " + signMissCount + "/" + HelperConfig.SIGN_MISS_LIMIT);
                break;
            }
            case SIGN_MENU: {
                if (busySeen) {
                    busySeen = false;
                    NewLineHelperClient.LOG.info("[auto] dungeon busy, retry in {}s", HelperConfig.BUSY_WAIT / 1000);
                    setState(State.WAIT_BUSY);
                    break;
                }
                if (client.currentScreen != null && clickByText(client, client.currentScreen, HelperConfig.BTN_CHALLENGE)) {
                    setState(State.CHALLENGE_FLOOR);
                    break;
                }
                if (now - stateStartTime >= HelperConfig.MENU_CLICK_TIMEOUT) {
                    NewLineHelperClient.LOG.info("[auto] SIGN_MENU timeout, closing menus and restarting");
                    closeAllMenus(client);
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case WAIT_BUSY: {
                if (now - stateStartTime >= HelperConfig.BUSY_WAIT) {
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case CHALLENGE_FLOOR: {
                if (client.currentScreen != null && clickByText(client, client.currentScreen, HelperConfig.BTN_FLOOR)) {
                    setState(State.CHALLENGE_DIFFICULTY);
                    break;
                }
                if (now - stateStartTime >= HelperConfig.MENU_CLICK_TIMEOUT) {
                    NewLineHelperClient.LOG.info("[auto] CHALLENGE_FLOOR timeout, closing menus and restarting");
                    closeAllMenus(client);
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case CHALLENGE_DIFFICULTY: {
                if (client.currentScreen != null
                        && clickByText(client, client.currentScreen, HelperConfig.difficulty())) {
                    setState(State.CHALLENGE_ENTER);
                    break;
                }
                if (now - stateStartTime >= HelperConfig.MENU_CLICK_TIMEOUT) {
                    NewLineHelperClient.LOG.info("[auto] CHALLENGE_DIFFICULTY timeout, closing menus and restarting");
                    closeAllMenus(client);
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case CHALLENGE_ENTER: {
                if (client.currentScreen != null && clickByText(client, client.currentScreen, HelperConfig.BTN_ENTER)) {
                    launchSeen = false;
                    frequentSeen = false;
                    setState(State.ENTERING);
                    break;
                }
                if (now - stateStartTime >= HelperConfig.MENU_CLICK_TIMEOUT) {
                    NewLineHelperClient.LOG.info("[auto] CHALLENGE_ENTER timeout, closing menus and restarting");
                    closeAllMenus(client);
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case ENTERING: {
                if (now - stateStartTime >= HelperConfig.WAIT_AFTER_ENTER) {
                    if (launchSeen && !frequentSeen) {
                        NewLineHelperClient.LOG.info("[auto] enter success, flying to A");
                        rewardSeen = false;
                        leaveSent = false;
                        killCount = 0;
                        setState(State.FLY_TO_A);
                    } else {
                        NewLineHelperClient.LOG.info("[auto] enter failed (launch={}, frequent={}), retry in {}s",
                                launchSeen, frequentSeen, HelperConfig.RETRY_WAIT / 1000);
                        launchSeen = false;
                        frequentSeen = false;
                        setState(State.WAIT_RETRY);
                    }
                }
                break;
            }
            case WAIT_RETRY: {
                if (now - stateStartTime >= HelperConfig.RETRY_WAIT) {
                    setState(State.FIND_SIGN);
                }
                break;
            }
            case FLY_TO_A: {
                if (near(client, HelperConfig.A_X, HelperConfig.A_Y, HelperConfig.A_Z)) {
                    releaseFlightKeys(client);
                    if (arrivedTime == 0L) {
                        arrivedTime = now;
                    }
                    if (now - arrivedTime >= HelperConfig.WAIT_AT_A) {
                        arrivedTime = 0L;
                        setState(State.FLY_TO_B);
                    }
                } else {
                    flyToward(client, HelperConfig.A_X, HelperConfig.A_Y, HelperConfig.A_Z);
                }
                break;
            }
            case FLY_TO_B: {
                if (near(client, HelperConfig.B_X, HelperConfig.B_Y, HelperConfig.B_Z)) {
                    releaseFlightKeys(client);
                    if (arrivedTime == 0L) {
                        arrivedTime = now;
                    }
                    if (now - arrivedTime >= HelperConfig.WAIT_AT_B) {
                        arrivedTime = 0L;
                        setState(State.FLY_TO_C);
                    }
                } else {
                    flyToward(client, HelperConfig.B_X, HelperConfig.B_Y, HelperConfig.B_Z);
                }
                break;
            }
            case FLY_TO_C: {
                if (rewardSeen || leaveSent || now - stateStartTime >= HelperConfig.REWARD_TIMEOUT) {
                    NewLineHelperClient.LOG.info("[auto] FLY_TO_C exit: reward={} leave={} timeout={}",
                            rewardSeen, leaveSent, now - stateStartTime >= HelperConfig.REWARD_TIMEOUT);
                    releaseFlightKeys(client);
                    setState(State.LEAVE);
                } else if (!near(client, HelperConfig.C_X, HelperConfig.C_Y, HelperConfig.C_Z)) {
                    flyToward(client, HelperConfig.C_X, HelperConfig.C_Y, HelperConfig.C_Z);
                } else {
                    releaseFlightKeys(client);
                }
                break;
            }
            case LEAVE: {
                if (leaveSent && leaveSentTime == 0L) {
                    leaveSentTime = now;
                }
                boolean waited = leaveSent && (now - leaveSentTime >= HelperConfig.LEAVE_WAIT);
                if (waited || (!leaveSent && now - stateStartTime >= HelperConfig.LEAVE_TIMEOUT)) {
                    setState(State.FLY_OFF);
                }
                break;
            }
            case FLY_OFF: {
                releaseFlightKeys(client);
                reset();
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 飞行
    // ------------------------------------------------------------------

    private static boolean near(MinecraftClient client, int x, int y, int z) {
        ClientPlayerEntity p = client.player;
        return p != null && p.squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5)
                < HelperConfig.ARRIVE_DIST * HelperConfig.ARRIVE_DIST;
    }

    private static void flyToward(MinecraftClient client, int tx, int ty, int tz) {
        ClientPlayerEntity p = client.player;
        if (p == null) {
            return;
        }

        boolean flying = p.getAbilities().flying;
        if (!flyingKnown || flying != lastFlying) {
            flyingKnown = true;
            lastFlying = flying;
            NewLineHelperClient.LOG.warn("[auto] flight state = {}", flying);
            if (!flying) {
                notifyPlayer(client, "[飞行] 检测到未在飞行，自动化飞行可能无效", Formatting.RED);
            }
        }

        double dx = (tx + 0.5) - p.getX();
        double dy = (ty + 0.5) - p.getY();
        double dz = (tz + 0.5) - p.getZ();

        p.setYaw((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F);

        setKey(client.options.forwardKey, true);
        if (dy > 1.0) {
            setKey(client.options.jumpKey, true);
            setKey(client.options.sneakKey, false);
        } else if (dy < -1.0) {
            setKey(client.options.jumpKey, false);
            setKey(client.options.sneakKey, true);
        } else {
            setKey(client.options.jumpKey, false);
            setKey(client.options.sneakKey, false);
        }
    }

    public static void releaseFlightKeys(MinecraftClient client) {
        if (client == null) {
            return;
        }
        setKey(client.options.forwardKey, false);
        setKey(client.options.jumpKey, false);
        setKey(client.options.sneakKey, false);
    }

    private static void setKey(KeyBinding kb, boolean pressed) {
        if (kb == null) {
            return;
        }
        InputUtil.Key key = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(key, pressed);
    }

    // ------------------------------------------------------------------
    // 界面操作
    // ------------------------------------------------------------------

    private static void closeAllMenus(MinecraftClient client) {
        int guard = 0;
        while (client.currentScreen != null && guard++ < 8) {
            client.setScreen(null);
        }
    }

    private static BlockPos findNearestSign(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.world == null) {
            return null;
        }
        BlockPos center = p.getBlockPos();
        int r = HelperConfig.SIGN_RADIUS;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(center.add(-r, -3, -r), center.add(r, 3, r))) {
            if (!(client.world.getBlockState(pos).getBlock() instanceof AbstractSignBlock)) {
                continue;
            }
            double d = p.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = pos.toImmutable();
            }
        }
        if (best == null) {
            NewLineHelperClient.LOG.debug("[auto] no sign within radius {}", r);
        }
        return best;
    }

    private static void rightClickBlock(MinecraftClient client, BlockPos pos) {
        ClientPlayerEntity p = client.player;
        if (p == null || client.interactionManager == null) {
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d dir = center.subtract(eye).normalize();
        Direction side = Direction.getFacing(dir.x, dir.y, dir.z);
        BlockHitResult hit = new BlockHitResult(center, side, pos, false);
        client.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    private static boolean clickByText(MinecraftClient client, Screen screen, String text) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < HelperConfig.CLICK_DELAY) {
            return false;
        }

        for (Element element : screen.children()) {
            if (!(element instanceof ButtonWidget button)) {
                continue;
            }
            if (!button.visible || !button.active) {
                continue;
            }
            String label = button.getMessage().getString();
            if (label.contains(text)) {
                NewLineHelperClient.LOG.info("[auto] clicking '{}' on {}",
                        label, screen.getClass().getSimpleName());
                button.onPress();
                lastClickTime = System.currentTimeMillis();
                return true;
            }
        }

        if (clickSlotByText(client, screen, text)) {
            lastClickTime = System.currentTimeMillis();
            return true;
        }

        dumpMenu(client, screen, text);
        return false;
    }

    private static boolean clickSlotByText(MinecraftClient client, Screen screen, String text) {
        if (!(screen instanceof HandledScreen<?> handled)) {
            return false;
        }
        if (client.player == null || client.interactionManager == null) {
            return false;
        }
        ScreenHandler handler = handled.getScreenHandler();

        // 先找容器区，避免误点自己背包里的同名物品；找不到再按配置找背包
        Slot slot = findSlot(handler, text, client, false, false);
        if (slot == null && HelperConfig.CLICK_PLAYER_SLOTS) {
            slot = findSlot(handler, text, client, false, true);
        }
        // 名字对不上时再匹配 lore（限流，避免每 tick 拼 tooltip）
        if (slot == null && HelperConfig.MATCH_LORE) {
            long now = System.currentTimeMillis();
            if (now - lastLoreScan >= HelperConfig.LORE_SCAN_INTERVAL) {
                lastLoreScan = now;
                slot = findSlot(handler, text, client, true, false);
                if (slot == null && HelperConfig.CLICK_PLAYER_SLOTS) {
                    slot = findSlot(handler, text, client, true, true);
                }
            }
        }
        if (slot == null) {
            return false;
        }

        NewLineHelperClient.LOG.info("[auto] clicking slot {} '{}' for '{}'",
                slot.id, slot.getStack().getName().getString(), text);
        client.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, client.player);
        return true;
    }

    private static Slot findSlot(ScreenHandler handler, String text, MinecraftClient client,
                                 boolean useLore, boolean playerSlots) {
        for (Slot slot : handler.slots) {
            boolean isPlayerSlot = slot.inventory instanceof PlayerInventory;
            if (isPlayerSlot != playerSlots) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (stackMatches(stack, text, client, useLore)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean stackMatches(ItemStack stack, String text, MinecraftClient client, boolean useLore) {
        if (stack.getName().getString().contains(text)) {
            return true;
        }
        if (!useLore) {
            return false;
        }
        for (Text line : stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC)) {
            if (line.getString().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static void dumpMenu(MinecraftClient client, Screen screen, String target) {
        if (target.equals(lastDumpTarget)) {
            return;
        }
        lastDumpTarget = target;
        NewLineHelperClient.LOG.warn("[auto] no '{}' on {} ({} children)",
                target, screen.getClass().getSimpleName(), screen.children().size());
        for (Element element : screen.children()) {
            if (element instanceof ButtonWidget button) {
                NewLineHelperClient.LOG.warn("[auto]   button '{}' at {},{} {}x{} visible={} active={}",
                        button.getMessage().getString(), button.getX(), button.getY(),
                        button.getWidth(), button.getHeight(), button.visible, button.active);
            } else {
                NewLineHelperClient.LOG.warn("[auto]   child {}", element.getClass().getSimpleName());
            }
        }
        if (screen instanceof HandledScreen<?> handled) {
            for (Slot slot : handled.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) {
                    continue;
                }
                NewLineHelperClient.LOG.warn("[auto]   slot {} '{}'", slot.id, stack.getName().getString());
                List<Text> tip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
                for (int i = 1; i < tip.size(); i++) {
                    NewLineHelperClient.LOG.warn("[auto]     lore: '{}'", tip.get(i).getString());
                }
            }
        }
    }

    /** ` 键：把当前界面的按钮/槽位打到日志和聊天栏，方便适配新服的菜单。 */
    public static void dumpScreen(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen == null) {
            NewLineHelperClient.LOG.info("[auto] dump: no screen open");
            notifyPlayer(client, "[调试] 没有打开的界面", Formatting.YELLOW);
            return;
        }
        NewLineHelperClient.LOG.info("[auto] dump: screen={}", screen.getClass().getName());
        notifyPlayer(client, "[调试] 界面=" + screen.getClass().getSimpleName(), Formatting.YELLOW);

        for (Element element : screen.children()) {
            if (element instanceof ButtonWidget button) {
                String line = "[调试] 按钮 '" + button.getMessage().getString() + "' "
                        + button.getX() + "," + button.getY()
                        + " " + button.getWidth() + "x" + button.getHeight();
                NewLineHelperClient.LOG.info("[auto] dump: {}", line);
                notifyPlayer(client, line, Formatting.GRAY);
            }
        }

        if (screen instanceof HandledScreen<?> handled) {
            for (Slot slot : handled.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) {
                    continue;
                }
                String line = "[调试] 格子 " + slot.id + " '" + stack.getName().getString() + "'";
                NewLineHelperClient.LOG.info("[auto] dump: {}", line);
                notifyPlayer(client, line, Formatting.GRAY);
            }
        }
    }

    private static void notifyPlayer(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(color), false);
        }
    }
}
