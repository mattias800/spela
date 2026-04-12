import { useParams } from "react-router-dom";
import { PageLayout, SectionList } from "@/components/layout";
import { GameAchievements } from "@/features/game-detail/components/game-achievements";
import { useGame } from "@/hooks/use-games";

export function GameAchievementsPage() {
  const { id } = useParams<{ id: string }>();
  const { data: game } = useGame(id ?? "");

  return (
    <PageLayout backButtonVariant="standard" backTo={`/games/${id}`}>
      <SectionList className="max-w-5xl">

      {game && (
        <h1 className="text-2xl font-bold text-surface-100">
          {game.title} — Achievements
        </h1>
      )}

      <GameAchievements
        gameId={id ?? ""}
        achievementsWarning={game?.achievementsWarning}
      />
    </SectionList>
    </PageLayout>
  );
}
