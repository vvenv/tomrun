/** Pixel-game decorations: cat, coin, portal, crate. */

export function PixelCat({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 108 108"
      className={className}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect width="108" height="108" fill="#6BCCF2" />
      <rect x="0" y="84" width="108" height="24" fill="#57BD47" />
      <rect x="30" y="34" width="48" height="40" fill="#8594B3" />
      <rect x="32" y="22" width="12" height="12" fill="#5C6B8C" />
      <rect x="64" y="22" width="12" height="12" fill="#5C6B8C" />
      <rect x="38" y="44" width="10" height="10" fill="#fff" />
      <rect x="60" y="44" width="10" height="10" fill="#fff" />
      <rect x="42" y="47" width="5" height="5" fill="#2E7D32" />
      <rect x="63" y="47" width="5" height="5" fill="#2E7D32" />
      <rect x="44" y="60" width="20" height="12" fill="#F5F5F0" />
      <rect x="50" y="58" width="8" height="6" fill="#E8A0A8" />
      <rect x="84" y="64" width="12" height="12" fill="#FFD426" />
    </svg>
  );
}

export function PixelCatMark({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 16 16"
      className={className}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect width="16" height="16" fill="#6BCCF2" />
      <rect x="0" y="13" width="16" height="3" fill="#57BD47" />
      <rect x="4" y="5" width="8" height="7" fill="#8594B3" />
      <rect x="4" y="3" width="2" height="2" fill="#5C6B8C" />
      <rect x="10" y="3" width="2" height="2" fill="#5C6B8C" />
      <rect x="5" y="7" width="2" height="2" fill="#fff" />
      <rect x="9" y="7" width="2" height="2" fill="#fff" />
      <rect x="6" y="10" width="4" height="2" fill="#F5F5F0" />
      <rect x="13" y="10" width="2" height="2" fill="#FFD426" />
    </svg>
  );
}

export function Coin({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 12 12"
      className={className}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect x="2" y="1" width="8" height="10" fill="#E8B43A" />
      <rect x="1" y="3" width="10" height="6" fill="#E8B43A" />
      <rect x="4" y="3" width="4" height="6" fill="#FFE08A" />
      <rect x="5" y="4" width="2" height="4" fill="#C48A14" />
    </svg>
  );
}

export function PortalRing({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 20 20"
      className={className}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect x="3" y="1" width="14" height="18" fill="#B478E8" />
      <rect x="1" y="3" width="18" height="14" fill="#B478E8" />
      <rect x="5" y="3" width="10" height="14" fill="#4DE8FF" />
      <rect x="3" y="5" width="14" height="10" fill="#4DE8FF" />
      <rect x="7" y="5" width="6" height="10" fill="#F3EBD4" />
      <rect x="5" y="7" width="10" height="6" fill="#F3EBD4" />
    </svg>
  );
}

export function Crate({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 12 12"
      className={className}
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      <rect width="12" height="12" fill="#A06230" />
      <rect x="1" y="1" width="10" height="10" fill="#C47A3E" />
      <rect x="1" y="5" width="10" height="2" fill="#8A4E22" />
      <rect x="5" y="1" width="2" height="10" fill="#8A4E22" />
    </svg>
  );
}
