package com.new_line_helper;

/**
 * 所有跟具体服务器绑定的东西都集中在这里：菜单文案、聊天关键词、航点、时序、扫描参数。
 * 换服或服务器改文案时，原则上只需要改这个文件。
 */
public final class HelperConfig {

    private HelperConfig() {
    }

    // ===== 菜单按钮文案 =====
    public static final String BTN_CHALLENGE = "独立副本挑战";
    public static final String BTN_FLOOR = "第十五层";
    public static final String BTN_ENTER = "进入副本";

    // ===== 难度选项：M 菜单里循环切换，数组顺序就是切换顺序 =====
    private static final String[] DIFFICULTIES = {"湮灭", "简单"};
    private static int difficultyIndex = 0;

    public static String difficulty() {
        return DIFFICULTIES[difficultyIndex];
    }

    public static void cycleDifficulty() {
        difficultyIndex = (difficultyIndex + 1) % DIFFICULTIES.length;
    }

    // ===== 聊天关键词（服务器改文案时改这里） =====
    public static final String KEY_KILL = "成功击杀";
    public static final String KEY_REWARD = "通关奖励已放入背包";
    public static final String KEY_LEAVE = "点击立即离开副本";
    public static final String KEY_BUSY = "同一时间";
    public static final String KEY_LAUNCH = "已获得启动名额";
    public static final String KEY_FREQUENT = "同一时刻地牢创建频繁";
    public static final String KEY_LV = "LV";

    // ===== 航点坐标 =====
    public static final int A_X = -45, A_Y = 14, A_Z = 10;
    public static final int B_X = -4, B_Y = 14, B_Z = 11;
    public static final int C_X = -5, C_Y = 14, C_Z = -8;

    // ===== 时序（毫秒） =====
    public static final long WAIT_AFTER_ENTER = 3000L;   // 点“进入副本”后等待判定
    public static final long RETRY_WAIT = 12000L;        // 进入失败后重试
    public static final long WAIT_AT_A = 1000L;          // A 点停留
    public static final long WAIT_AT_B = 8000L;          // B 点停留
    public static final long REWARD_TIMEOUT = 60000L;    // 等奖励的兜底
    public static final long LEAVE_WAIT = 10000L;        // 点离开链接后等待
    public static final long LEAVE_TIMEOUT = 8000L;      // 没点到离开链接的兜底
    public static final long CLICK_DELAY = 600L;         // 两次点击的最小间隔
    public static final long MENU_CLICK_TIMEOUT = 5000L; // 单步菜单点击超时
    public static final long BUSY_WAIT = 10000L;         // 副本繁忙等待
    public static final long SIGN_SCAN_INTERVAL = 1000L; // 找告示牌扫描间隔
    public static final int SIGN_MISS_LIMIT = 3;         // 连续扫不到 → 视为已在副本内

    // ===== 扫描 / 到达参数 =====
    public static final int SIGN_RADIUS = 6;             // 告示牌水平扫描半径
    public static final double ARRIVE_DIST = 2.0;        // 到点判定距离

    // ===== 容器菜单（物品形式）=====
    /** 是否允许点击玩家自己背包区域的格子。菜单物品通常在容器区，默认只先找容器区，兜底再找背包。 */
    public static final boolean CLICK_PLAYER_SLOTS = true;
    /** 物品名字对不上时，是否再匹配物品 lore（很多菜单把选项名写在 lore 里）。 */
    public static final boolean MATCH_LORE = true;
    /** lore 匹配的限流间隔，避免每 tick 拼 tooltip。 */
    public static final long LORE_SCAN_INTERVAL = 250L;
}
