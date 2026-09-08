import { FEATURED_RELICS } from "@/data/relics";
import { RELIC_COUNT } from "@/data/site";
import { useStaggeredReveal } from "../hooks/useReveal";

export function Museum() {
  const ref = useStaggeredReveal(70);

  return (
    <section id="museum" className="relative py-24 lg:py-32">
      <div className="container">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <div className="eyebrow mb-4">
            <span>Museum</span>
          </div>
          <h2 className="heading-display text-4xl sm:text-5xl">
            跑着跑着，
            <span className="text-ember"> 走进博物馆</span>
          </h2>
          <p className="mt-5 text-base leading-relaxed text-ink/60">
            游戏里一共 {RELIC_COUNT}{" "}
            件文物，选材贴合中小学历史课本。下面是官网展柜里的几件名品——像素配图来自游戏本体。
          </p>
        </div>

        <div ref={ref} className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {FEATURED_RELICS.map((r) => (
            <figure
              key={r.id}
              className="reveal group border-2 border-ink/10 bg-paper p-3 transition-transform duration-300 hover:-translate-y-1 hover:border-gold/50"
            >
              <div className="aspect-square overflow-hidden bg-midnight/90">
                <img
                  src={`/relics/relic_fancy_${r.id}.png`}
                  alt={`${r.name} · ${r.era}`}
                  width={256}
                  height={256}
                  className="pixelated h-full w-full object-contain"
                  loading="lazy"
                />
              </div>
              <figcaption className="mt-3">
                <div className="font-display text-base font-bold text-ink">{r.name}</div>
                <div className="mt-0.5 text-xs text-gold">{r.era}</div>
                <p className="mt-1 text-xs leading-relaxed text-ink/55">{r.fact}</p>
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </section>
  );
}
