import { CONTACT_EMAIL_DISPLAY, GITHUB_URL } from "@/data/site";
import { Coin, PixelCatMark } from "./PixelDecor";

export function Footer() {
  return (
    <footer className="relative overflow-hidden border-t-2 border-ink/10 bg-parchment/50">
      <div className="container py-16">
        <div className="grid gap-10 lg:grid-cols-[1.5fr_1fr_1fr]">
          <div className="max-w-sm">
            <div className="flex items-center gap-2.5">
              <PixelCatMark className="h-8 w-8 border-2 border-ink/10" />
              <span className="font-display text-2xl font-black tracking-tight text-ink">
                汤姆猫<span className="text-ember">跑酷</span>
              </span>
            </div>
            <p className="mt-4 font-pixel text-[11px] leading-loose text-gold/80">
              三道换线，穿越六大宇宙
            </p>
            <p className="mt-3 text-sm leading-relaxed text-ink/55">
              给中小学做的 3D 像素跑酷。免费无广告，文物知识不考问。
            </p>
            <div className="mt-5 flex items-center gap-3 text-gold/50">
              <Coin className="h-5 w-5" />
            </div>
          </div>

          <div>
            <h4 className="mb-4 text-xs font-semibold uppercase tracking-widest text-ink/40">
              导航
            </h4>
            <ul className="space-y-3 text-sm">
              {[
                { label: "玩法", href: "#features" },
                { label: "宇宙", href: "#worlds" },
                { label: "文物", href: "#museum" },
                { label: "纪录榜", href: "#leaderboard" },
                { label: "下载", href: "#download" },
                { label: "常见问题", href: "#faq" },
              ].map((link) => (
                <li key={link.href}>
                  <a href={link.href} className="text-ink/60 transition-colors hover:text-ember">
                    {link.label}
                  </a>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="mb-4 text-xs font-semibold uppercase tracking-widest text-ink/40">
              联系
            </h4>
            <ul className="space-y-3 text-sm">
              <li className="text-ink/60">
                <span className="block text-ink/40">邮箱</span>
                <span className="font-medium text-ink">{CONTACT_EMAIL_DISPLAY}</span>
              </li>
              <li className="text-ink/60">
                <span className="block text-ink/40">开源代码</span>
                <a
                  href={GITHUB_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-medium text-ink transition-colors hover:text-ember"
                >
                  github.com/vvenv/tomrun
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="my-10 flex items-center justify-center gap-3 text-gold/50">
          <span className="h-px w-16 bg-gold/30" />
          <span className="font-pixel text-xs">✦</span>
          <span className="h-px w-16 bg-gold/30" />
        </div>

        <div className="flex flex-col items-center justify-between gap-4 text-xs text-ink/40 sm:flex-row">
          <p>© 2026 汤姆猫跑酷</p>
          <p className="font-pixel text-[10px] tracking-wider">run.edao.plus</p>
        </div>
      </div>
    </footer>
  );
}
