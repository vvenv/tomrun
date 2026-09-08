import { Apple, Smartphone } from "lucide-react";
import { APP_VERSION, APK_URL, MIN_ANDROID } from "@/data/site";
import { useReveal } from "../hooks/useReveal";
import { Coin, PortalRing } from "./PixelDecor";

export function Download() {
  const { ref, visible } = useReveal();

  return (
    <section id="download" className="relative overflow-hidden py-24 lg:py-32">
      <div className="container">
        <div
          ref={ref}
          className={`reveal relative overflow-hidden bg-ink px-8 py-16 text-center shadow-[8px_8px_0_rgba(232,180,58,0.35)] lg:px-16 lg:py-20 ${
            visible ? "is-visible" : ""
          }`}
        >
          <PortalRing className="pointer-events-none absolute -left-8 -top-8 h-36 w-36 animate-spin-slower opacity-20" />
          <Coin className="pointer-events-none absolute -bottom-4 -right-2 h-24 w-24 animate-float opacity-20" />

          <div className="relative z-10">
            <div className="eyebrow mb-4 !text-gold/80">
              <span>Get Tomrun</span>
            </div>
            <h2 className="font-display text-4xl font-black leading-tight text-paper sm:text-5xl">
              带一只猫，
              <span className="text-gold"> 跑进六大宇宙</span>
            </h2>
            <p className="mx-auto mt-5 max-w-xl text-base leading-relaxed text-paper/60">
              免费、无广告、无内购。装扮和金币都在游戏里跑出来。
            </p>

            <div className="mt-10 flex flex-wrap items-center justify-center gap-4">
              <div
                aria-disabled="true"
                title="iOS 版本即将推出"
                className="inline-flex cursor-not-allowed items-center gap-3 border border-paper/15 bg-paper/5 px-6 py-4 opacity-55"
              >
                <Apple className="h-7 w-7 text-paper" />
                <div className="text-left">
                  <div className="text-[10px] uppercase tracking-widest text-paper/50">
                    即将推出
                  </div>
                  <div className="text-sm font-semibold text-paper">App Store</div>
                </div>
              </div>

              <a
                href={APK_URL}
                download
                className="group relative inline-flex items-center gap-3 border border-gold/40 bg-gold/15 px-6 py-4 transition-colors hover:border-gold/70 hover:bg-gold/25"
              >
                <span className="absolute -right-2 -top-2 bg-ember px-2 py-0.5 text-[10px] font-bold text-paper">
                  APK
                </span>
                <Smartphone className="h-7 w-7 text-gold" />
                <div className="text-left">
                  <div className="text-[10px] uppercase tracking-widest text-gold/70">
                    Get it for
                  </div>
                  <div className="text-sm font-semibold text-paper">Android 下载</div>
                </div>
              </a>
            </div>

            <div className="mt-12 flex flex-col items-center justify-center gap-6 sm:flex-row sm:gap-10">
              <div className="flex flex-col items-center gap-3">
                <div className="flex h-32 w-32 items-center justify-center border border-paper/15 bg-paper p-3">
                  <img
                    src="/qr-code.png"
                    alt="扫码打开汤姆猫跑酷官网"
                    width={128}
                    height={128}
                    className="h-full w-full"
                  />
                </div>
                <span className="text-xs text-paper/50">扫码打开官网</span>
              </div>

              <div className="hidden h-24 w-px bg-paper/15 sm:block" />

              <div className="text-left">
                <div className="font-display text-lg font-semibold text-paper">
                  版本 v{APP_VERSION}
                </div>
                <div className="mt-1 text-sm text-paper/50">
                  支持 Android {MIN_ANDROID}+ · 约 5 MB
                </div>
                <div className="mt-1 text-xs text-paper/40">
                  需允许安装未知来源，或用文件管理器打开 APK
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
