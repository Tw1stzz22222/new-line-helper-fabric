package com.new_line_helper;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** 按键：M 开菜单、= 终止、` 输出当前界面信息。 */
public final class KeyBindings {

    private KeyBindings() {
    }

    public static final String CATEGORY = "key.categories.new_line_helper";

    public static KeyBinding OPEN_MENU;
    public static KeyBinding TERMINATE;
    public static KeyBinding DEBUG;

    public static void register() {
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.new_line_helper.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        TERMINATE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.new_line_helper.terminate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CATEGORY));
        DEBUG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.new_line_helper.debug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY));
    }
}
