import { Link } from "react-router-dom";
import {
  Award,
  Mountain,
  Target,
  Sparkles,
  Swords,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { ScrollShelf } from "@/components/scroll-shelf";
import { CAROUSEL_CARD_HEIGHT } from "@/lib/carousel-constants";
import type {
  Game,
  EasyToCompleteResponse,
  HardestGamesResponse,
  AlmostDoneResponse,
  FreshChallengesResponse,
  ActiveChallengesResponse,
} from "@/types/api";

// --- Easy to Complete Shelf ---

interface EasyToCompleteShelfProps {
  data: EasyToCompleteResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function EasyToCompleteShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: EasyToCompleteShelfProps) {
  return (
    <ScrollShelf
      title="Easy to 100%"
      subtitle="Games with the highest achievement completion rates"
      icon={Award}
      testId="easy-to-complete-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games?.map((item) => (
        <div
          key={item.game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p
            className="text-xs text-surface-400 mt-1.5"
            data-testid="avg-completion"
          >
            {item.avgCompletion}% avg completion
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Hardest Games Shelf ---

interface HardestGamesShelfProps {
  data: HardestGamesResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function HardestGamesShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: HardestGamesShelfProps) {
  return (
    <ScrollShelf
      title="Mount Everest"
      subtitle="The toughest achievement challenges"
      icon={Mountain}
      testId="hardest-games-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games?.map((item) => (
        <div
          key={item.game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p
            className="text-xs text-surface-400 mt-1.5"
            data-testid="hardest-completion"
          >
            {item.avgCompletion}% avg · {item.playersCompleted}/{item.playersAttempted} completed
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Almost Done Shelf ---

interface AlmostDoneShelfProps {
  data: AlmostDoneResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function AlmostDoneShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: AlmostDoneShelfProps) {
  return (
    <ScrollShelf
      title="Almost Done"
      subtitle="You're so close to 100%!"
      icon={Target}
      testId="almost-done-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games?.map((item) => (
        <div
          key={item.game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <div className="mt-1.5">
            <div
              className="w-full h-1.5 bg-surface-700 rounded-full overflow-hidden"
              role="progressbar"
              aria-valuenow={item.completionPercent}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label={`${item.unlockedCount} of ${item.totalCount} achievements unlocked`}
            >
              <div
                className="h-full bg-brand-500 rounded-full transition-all"
                style={{ width: `${item.completionPercent}%` }}
                data-testid="progress-bar"
              />
            </div>
            <p
              className="text-xs text-surface-400 mt-1"
              data-testid="achievement-progress"
            >
              {item.unlockedCount}/{item.totalCount} achievements ({item.completionPercent}%)
            </p>
          </div>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Fresh Challenges Shelf ---

interface FreshChallengesShelfProps {
  data: FreshChallengesResponse | undefined;
  isLoading: boolean;
  onToggleFavorite?: (game: Game) => void;
  onTogglePlayLater?: (game: Game) => void;
}

export function FreshChallengesShelf({
  data,
  isLoading,
  onToggleFavorite,
  onTogglePlayLater,
}: FreshChallengesShelfProps) {
  return (
    <ScrollShelf
      title="Fresh Achievement Challenges"
      subtitle="Games with achievements you haven't started yet"
      icon={Sparkles}
      testId="fresh-challenges-shelf"
      isLoading={isLoading}
      isEmpty={!data?.games || data.games.length === 0}
    >
      {data?.games?.map((item) => (
        <div
          key={item.game.id}
          className="flex-shrink-0"
          role="listitem"
        >
          <GameCard
            game={item.game}
            showConsoleBadge
            coverHeight={CAROUSEL_CARD_HEIGHT}
            onToggleFavorite={onToggleFavorite}
            onTogglePlayLater={onTogglePlayLater}
          />
          <p
            className="text-xs text-surface-400 mt-1.5"
            data-testid="fresh-challenge-info"
          >
            {item.totalAchievements} achievements &middot; {item.totalPoints} points
          </p>
        </div>
      ))}
    </ScrollShelf>
  );
}

// --- Active Challenges Shelf ---

interface ActiveChallengesShelfProps {
  data: ActiveChallengesResponse | undefined;
  isLoading: boolean;
}

export function ActiveChallengesShelf({
  data,
  isLoading,
}: ActiveChallengesShelfProps) {
  return (
    <ScrollShelf
      title="Active Challenges"
      subtitle="Open challenges from the community"
      icon={Swords}
      testId="active-challenges-shelf"
      isLoading={isLoading}
      isEmpty={!data?.challenges || data.challenges.length === 0}
    >
      {data?.challenges.map((ch) => (
        <div key={ch.id} className="w-64 flex-shrink-0" role="listitem">
          <Link to={`/challenges/${ch.id}`} className="block">
            <div className="bg-surface-800 rounded-lg p-4 hover:bg-surface-700 transition-colors">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-brand-500/20 text-brand-400">
                  {ch.type}
                </span>
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-surface-600 text-surface-300">
                  {ch.difficulty}
                </span>
              </div>
              <h3
                className="text-sm font-semibold text-surface-100 truncate"
                data-testid="challenge-name"
              >
                {ch.name}
              </h3>
              <p className="text-xs text-surface-400 truncate mt-1">
                {ch.gameTitle}
              </p>
              <div className="flex items-center gap-3 mt-3 text-xs text-surface-400">
                <span>{ch.attemptCount} attempts</span>
                <span>{ch.completionCount} completed</span>
              </div>
            </div>
          </Link>
        </div>
      ))}
    </ScrollShelf>
  );
}
