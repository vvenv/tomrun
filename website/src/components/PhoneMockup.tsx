import type { ReactNode } from "react";
import { Coin, Crate } from "./PixelDecor";

/** CSS pixel runner inside a phone bezel — no screenshots required. */
export function PhoneMockup({ className = "" }: { className?: string }) {
  return (
    <div className={`relative ${className}`}>
      <div className="w-[280px] overflow-hidden rounded-[1.6rem] border-[10px] border-ink bg-midnight shadow-[8px_12px_0_rgba(44,36,22,0.18)] sm:w-[300px]">
        <div className="relative aspect-[9/19] overflow-hidden">
          <div
            className="absolute inset-0"
            style={{
              background:
                "linear-gradient(#4aa0d8 0%, #8ec8ea 38%, #f1e8c6 58%, #77b94e 62%, #5fa845 100%)",
            }}
          />

          <div className="absolute left-3 top-3 z-20 flex items-center gap-2 rounded-sm bg-ink/70 px-2 py-1 font-pixel text-[8px] text-gold">
            <Coin className="h-3 w-3" />
            1280
          </div>
          <div className="absolute right-3 top-3 z-20 font-pixel text-[8px] text-paper/90">
            842 m
          </div>

          <div className="absolute inset-x-8 bottom-[18%] top-[28%] flex justify-center gap-2">
            <Lane dim />
            <Lane active>
              <div className="absolute left-1/2 top-[38%] z-10 -translate-x-1/2 animate-bob">
                <RunnerCat />
              </div>
            </Lane>
            <Lane>
              <Crate className="absolute left-1/2 top-[22%] h-7 w-7 -translate-x-1/2" />
            </Lane>
          </div>

          <Coin className="absolute left-[22%] top-[34%] h-5 w-5 animate-float" />
          <Coin className="absolute right-[24%] top-[42%] h-4 w-4 animate-float [animation-delay:1.2s]" />

          <div className="absolute inset-x-0 bottom-0 h-10 bg-ink/80">
            <div className="flex h-full items-center justify-around px-4 font-pixel text-[7px] text-paper/70">
              <span>← → 换道</span>
              <span>↑ 跳</span>
              <span>↓ 铲</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Lane({
  children,
  active,
  dim,
}: {
  children?: ReactNode;
  active?: boolean;
  dim?: boolean;
}) {
  return (
    <div
      className={`relative flex-1 overflow-hidden ${dim ? "opacity-80" : ""}`}
      style={{
        backgroundColor: active ? "#e4c66a" : "#c9a84c",
        backgroundImage:
          "repeating-linear-gradient(to bottom, transparent 0 20px, rgba(255,255,255,0.18) 20px 24px)",
        animation: "scroll-lane 1.4s linear infinite",
      }}
    >
      {children}
    </div>
  );
}

function RunnerCat() {
  return (
    <svg
      viewBox="0 0 24 28"
      className="h-14 w-12"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect x="6" y="2" width="4" height="4" fill="#5C6B8C" />
      <rect x="14" y="2" width="4" height="4" fill="#5C6B8C" />
      <rect x="4" y="6" width="16" height="14" fill="#8594B3" />
      <rect x="6" y="10" width="4" height="4" fill="#fff" />
      <rect x="14" y="10" width="4" height="4" fill="#fff" />
      <rect x="7" y="11" width="2" height="2" fill="#2E7D32" />
      <rect x="15" y="11" width="2" height="2" fill="#2E7D32" />
      <rect x="8" y="16" width="8" height="4" fill="#F5F5F0" />
      <rect x="10" y="15" width="4" height="2" fill="#E8A0A8" />
      <rect x="5" y="20" width="5" height="6" fill="#6B7A9A" />
      <rect x="14" y="20" width="5" height="6" fill="#6B7A9A" />
    </svg>
  );
}
