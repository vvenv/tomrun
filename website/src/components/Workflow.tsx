import { useStaggeredReveal } from "../hooks/useReveal";

const STEPS = [
  {
    num: "01",
    title: "左右滑动换道",
    desc: "三条跑道自动向前。矮栏要跳，横杆要铲，大木箱只能躲开。",
  },
  {
    num: "02",
    title: "吃金币、叠连击",
    desc: "连续吃币会加倍计分。磁铁、头盔、闪电会在路上刷出来。",
  },
  {
    num: "03",
    title: "穿门、拾文物",
    desc: "传送门换世界，发光小鼎是文物。捡到才出现一句展签。",
  },
  {
    num: "04",
    title: "回家装扮小屋",
    desc: "钱包金币买房屋和院子。装得越好，下一局开场 buff 越厚。",
  },
];

export function Workflow() {
  const ref = useStaggeredReveal(150);

  return (
    <section id="workflow" className="relative overflow-hidden bg-parchment/40 py-24 lg:py-32">
      <div className="container">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <div className="eyebrow mb-4">
            <span>How</span>
          </div>
          <h2 className="heading-display text-4xl sm:text-5xl">
            四步就懂，
            <span className="text-ember"> 一局就上瘾</span>
          </h2>
        </div>

        <div ref={ref} className="relative">
          <div className="absolute left-0 right-0 top-10 hidden border-t-2 border-dashed border-gold/30 lg:block" />
          <div className="grid gap-10 lg:grid-cols-4 lg:gap-6">
            {STEPS.map((step) => (
              <div key={step.num} className="reveal relative flex flex-col items-center text-center">
                <div className="relative z-10 mb-6 flex h-20 w-20 items-center justify-center border-2 border-gold/50 bg-paper font-pixel text-xl text-ember shadow-[4px_4px_0_rgba(232,180,58,0.25)]">
                  {step.num}
                </div>
                <h3 className="font-display text-xl font-bold text-ink">{step.title}</h3>
                <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink/60">{step.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
