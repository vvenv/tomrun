import { ChevronDown } from "lucide-react";
import { FAQS } from "@/data/faq";
import { useReveal } from "../hooks/useReveal";

export function Faq() {
  const { ref, visible } = useReveal();

  return (
    <section id="faq" className="relative py-24 lg:py-32">
      <div className="container">
        <div className="grid gap-12 lg:grid-cols-[0.85fr_1.15fr] lg:gap-16">
          <div className="lg:sticky lg:top-28 lg:self-start">
            <div className="eyebrow eyebrow-left mb-4">
              <span>FAQ</span>
            </div>
            <h2 className="heading-display text-4xl sm:text-5xl">
              常见
              <span className="text-ember"> 疑问</span>
            </h2>
            <p className="mt-5 text-base leading-relaxed text-ink/60">
              给家长和第一次安装的人。
            </p>
          </div>

          <div ref={ref} className={`reveal space-y-3 ${visible ? "is-visible" : ""}`}>
            {FAQS.map((item, i) => (
              <details
                key={i}
                open={i === 0}
                className="group overflow-hidden border-2 border-ink/10 bg-paper/50 open:border-gold/30"
              >
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-6 py-5 text-left [&::-webkit-details-marker]:hidden">
                  <span className="font-display text-lg font-semibold text-ink transition-colors group-open:text-ember">
                    {item.q}
                  </span>
                  <ChevronDown className="h-5 w-5 shrink-0 text-gold transition-transform duration-300 group-open:rotate-180" />
                </summary>
                <p className="px-6 pb-5 text-sm leading-relaxed text-ink/65">{item.a}</p>
              </details>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
