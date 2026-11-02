# New Line Helper (Fabric 1.21.1)

「新一线」15c 副本自动化的 **1.21.1 Fabric** 移植版，客户端 mod。

> 这是从 1.12.2 Forge 工程 [new-line-helper](../new-line-helper) 独立出来的新项目，
> 原工程保留不动。逻辑（状态机、时序、容错）照搬，API 换成 Fabric + 1.21.1。

## 状态

- [x] 工程骨架（Loom / yarn 1.21.1 / Java 21 / Fabric Loader 0.19.3）
- [x] 状态机、飞行、聊天监听、菜单全部移植（右上角 HUD 已按需求移除）
- [ ] 服务器文案适配：**新服如果改过文案/菜单结构，需要按 `HelperConfig` 调整**
- [ ] 进游戏实测（需要 1.21.1 服务器 + 告示牌/菜单环境）

## 构建

```powershell
.\gradlew build
```

产物：`build/libs/new_line_helper-1.0.0.jar`

环境要求：JDK 21（`gradle.properties` 里 `org.gradle.java.home` 指向本机 JDK21，换机器要改）。

## 用法

| 按键 | 作用 |
|---|---|
| `N` | 打开菜单（切难度 / 启用自动化） |
| `=` | 终止自动化 |
| `` ` `` | 把当前界面的按钮和槽位输出到聊天栏和日志 |

启用后自动跑：找告示牌 → 独立副本挑战 → 第十五层 → 难度 → 进入副本 → 飞 A → 飞 B(停 8s) → 飞 C → 等奖励 → 点离开 → 下一轮。

## 目录

```
src/main/java/com/new_line_helper/
  NewLineHelperClient.java   入口（ClientModInitializer）
  HelperConfig.java          所有服务器文案 / 航点 / 时序（改服只改这里）
  DungeonAutomation.java     状态机核心
  ChatListener.java          聊天关键词 → 状态
  ClientEventHandler.java    按键 + 每 tick 驱动
  KeyBindings.java           按键注册
  ModState.java              总开关
  gui/GuiMenu.java           N 键菜单
```

## 飞行（Wurst 优先）

- 装了 Wurst（客户端检测到 mod id `wurst`）时：
  - 进入飞行段：`.t Flight on`
  - 每段移动：`.goto <x> <y> <z>`（`GoToCmd` 的语法就是 x y z）
  - 到点：无参 `.goto` 停止寻路
  - 本轮结束 / 按终止键：`.t Flight off`（若当时还在空中，落地后会在找告示牌阶段补关）
- 没装 Wurst：自动退回原来的按键飞行（改 yaw + 前进/跳跃/潜行）。
- `.goto` 发出去 15 秒还没到点会重发一次，避免寻路卡死后原地发呆。

## 与 1.12.2 版的关键差异

- 不再有 SRG 混淆反射：按钮直接遍历 `Screen.children()`；**菜单是容器物品时**按槽位点击，匹配顺序为「容器区物品名 → 玩家背包物品名（可关）→ 物品 lore（限流）」
- 飞行默认交给 Wurst 的 Flight + GoTo 命令，没有 Wurst 时退回按键飞行
- 奖励/离开消息仍然只在 C 点/离开阶段接受（防止上一轮消息串场）
