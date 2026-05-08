import { useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import {
  Library,
  FolderSearch,
  ArrowRight,
} from "lucide-react";
import { GameCard } from "@/components/game-card";
import { GameGrid } from "@/components/game-grid";
import { ConsoleHeroBanner } from "@/components/console-hero-banner";
import {
  EmptyState,
  GameCardSkeleton,
  SearchInput,
  ActionsMenu,
  BackButton,
} from "@/components/ui";
import { PageLayout, SectionList, TitledSection } from "@/components/layout";
import { useConsoles } from "@/hooks/use-consoles";
import { useGames, useToggleFavorite } from "@/hooks/use-games";
import { useTogglePlayLater } from "@/hooks/use-play-later";
import { useBiosStatus } from "@/hooks/use-bios";
import { useAuth } from "@/hooks/use-auth";
import { useScanLibrary } from "@/hooks/use-admin";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { useToast } from "@/components/ui";
import { BiosWarningBanner } from "@/features/bios/components/bios-warning-banner";
import {
  ConsoleEssentials,
  ConsoleHiddenGems,
  ConsoleLaunchGames,
  ConsoleRecentlyAdded,
  ConsoleTopDevelopers,
  ConsoleRecentlyPlayed,
} from "@/features/explore/components/console-showcase-sections";

const SMALL_LIBRARY_THRESHOLD = 24;
const BROWSE_CTA_THRESHOLD = 15;

export function ConsoleDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: consoles } = useConsoles();
  const { toggle: handleToggleFavorite } = useToggleFavorite();
  const { toggle: handleTogglePlayLater } = useTogglePlayLater();
  const { data: biosData } = useBiosStatus();
  const { isAdmin } = useAuth();
  const scanLibrary = useScanLibrary();
  const { toast } = useToast();

  // Small library inline search
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search, 300);

  // Find console info from the consoles list
  const console = consoles?.find((c) => c.id === id);

  const consoleName = console?.name ?? "Console";
  const consoleAbbr = console?.abbreviation ?? id ?? "";
  const gameCount = console?.gameCount ?? 0;

  const isSmallLibrary = gameCount > 0 && gameCount <= SMALL_LIBRARY_THRESHOLD;

  // Only fetch games for small library inline view
  const { data: smallLibraryData } = useGames(
    {
      consoleId: id,
      search: debouncedSearch || undefined,
      pageSize: SMALL_LIBRARY_THRESHOLD,
      sortBy: "title",
      sortOrder: "asc",
    },
    { enabled: isSmallLibrary },
  );

  const biosConsole = biosData?.consoles?.find((c) => c.consoleId === id);
  const showBiosWarning =
    biosConsole?.status === "missing" && biosConsole.biosRequired;
  const missingBiosFiles =
    biosConsole?.files
      ?.filter((f) => f.status === "missing" && f.required)
      .map((f) => f.fileName) ?? [];

  const adminMenuItems = isAdmin
    ? [
        {
          label: "Scan for new games",
          icon: <FolderSearch className="h-4 w-4" />,
          onClick: () =>
            scanLibrary.mutate(
              { console: consoleAbbr },
              {
                onSuccess: () =>
                  toast("info", `Scan started for ${consoleName}.`),
                onError: (err) =>
                  toast(
                    "error",
                    err instanceof Error ? err.message : "Scan failed",
                  ),
              },
            ),
          loading: scanLibrary.isPending,
        },
      ]
    : [];

  return (
    <PageLayout>
      <SectionList>

      {/* Page header: back button + admin overflow menu */}
      <div className="flex items-center justify-between">
        <BackButton onClick={() => navigate("/consoles")} data-testid="page-back-button">
          Consoles
        </BackButton>
        {adminMenuItems.length > 0 && (
          <ActionsMenu items={adminMenuItems} />
        )}
      </div>

      {/* Console hero banner — pure identity, no action buttons */}
      <ConsoleHeroBanner console={console} />

      {/* BIOS warning */}
      {showBiosWarning && (
        <BiosWarningBanner
          message={`Missing BIOS: ${consoleName} requires firmware files to play games.`}
          isAdmin={isAdmin}
          missingFiles={missingBiosFiles}
        />
      )}

      {/* Content varies by library size */}
      {gameCount === 0 ? (
        <EmptyState
          icon={Library}
          title="No games found"
          description="No games have been detected for this console yet."
        />
      ) : isSmallLibrary ? (
        /* Small library: inline game grid, with curated shelves above
           when content exists. Each shelf self-hides if the server
           returns an empty list (#633). Essentials, Launch Games, and
           Hidden Gems are most valuable on small libraries — they
           help a user with 6 Virtual Boy games discover the
           curated picks. Top Developers stays hidden on small
           libraries because its signal degrades with a small pool. */
        <>
          <ConsoleEssentials consoleId={id!} />
          <ConsoleLaunchGames consoleId={id!} />
          <ConsoleHiddenGems consoleId={id!} />
          <SearchInput
            placeholder={`Search ${consoleName} games...`}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-sm"
            data-testid="small-library-search"
          />
          {smallLibraryData?.data && smallLibraryData.data.length > 0 ? (
            <GameGrid>
              {smallLibraryData.data.map((game) => (
                <GameCard
                  key={game.id}
                  game={game}
                  aspectRatio={console?.coverAspectRatio}
                  hideConsoleName
                  onToggleFavorite={handleToggleFavorite}
                  onTogglePlayLater={handleTogglePlayLater}
                />
              ))}
            </GameGrid>
          ) : smallLibraryData ? (
            <EmptyState
              icon={Library}
              title="No matching games"
              description={`No games matching "${search.trim()}" for this console.`}
            />
          ) : (
            <GameGrid>
              {Array.from({ length: gameCount }, (_, i) => (
                <GameCardSkeleton
                  key={i}
                  aspectRatio={console?.coverAspectRatio}
                />
              ))}
            </GameGrid>
          )}
        </>
      ) : (
        /* Large library: showcase sections + terminal browse CTA */
        <>
          <ConsoleRecentlyPlayed consoleId={id!} />
          <ConsoleEssentials consoleId={id!} />
          <ConsoleLaunchGames consoleId={id!} />
          <ConsoleHiddenGems consoleId={id!} />
          <ConsoleTopDevelopers consoleId={id!} />
          <ConsoleRecentlyAdded consoleId={id!} />
          {gameCount > BROWSE_CTA_THRESHOLD && (
            <TitledSection title="Library" icon={Library}>
              <Link
                to={`/consoles/${id}/games`}
                className="flex items-center justify-between w-full rounded-xl bg-white/[0.03] border border-white/[0.06] px-5 py-4 text-surface-100 hover:bg-white/[0.06] transition-colors group"
                data-testid="browse-all-games-cta"
              >
                <span className="text-base font-medium">
                  Browse all {gameCount} {consoleName} games
                </span>
                <ArrowRight className="h-5 w-5 text-surface-400 group-hover:text-surface-200 transition-colors" />
              </Link>
            </TitledSection>
          )}
        </>
      )}
    </SectionList>
    </PageLayout>
  );
}

export default ConsoleDetailPage;
