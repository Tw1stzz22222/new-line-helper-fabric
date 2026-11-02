package com.new_line_helper.gui;

import com.new_line_helper.ClientEventHandler;
import com.new_line_helper.DungeonAutomation;
import com.new_line_helper.HelperConfig;
import com.new_line_helper.ModState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/** M 键打开的菜单：切难度 + 启用。 */
public class GuiMenu extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 140;

    private int panelX;
    private int panelY;

    public GuiMenu() {
        super(Text.literal("New Line Helper"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int btnW = 160;
        int btnH = 22;
        int bx = (this.width - btnW) / 2;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("难度: " + HelperConfig.difficulty()), b -> {
                    HelperConfig.cycleDifficulty();
                    b.setMessage(Text.literal("难度: " + HelperConfig.difficulty()));
                }).dimensions(bx, panelY + 50, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("启用自动化"), b -> {
                    DungeonAutomation.reset();
                    ModState.start();
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(
                                Text.literal("[新一线] 自动化已开启").formatted(Formatting.GREEN), false);
                    }
                    if (this.client != null) {
                        this.client.setScreen(null);
                    }
                }).dimensions(bx, panelY + 78, btnW, btnH).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xCC101418);
        context.drawBorder(panelX, panelY, PANEL_W, PANEL_H, 0xFF2BB0A8);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Dungeon Automation").formatted(Formatting.BOLD, Formatting.AQUA),
                this.width / 2, panelY + 16, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Fabric 1.21.1 · 15c").formatted(Formatting.GRAY),
                this.width / 2, panelY + 30, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("= 终止    ` 调试").formatted(Formatting.DARK_GRAY),
                this.width / 2, panelY + PANEL_H - 14, 0xFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_EQUAL) {
            if (this.client != null) {
                ClientEventHandler.terminate(this.client);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
