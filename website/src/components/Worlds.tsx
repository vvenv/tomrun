import { WORLDS } from "@/data/worlds";
import { useStaggeredReveal } from "../hooks/useReveal";

export function Worlds() {
  const ref = useStaggeredReveal(100);

  return (
    <section id="worlds" className="relative overflow-hidden bg-parchment/40 py-24 lg:py-32">
      <div className="container">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <div className="eyebrow mb-4">
            <span>Worlds</span>
          </div>
          <h2 className="heading-display text-4xl sm:text-5xl">
            穿过传送门，
            <span className="text-ember"> 换一套物理</span>
          </h2>
          <p className="mt-5 text-base leading-relaxed text-ink/60">
            门芯颜色会预告下一站。六个世界全部走访过，有一次金币大奖。
          </p>
        </div>

        <div ref={ref} className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {WORLDS.map((w) => (
            <article
              key={w.id}
              className="reveal overflow-hidden border-2 border-ink/10 transition-transform duration-300 hover:-translate-y-1"
            >
              <div
                className="relative h-28"
                style={{
                  background: `linear-gradient(180deg, ${w.sky} 0%, ${w.ground} 100%)`,
                }}
              >
                <span
                  className="absolute bottom-3 left-3 font-pixel text-[10px] tracking-wider"
                  style={{ color: w.ink }}
                >
                  {w.perk}
                </span>
                <span
                  className="absolute right-3 top-3 h-4 w-4"
                  style={{ background: w.accent }}
                />
              </div>
              <div className="bg-paper px-5 py-5">
                <h3 className="font-display text-xl font-bold text-ink">{w.name}</h3>
                <p className="mt-2 text-sm text-ink/60">{w.landscape}</p>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
