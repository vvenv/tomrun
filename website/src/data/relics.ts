export interface Relic {
  id: string;
  name: string;
  era: string;
  fact: string;
}

/** 官网展出的课本级名件（文件名与游戏 relic_fancy_XX.png 一致） */
export const FEATURED_RELICS: Relic[] = [
  { id: "00", name: "彩陶盆", era: "新石器时代", fact: "半坡遗址出土，画着人面鱼纹" },
  { id: "01", name: "甲骨文", era: "商代", fact: "刻在龟甲兽骨上的最早汉字" },
  { id: "08", name: "后母戊鼎", era: "商代", fact: "现存最重的青铜器，约 832 公斤" },
  { id: "09", name: "越王勾践剑", era: "春秋", fact: "埋藏两千多年依然锋利如新" },
  { id: "10", name: "曾侯乙编钟", era: "战国", fact: "65 件铜钟能演奏完整乐曲" },
  { id: "11", name: "兵马俑", era: "秦代", fact: "守卫秦始皇陵的地下军团" },
  { id: "14", name: "四羊方尊", era: "商代", fact: "四角各立一只卷角羊的国宝" },
  { id: "16", name: "清明上河图", era: "北宋", fact: "五米长卷画尽北宋都城繁华" },
  { id: "17", name: "敦煌飞天", era: "唐代", fact: "莫高窟壁画里的飞舞仙女" },
  { id: "22", name: "长信宫灯", era: "汉代", fact: "宫女跪捧，烟气吸入袖中" },
  { id: "24", name: "三星堆面具", era: "商代", fact: "纵目巨耳的青铜神面" },
  { id: "26", name: "红山玉龙", era: "红山文化", fact: "C 形碧玉龙，中华第一龙" },
  { id: "27", name: "太阳神鸟", era: "古蜀", fact: "四鸟绕日的金箔神徽" },
];
