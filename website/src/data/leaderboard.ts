export interface BoardCategory {
  slug: string;
  title: string;
  subtitle: string;
  format: (value: number) => string;
}

export const BOARD_CATEGORIES: BoardCategory[] = [
  {
    slug: "run_distance",
    title: "单场距离",
    subtitle: "一局跑过的最远",
    format: (v) => `${v} m`,
  },
  {
    slug: "run_score",
    title: "单场得分",
    subtitle: "一局最高分",
    format: (v) => v.toLocaleString("zh-CN"),
  },
  {
    slug: "museum_collect",
    title: "藏品图鉴",
    subtitle: "已收集文物",
    format: (v) => `${v}`,
  },
  {
    slug: "honor_count",
    title: "荣誉获得",
    subtitle: "累计荣誉级数",
    format: (v) => `${v}`,
  },
];

export interface BoardEntry {
  player: string;
  value: number;
  detail?: string;
}

export interface BoardPayload {
  category: string;
  entries: BoardEntry[];
}
