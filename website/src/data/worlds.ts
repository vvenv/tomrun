export interface World {
  id: string;
  name: string;
  perk: string;
  landscape: string;
  sky: string;
  ground: string;
  accent: string;
  ink: string;
}

export const WORLDS: World[] = [
  {
    id: "meadow",
    name: "草原世界",
    perk: "基准节奏",
    landscape: "树木花草，天气与昼夜",
    sky: "#6EB6E6",
    ground: "#77B94E",
    accent: "#F2A33C",
    ink: "#2C2416",
  },
  {
    id: "water",
    name: "水下世界",
    perk: "重力 0.45× 漂浮跳",
    landscape: "海草珊瑚，头顶游鱼",
    sky: "#0E4A66",
    ground: "#AE9C5F",
    accent: "#E8836E",
    ink: "#E8F6FA",
  },
  {
    id: "sky",
    name: "天空世界",
    perk: "跳得更高",
    landscape: "漂浮岛与彩虹拱桥",
    sky: "#3D8FE0",
    ground: "#CFE4F6",
    accent: "#F2C14E",
    ink: "#1A2A40",
  },
  {
    id: "lava",
    name: "熔岩世界",
    perk: "金币分数 ×2",
    landscape: "黑曜石、岩浆与火山",
    sky: "#5A1E14",
    ground: "#352A27",
    accent: "#FF7A2A",
    ink: "#FFE6C8",
  },
  {
    id: "candy",
    name: "糖果世界",
    perk: "钱包金币 ×2",
    landscape: "棒棒糖树与拐杖糖",
    sky: "#FFA8CF",
    ground: "#7CC75F",
    accent: "#FFD54A",
    ink: "#4A2A22",
  },
  {
    id: "space",
    name: "星空世界",
    perk: "超低重力",
    landscape: "环状行星与霓虹水晶",
    sky: "#12142E",
    ground: "#201A3C",
    accent: "#4DE8FF",
    ink: "#EAF6FF",
  },
];
