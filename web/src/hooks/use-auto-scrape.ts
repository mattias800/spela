import { useEffect } from "react";
import { useInView } from "@/hooks/use-in-view";
import { enqueueScrape } from "@/lib/scrape-queue";
import type { Game } from "@/types/api";

export function useAutoScrape(game: Game) {
  const needsScrape = !game.coverUrl && game.scrapeAttempts === 0;
  const { ref, isInView } = useInView();

  useEffect(() => {
    if (needsScrape && isInView) {
      enqueueScrape(game.id);
    }
  }, [needsScrape, isInView, game.id]);

  return { ref };
}
