import { useEffect, useState } from "react";
import {
  BOARD_CATEGORIES,
  type BoardEntry,
  type BoardPayload,
} from "@/data/leaderboard";
import { useReveal } from "../hooks/useReveal";

export function Leaderboard() {
  const { ref, visible } = useReveal();
  const [active, setActive] = useState(BOARD_CATEGORIES[0].slug);
  const [entries, setEntries] = useState<BoardEntry[]>([]);
  const [status, setStatus] = useState<"loading" | "ok" | "empty" | "error">(
    "loading",
  );

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/v1/leaderboard/${active}?limit=10`)
      .then((res) => {
        if (!res.ok) throw new Error(String(res.status));
        return res.json() as Promise<BoardPayload>;
      })
      .then((data) => {
        if (cancelled) return;
        const list = data.entries ?? [];
        setEntries(list);
        setStatus(list.length ? "ok" : "empty");
      })
      .catch(() => {
        if (!cancelled) setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [active]);

  const cat = BOARD_CATEGORIES.find((c) => c.slug === active) ?? BOARD_CATEGORIES[0];

  return (
    <section id="leaderboard" className="relative py-24 lg:py-32">
      <div className="container">
        <div
          ref={ref}
          className={`reveal mx-auto max-w-3xl ${visible ? "is-visible" : ""}`}
        >
          <div className="mb-10 text-center">
            <div className="eyebrow mb-4">
              <span>Board</span>
            </div>
            <h2 className="heading-display text-4xl sm:text-5xl">
              全服
              <span className="text-ember"> 纪录榜</span>
            </h2>
            <p className="mt-5 text-base leading-relaxed text-ink/60">
              和官网同一个域名。游戏里可选同步；不想联网，本机榜照样记。
            </p>
          </div>

          <div className="mb-6 flex flex-wrap justify-center gap-2">
            {BOARD_CATEGORIES.map((c) => (
              <button
                key={c.slug}
                type="button"
                onClick={() => {
                  setActive(c.slug);
                  setStatus("loading");
                }}
                className={`px-4 py-2 text-sm font-medium transition-colors ${
                  c.slug === active
                    ? "bg-ink text-paper"
                    : "border-2 border-ink/15 bg-paper text-ink/70 hover:border-gold"
                }`}
              >
                {c.title}
              </button>
            ))}
          </div>

          <div className="border-2 border-ink/10 bg-paper">
            <div className="border-b-2 border-ink/10 px-5 py-3 text-xs text-ink/45">
              {cat.subtitle}
            </div>
            {status === "loading" && (
              <p className="px-5 py-10 text-center text-sm text-ink/45">读取中…</p>
            )}
            {status === "error" && (
              <p className="px-5 py-10 text-center text-sm text-ink/45">
                暂时连不上榜。游戏里仍可查看本机纪录。
              </p>
            )}
            {status === "empty" && (
              <p className="px-5 py-10 text-center text-sm text-ink/45">
                还没有人上榜，去跑一局当第一名。
              </p>
            )}
            {status === "ok" && (
              <ol className="divide-y divide-ink/8">
                {entries.map((e, i) => (
                  <li
                    key={`${e.player}-${e.value}-${i}`}
                    className="flex items-center justify-between gap-4 px-5 py-3"
                  >
                    <span className="flex items-center gap-3">
                      <span className="font-pixel w-6 text-xs text-gold">
                        {String(i + 1).padStart(2, "0")}
                      </span>
                      <span className="font-medium text-ink">{e.player || "汤姆"}</span>
                    </span>
                    <span className="font-pixel text-xs text-ink/70">
                      {cat.format(e.value)}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
