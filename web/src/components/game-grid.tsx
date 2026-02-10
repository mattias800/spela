import { type ReactNode } from "react";

export const GAME_GRID_CLASSES =
  "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5";

export function GameGrid({ children }: { children: ReactNode }) {
  return <div className={GAME_GRID_CLASSES}>{children}</div>;
}
