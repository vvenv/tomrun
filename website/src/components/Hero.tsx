import { ArrowRight, Smartphone, Orbit, Landmark, Trophy } from "lucide-react";
import { PhoneMockup } from "./PhoneMockup";
import { Coin, PortalRing } from "./PixelDecor";
import { RELIC_COUNT, UNIVERSE_COUNT } from "@/data/site";

export function Hero() {
  return (
    <section className="relative overflow-hidden pt-28 lg:pt-36">
      <PortalRing className="pointer-events-none absolute -left-8 top-32 hidden h-28 w-28 animate-spin-slower text-gold/20 lg:block" />
      <Coin className="pointer-events-none absolute right-[12%] top-28 hidden h-10 w-10 animate-float lg:block" />

      <div className="container relative z-10">
        <div className="grid items-center gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:gap-8">
          <div className="max-w-xl">
            <div
              className="mb-6 inline-flex items-center gap-2 border-2 border-gold/35 bg-gold/10 px-4 py-1.5 font-pixel text-[10px] text-gold opacity-0"
              style={{ animation: "fadeUp 0.8s 0.1s forwards" }}
            >
              免费 · 无广告 · Android
            </div>

            <h1
              className="heading-display text-5xl opacity-0 sm:text-6xl lg:text-7xl"
              style={{ animation: "fadeUp 0.9s 0.2s forwards" }}
            >
              汤姆猫
              <span className="block text-ember">跑酷</span>
            </h1>

            <p
              className="mt-4 font-pixel text-sm leading-loose text-gold opacity-0"
              style={{ animation: "fadeUp 0.9s 0.35s forwards" }}
            >
              三道换线，穿越六大宇宙
            </p>

            <p
              className="mt-6 text-base leading-relaxed text-ink/70 opacity-0"
              style={{ animation: "fadeUp 0.9s 0.5s forwards" }}
            >
              原生 3D 像素无尽跑酷。左右换道、跳跃铲滑，穿过传送门去草原、水下、天空、熔岩、糖果和星空。路上还能捡到中国文物——知识只在自己挖到之后出现，不弹题、不考问。
            </p>

            <div
              className="mt-8 flex flex-wrap items-center gap-4 opacity-0"
              style={{ animation: "fadeUp 0.9s 0.65s forwards" }}
            >
              <a href="#download" className="btn-primary">
                <Smartphone className="h-4 w-4" />
                立即下载
                <ArrowRight className="h-4 w-4" />
              </a>
              <a href="#features" className="btn-secondary">
                看看怎么玩
              </a>
            </div>

            <div
              className="mt-10 flex flex-wrap items-center gap-x-8 gap-y-4 opacity-0"
              style={{ animation: "fadeUp 0.9s 0.8s forwards" }}
            >
              {[
                { icon: Orbit, label: "平行宇宙", value: `${UNIVERSE_COUNT} 个世界` },
                { icon: Landmark, label: "文物图鉴", value: `${RELIC_COUNT} 件藏品` },
                { icon: Trophy, label: "纪录榜", value: "全服可同步" },
              ].map((item) => (
                <div key={item.label} className="flex items-center gap-2.5">
                  <item.icon className="h-5 w-5 text-gold" />
                  <div>
                    <div className="text-sm font-semibold text-ink">{item.value}</div>
                    <div className="text-xs text-ink/50">{item.label}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div
            className="relative flex justify-center opacity-0 lg:justify-end"
            style={{ animation: "fadeIn 1s 0.4s forwards" }}
          >
            <div className="absolute inset-0 -z-10 bg-linear-to-tr from-sky/25 via-transparent to-gold/20 blur-3xl" />
            <div className="animate-float">
              <PhoneMockup />
            </div>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(24px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: scale(0.96); }
          to { opacity: 1; transform: scale(1); }
        }
      `}</style>
    </section>
  );
}
