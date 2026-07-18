# 汤姆猫跑酷（3D）

原生 Android 3D 无尽跑酷小游戏，类似《汤姆猫金币跑》的核心玩法。
纯 Kotlin + OpenGL ES 2.0 实现，无任何第三方依赖，低多边形卡通风格。

## 玩法

- 猫在三条跑道上自动向前跑，速度越来越快
- **左右滑动**：换道
- **上滑 / 点击**：跳跃（跳过矮栏）
- **下滑**：铲滑（滑过高空横杆）
- 大木箱只能换道躲开；吃金币加分（1 金币 = 10 分）
- 撞上障碍游戏结束，最高分自动保存

## 构建与运行

需要 Android SDK（`local.properties` 里指向本机 SDK 路径）和 JDK 17+：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录运行。最低支持 Android 7.0（API 24）。

## 代码结构

- [Game.kt](app/src/main/java/com/vvenv/tomrun/Game.kt) — 游戏逻辑：跑道/跳跃/铲滑物理、障碍与金币生成、碰撞判定、计分
- [GameRenderer.kt](app/src/main/java/com/vvenv/tomrun/GameRenderer.kt) — OpenGL ES 渲染：跟随相机、光照与距离雾、猫模型动画、场景绘制
- [Mesh.kt](app/src/main/java/com/vvenv/tomrun/Mesh.kt) — 基础几何体（立方体/球/圆柱/圆锥）
- [HudView.kt](app/src/main/java/com/vvenv/tomrun/HudView.kt) — 中文 HUD 与滑动手势识别
- [MainActivity.kt](app/src/main/java/com/vvenv/tomrun/MainActivity.kt) — 入口，GLSurfaceView + HUD 叠加
