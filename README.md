# 汤姆猫跑酷

> 三道换线，穿越六大宇宙。

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://run.edao.plus)
[![Website](https://img.shields.io/badge/官网-run.edao.plus-E8B43A)](https://run.edao.plus)
[![Kotlin](https://img.shields.io/badge/Kotlin-OpenGL%20ES%202.0-7F52FF?logo=kotlin&logoColor=white)](https://github.com/vvenv/tomrun)

原生 Android 3D 像素跑酷。猫在三条跑道上自己往前跑，左右换道、跳跃铲滑；穿过传送门，还能在路上捡到中国文物。

**免费、无广告、无内购。** 进度存在手机里。官网与下载：**<https://run.edao.plus>**

面向中小学：画面做过护眼调色，文物知识只在自己捡到之后出现——不弹题、不考问。

## 怎么玩

猫会自动向前跑，你只要顾好脚下：

| 手势 | 作用 |
|------|------|
| 左右滑动 | 换三条跑道 |
| 上滑或点击 | 跳过矮栏 |
| 下滑 | 铲过头顶横杆 |
| 左上角按钮 | 暂停；切到后台也会自动停住 |

大木箱只能躲开。吃金币加分，连着吃还能叠倍率。大约 450 米后会出现施工跳台：两条道被堵住，要提前切到黄色斜坡。

撞上障碍本局结束。最高分、图鉴和小屋都会自动保存。

## 六大平行宇宙

大约 320 米后，路上会出现横跨三道的彩色传送门。穿过去，就到了另一个世界——配色、景物、重力都不一样：

| 宇宙 | 你会看见 | 手感 / 福利 |
|------|----------|-------------|
| 草原世界 | 树木花草，天气和昼夜 | 基准节奏 |
| 水下世界 | 海草、珊瑚、头顶游鱼 | 跳起来更漂 |
| 天空世界 | 浮岛、彩虹、飞鸟 | 跳得更高 |
| 熔岩世界 | 火山、岩浆、黑曜石 | 金币分数 ×2 |
| 糖果世界 | 棒棒糖树、拐杖糖 | 钱包金币 ×2 |
| 星空世界 | 行星、陨石、星尘 | 超低重力 |

门芯的颜色会提前告诉你下一站是哪里。六个世界都走过，有一次金币大奖。主菜单能看到宇宙图鉴。

## 文物博物馆

跑道上会稀有地刷出中国文物：发光的小鼎，走近才看清名牌。一共 **88 件**，按普通 / 稀有 / 传说分档，选材贴合历史课本——

甲骨文、后母戊鼎、越王勾践剑、曾侯乙编钟、兵马俑、清明上河图、敦煌飞天、三星堆面具、长信宫灯……

每件都有朝代、一句小知识和专属像素配图。捡到之后可以在「藏品」里反复翻看；还没遇到的只显示剪影。集齐有一次金币大奖。

知识只在孩子**自己挖到之后**才出现。横幅写得像博物馆展签，不说「小知识」三个字。主菜单和结算页会安静轮播已收集的展签；一件都没捡到时，什么都不催。

## 汤姆的小屋

主菜单进「小屋」，用钱包里的金币装扮（和本局计分金币是分开的）：

| 类别 | 可以买什么 |
|------|------------|
| 房屋 | 小木屋（免费）→ 砖瓦房 → 双层小楼 → 梦幻城堡 |
| 屋顶 | 红 / 青 / 紫 / 金 |
| 院子 | 花坛、栅栏、信箱、秋千、猫爬架、小泳池…… |

秋千会荡，泳池有波纹。院子里的猫会散步、打盹、嗅花、坐秋千——装饰越多，它越忙。小屋越丰盛，下一局开场赠送的 buff 越厚（磁铁、头盔、加倍）。

商店里还能给猫换配色、围巾、帽子和脚底光迹，庭院里的猫会穿同样的装备。

## 路上还有什么

- **道具**：磁铁吸币、头盔抗撞、加倍翻分、闪电冲刺（能撞碎障碍）、高空索道
- **局内任务**：每局随机 3 个，完成同时给分数和钱包金币
- **荣誉**：22 类 × 铜银金钻，小屋里可以翻页看进度
- **纪录榜**：本机记下距离、得分、藏品和荣誉；愿意的话可以同步到全服，家人也能用不同角色名区分

天气、昼夜、程序化音效和轻轻的振动都在。字体是 [Fusion Pixel](https://github.com/TakWolf/fusion-pixel-font) 12px 简体（OFL）。

## 下载

到官网扫码或直接下 APK：**<https://run.edao.plus>**

需要 **Android 7.0** 或更新。安装时允许「未知来源」，或用文件管理器打开 APK。

自己编译也可以：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 官网

介绍页、下载和全服榜都在 `website/`，和 [Alice 听写官网](https://github.com/vvenv/alice/tree/main/website) 同一套路（Vite + React + Tailwind，构建时预渲染）。

```bash
cd website
pnpm install
pnpm dev
```

发版：`bash scripts/release-website.sh`（读 `.env.deploy`）。`run.edao.plus` 的 `/` 是官网，`/api/` 仍是纪录榜。

## 全服纪录榜

游戏里的奖杯页可以看本机榜，也可以同步到 <https://run.edao.plus>。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/leaderboard/{slug}?limit=10` | 前 N 名 |
| POST | `/api/v1/leaderboard/{slug}` | 上报一条 |

`slug`：`run_distance` · `run_score` · `museum_collect` · `honor_count`

本地起一份参考服务端（只需 Python 3）：

```bash
python3 server/leaderboard_server.py
```

| 构建 | 默认 API |
|------|----------|
| debug | `http://10.0.2.2:8787`（模拟器访问本机） |
| release | `https://run.edao.plus`（见 `gradle.properties`） |

真机调试可在 `gradle.properties` 写成局域网地址。部署服务端：

```bash
cp server/deploy.env.example .env.deploy   # 填入 SSH 密码，不要提交
set -a && source .env.deploy && set +a
./server/deploy.sh
```

## 技术栈

- **Kotlin + OpenGL ES 2.0** — 游戏本体，没有第三方引擎
- **Python 3（标准库）** — 纪录榜参考服务端
- **Vite + React + Tailwind CSS** — 官网
- **Nginx** — 静态站与 `/api` 反代

护眼调色集中在 [EyeComfort.kt](app/src/main/java/com/vvenv/tomrun/EyeComfort.kt)：降一点饱和、抬黑压白、少一点蓝光。赛道、HUD、藏品图走同一条公式，长时间看会舒服些。

## 代码地图

| 文件 | 做什么 |
|------|--------|
| [Game.kt](app/src/main/java/com/vvenv/tomrun/Game.kt) | 节奏、任务、成就、商店、生成 |
| [GameRenderer.kt](app/src/main/java/com/vvenv/tomrun/GameRenderer.kt) | 体素世界与光迹 |
| [HudView.kt](app/src/main/java/com/vvenv/tomrun/HudView.kt) | 手势、商店、荣誉、暂停 |
| [HomeScene3D.kt](app/src/main/java/com/vvenv/tomrun/HomeScene3D.kt) | 小屋预览 |
| [RelicIcons.kt](app/src/main/java/com/vvenv/tomrun/RelicIcons.kt) | 藏品像素图 |
| [EyeComfort.kt](app/src/main/java/com/vvenv/tomrun/EyeComfort.kt) | 护眼调色 |
| [Leaderboards.kt](app/src/main/java/com/vvenv/tomrun/Leaderboards.kt) | 本机榜 |
| [server/leaderboard_server.py](server/leaderboard_server.py) | 全服榜 |
| [website/](website/) | 官网 |

## 反馈

玩得不顺、文物写错了、或者只是想要一只粉色的猫——欢迎到 [GitHub Issues](https://github.com/vvenv/tomrun/issues) 留言，或写信到 `vvenvw[at]gmail.com`。
