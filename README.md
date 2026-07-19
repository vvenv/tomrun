# 汤姆猫跑酷（3D 像素风）

原生 Android 3D 无尽跑酷小游戏，类似《汤姆猫金币跑》的核心玩法。
纯 Kotlin + OpenGL ES 2.0 实现，无任何第三方依赖，像素/体素方块风格。

## 玩法

- 猫在三条跑道上自动向前跑，速度越来越快
- **左右滑动**：换道
- **上滑 / 点击**：跳跃（跳过矮栏）
- **下滑**：铲滑（滑过高空横杆）
- 大木箱只能换道躲开；吃金币加分（1 金币 = 10 分）
- 撞上障碍游戏结束，最高分自动保存

## 道具与索道

- **磁铁**（红色 U 形，8 秒）：自动吸附附近各条道上的金币
- **头盔**（黄色安全帽）：抗一次撞击，戴在头上直到碎掉
- **加倍**（紫色宝石，10 秒）：金币与里程得分翻倍
- **高空索道**：随机出现、长度随机（30~80 米）。在地面对准
  绿色入口门架所在的跑道即可自动抓上索道，滑行期间免疫一切障碍，
  钢缆下还挂着一排金币；到出口自动落下，左右滑/下滑可提前下索道
- 左上角 HUD 实时显示生效中的道具和滑索状态

## 画面

体素方块世界：方块猫（阶梯尾巴、方块耳朵、白爪子）、方块树、方块云、
旋转金币块、远山台地，8-bit 配色。渲染上有 4x MSAA 抗锯齿、
半球环境光 + 方向光、距离雾、软阴影贴地投影。

## 构建与运行

需要 Android SDK（`local.properties` 里指向本机 SDK 路径）和 JDK 17+：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录运行。最低支持 Android 7.0（API 24）。

## 代码结构

- [Game.kt](app/src/main/java/com/vvenv/tomrun/Game.kt) — 游戏逻辑：跑道/跳跃/铲滑物理、障碍与金币生成、碰撞判定、计分
- [GameRenderer.kt](app/src/main/java/com/vvenv/tomrun/GameRenderer.kt) — OpenGL ES 体素渲染：跟随相机、光照与距离雾、方块猫动画、场景绘制
- [Mesh.kt](app/src/main/java/com/vvenv/tomrun/Mesh.kt) — 立方体网格
- [HudView.kt](app/src/main/java/com/vvenv/tomrun/HudView.kt) — 中文 HUD 与滑动手势识别
- [MainActivity.kt](app/src/main/java/com/vvenv/tomrun/MainActivity.kt) — 入口，GLSurfaceView(MSAA) + HUD 叠加
