import {
  Gamepad2,
  Orbit,
  Landmark,
  Home,
  Zap,
  Shield,
  type LucideIcon,
} from "lucide-react";
import { useStaggeredReveal } from "../hooks/useReveal";
import { Coin } from "./PixelDecor";

interface Feature {
  icon: LucideIcon;
  title: string;
  desc: string;
  tag: string;
}

const FEATURES: Feature[] = [
  {
    icon: Gamepad2,
    title: "三道无尽跑酷",
    desc: "猫自己往前跑。左右滑动换道，上滑跳跃，下滑铲滑。大木箱只能躲开，金币连吃还能叠倍率。",
    tag: "核心玩法",
  },
  {
    icon: Orbit,
    title: "平行宇宙传送门",
    desc: "彩色传送门随机把你送到另一个世界。六个宇宙各有配色、景物，还有漂浮跳、低重力或金币翻倍。",
    tag: "六大世界",
  },
  {
    icon: Landmark,
    title: "文物博物馆",
    desc: "路上会刷出 88 件中国文物，贴合历史课本。捡到才出现朝代和小知识，藏品页可以反复翻看。",
    tag: "寓教于乐",
  },
  {
    icon: Home,
    title: "汤姆的小屋",
    desc: "用钱包金币装扮房屋、屋顶和院子。秋千会荡、猫会散步。小屋越丰盛，开局赠送的 buff 越多。",
    tag: "家园",
  },
  {
    icon: Zap,
    title: "道具与索道",
    desc: "磁铁吸币、头盔抗撞、加倍翻分、闪电冲刺撞碎障碍。对准绿色门架还能飞上高空索道。",
    tag: "节奏",
  },
  {
    icon: Shield,
    title: "护眼，也护进度",
    desc: "画面降饱和、抬黑压白、少一点蓝光。切到后台会自动暂停。进度存在手机里，不想联网也能玩。",
    tag: "给孩子",
  },
];

export function Features() {
  const ref = useStaggeredReveal(120);

  return (
    <section id="features" className="relative py-24 lg:py-32">
      <div className="container">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <div className="eyebrow mb-4">
            <span>Play</span>
          </div>
          <h2 className="heading-display text-4xl sm:text-5xl">
            为跑酷打磨的
            <span className="text-ember"> 每一处细节</span>
          </h2>
          <p className="mt-5 text-base leading-relaxed text-ink/60">
            从换道手感到文物展签，都希望孩子能玩得久、看得清、记得住。
          </p>
        </div>

        <div ref={ref} className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title} className="card-pixel reveal group relative overflow-hidden">
              <Coin className="absolute right-6 top-6 h-5 w-5 opacity-20 transition-all duration-300 group-hover:rotate-12 group-hover:opacity-50" />

              <div className="mb-5 inline-flex h-12 w-12 items-center justify-center bg-ink text-paper transition-colors duration-300 group-hover:bg-ember">
                <f.icon className="h-6 w-6" />
              </div>

              <div className="mb-1 font-pixel text-[10px] tracking-widest text-gold/80">
                {f.tag}
              </div>
              <h3 className="font-display text-2xl font-bold text-ink">{f.title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-ink/60">{f.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
