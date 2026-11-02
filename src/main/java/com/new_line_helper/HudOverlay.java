package com.new_line_helper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/** 右上角两个固定框：当前状态 + 最近的 LV 玩家信息。 */
public final class HudOverlay {

    private static final int MAX_LINES = 8;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_W = 600;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;
    private static final int BOX_GAP = 6;
    private static final int BOX_BG = 0xCC101418;
    private static final int BOX_BORDER = 0xFF2BB0A8;

    private static String stateText = "待命";
    private static final Deque<Text> lvLines = new LinkedList<>();

    private HudOverlay() {
    }

    public static void setState(String text) {
        stateText = text;
    }

    public static void addLv(Text text) {
        lvLines.addFirst(text.copy());
        while (lvLines.size() > MAX_LINES) {
            lvLines.removeLast();
        }
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }

        int right = client.getWindow().getScaledWidth() - 8;
        int y = 8;

        List<Text> stateLines = new ArrayList<>();
        stateLines.add(Text.literal(stateText).formatted(Formatting.AQUA));

        y = renderBox(context, client, stateLines, right, y, 1);
        renderBox(context, client, new ArrayList<>(lvLines), right, y, MAX_LINES);
    }

    private static int renderBox(DrawContext context, MinecraftClient client,
                                 List<Text> lines, int right, int y, int fixedLines) {
        int boxW = Math.min(BOX_W, right - 8);
        int x1 = right - boxW;
        int lineCount = Math.max(fixedLines, lines.size());
        int boxH = lineCount * LINE_HEIGHT + PAD_Y * 2;

        context.fill(x1, y, right, y + boxH, BOX_BG);
        context.drawBorder(x1, y, boxW, boxH, BOX_BORDER);

        int maxTextW = boxW - PAD_X * 2;
        int ty = y + PAD_Y;
        for (Text line : lines) {
            Text draw = line;
            if (client.textRenderer.getWidth(line) > maxTextW) {
                draw = Text.literal(client.textRenderer.trimToWidth(line.getString(), maxTextW));
            }
            context.drawTextWithShadow(client.textRenderer, draw, x1 + PAD_X, ty, 0xFFFFFF);
            ty += LINE_HEIGHT;
        }
        return y + boxH + BOX_GAP;
    }
}
