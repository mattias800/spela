import { useParams, useNavigate } from "react-router-dom";
import { BackButton } from "@/components/ui";
import { GameAchievements } from "@/features/game-detail/components/game-achievements";
import { useGame } from "@/hooks/use-games";

export function GameAchievementsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: game } = useGame(id ?? "");

  return (
    <div className="max-w-5xl space-y-6">
      <BackButton onClick={() => navigate(`/games/${id}`)} />

      {game && (
        <h1 className="text-2xl font-bold text-surface-100">
          {game.title} — Achievements
        </h1>
      )}

      <GameAchievements
        gameId={id ?? ""}
        achievementsWarning={game?.achievementsWarning}
      />
    </div>
  );
}
