# 汤姆猫跑酷 · 官网

<https://run.edao.plus> —— 产品介绍、APK 下载与全服纪录榜。

和 [Alice 听写官网](https://github.com/vvenv/alice/tree/main/website) 同一套骨架：Vite + React 19 + Tailwind CSS v4 的静态站，构建时用 Playwright 预渲染成纯 HTML（`scripts/prerender.mjs`）。页面部署在 `run.edao.plus`，`/api/` 仍反代到纪录榜服务。

```bash
cd website
pnpm install
pnpm dev          # 本地开发（/api 代理到线上）
pnpm check        # 类型检查
pnpm lint
pnpm build        # 预渲染产物到 dist/
```

发版：仓库根目录 `bash scripts/release-website.sh`（读 `.env.deploy`）。

## 约定

- **设计令牌全在 `src/index.css`**。Tailwind v4 用 `@theme` / `@utility` 声明颜色、像素字体与动画，没有 `tailwind.config.js`。
- **路径别名 `@/`** 指向 `src/`。
- 文案、下载链接集中在 `src/data/site.ts`；FAQ 在 `src/data/faq.ts`，同时喂给页面和 `JsonLd`。
