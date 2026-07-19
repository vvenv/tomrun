# 汤姆猫跑酷（3D 像素风）

原生 Android 3D 无尽跑酷小游戏，类似《汤姆猫金币跑》的核心玩法。
纯 Kotlin + OpenGL ES 2.0 实现，无任何第三方依赖，像素/体素方块风格。

## 玩法

- 猫在三条跑道上自动向前跑；速度从 12 平滑爬升，约 70 秒接近上限 28
- **左右滑动**：换道
- **上滑 / 点击**：跳跃（跳过矮栏）
- **下滑**：铲滑（滑过高空横杆）
- 大木箱只能换道躲开；吃金币加分（1 金币 = 10 分）
- **施工跳台**：约 450 米后出现；两条道被木箱封住，需提前切到黄色斜坡
- **连击**：1.6 秒内连续吃金币；阈值 5 / 12 / 22 / 35 对应 x2~x5；
  倍率只加分，不虚增金币与钱包；超时或受击重置
- 障碍间距按反应时间缩放（约 1.7s → 0.95s），道具约每 7 波保底一次
- 撞上障碍游戏结束，最高分与统计自动保存

## 道具与索道

- **磁铁**（8s，可叠至 16s）：吸附附近金币
- **头盔**（最多 2 层）：每层抗一次撞击
- **加倍**（10s，可叠至 20s）：本局计分金币与里程翻倍（不刷钱包）
- **闪电冲刺**（6s，可叠至 12s）：速度 +25%，撞碎障碍（+25 分）
- **高空索道**：约 400 米后出现；对准绿色门架自动上索，免疫障碍

## 局内任务

每局随机抽取 3 个不重复任务（金币 / 距离 / 连击 / 跳跃 / 铲滑 / 撞碎），
目标与奖励按累计里程分新手 / 熟练 / 高手三档。
完成同时获得本局分数与**钱包金币**。

## 成就（5 类 × 铜银金）

| 类别 | 铜 / 银 / 金 | 奖励钱包 |
|------|-------------|---------|
| 金币收藏家 | 200 / 1000 / 5000 累计金币 | 100 / 250 / 500 |
| 长跑健将 | 2000 / 10000 / 50000 米 | 同上 |
| 任务达人 | 5 / 25 / 100 次任务 | 同上 |
| 连击大师 | 30 / 100 / 250 最高连击 | 同上 |
| 得分王 | 3000 / 8000 / 20000 最高分 | 同上 |

菜单「成就」面板可查看进度；共 15 级。

## 外观商店

跑酷中拾取金币与完成任务/成就会存入**钱包**（与本局计分金币分离）。
开始/结束界面进入「商店」解锁：

| 项目 | 价格 |
|------|------|
| 蓝灰猫 | 免费 |
| 橘黄 / 乌黑 / 粉红 | 300 / 800 / 1500 |
| 无尾迹 | 免费 |
| 青色 / 金色 / 彩虹尾迹 | 500 / 1200 / 2500 |

装备状态与钱包余额持久保存。

## 反馈与画面

- 程序化音效、分级振动、飘分、粒子、动态 FOV、速度线、镜头震动
- 天气（晴/雨/雪）与昼夜循环独立叠加；夜晚有月亮、星星与路灯
- HUD 使用 [Fusion Pixel](https://github.com/TakWolf/fusion-pixel-font) 12px 简体像素字体（OFL），字号取 12 的整数倍

## 构建与运行

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

最低支持 Android 7.0（API 24）。

## 代码结构

- [Game.kt](app/src/main/java/com/vvenv/tomrun/Game.kt) — 数值节奏、任务、成就、钱包商店、物理与生成
- [GameRenderer.kt](app/src/main/java/com/vvenv/tomrun/GameRenderer.kt) — OpenGL 体素渲染与尾迹
- [HudView.kt](app/src/main/java/com/vvenv/tomrun/HudView.kt) — HUD、商店/成就面板、手势
- [assets/fonts/](app/src/main/assets/fonts/) — Fusion Pixel 12px（zh_hans）
- [SoundFx.kt](app/src/main/java/com/vvenv/tomrun/SoundFx.kt) — 程序化音效
- [MainActivity.kt](app/src/main/java/com/vvenv/tomrun/MainActivity.kt) — 入口与振动
