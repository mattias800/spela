import { Star, Gamepad2, Monitor } from "lucide-react";

interface DeveloperHeroBannerProps {
  name: string;
  gameCount: number;
  avgRating: number;
  consoleCount: number;
  heroUrl?: string;
  logoUrl?: string;
}

/** Generates a deterministic color from a string, used for the avatar circle. */
function avatarColor(name: string): string {
  const colors = [
    "#6366f1", "#8b5cf6", "#ec4899", "#f43f5e",
    "#f97316", "#eab308", "#22c55e", "#14b8a6",
    "#06b6d4", "#3b82f6",
  ];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return colors[Math.abs(hash) % colors.length];
}

export function DeveloperHeroBanner({
  name,
  gameCount,
  avgRating,
  consoleCount,
  heroUrl,
  logoUrl,
}: DeveloperHeroBannerProps) {
  const color = avatarColor(name);

  return (
    <div
      className="relative w-full rounded-2xl overflow-hidden"
      data-testid="developer-hero-banner"
    >
      {/* Background */}
      {heroUrl ? (
        <img
          src={heroUrl}
          alt=""
          className="absolute inset-0 w-full h-full object-cover"
          loading="eager"
        />
      ) : (
        <div
          className="absolute inset-0"
          style={{
            background: `linear-gradient(135deg, ${color}30, ${color}10, transparent)`,
          }}
        />
      )}

      {/* Dark gradient overlay */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/60 to-black/30 pointer-events-none" />

      {/* Content */}
      <div className="relative px-6 sm:px-8 py-10 sm:py-14">
        <div className="flex items-center gap-4 mb-4">
          {/* Avatar / Logo */}
          {logoUrl ? (
            <img
              src={logoUrl}
              alt={`${name} logo`}
              className="flex-shrink-0 w-16 h-16 object-contain rounded-lg"
              data-testid="developer-logo"
            />
          ) : (
            <div
              className="flex-shrink-0 flex items-center justify-center w-14 h-14 rounded-full text-2xl font-bold text-white"
              style={{ backgroundColor: color }}
              data-testid="developer-avatar"
            >
              {name.charAt(0).toUpperCase()}
            </div>
          )}
          <h1 className="text-3xl sm:text-4xl font-bold text-white">
            {name}
          </h1>
        </div>

        {/* Stats row */}
        <div
          className="flex flex-wrap items-center gap-4 text-sm text-white/70"
          data-testid="developer-stats"
        >
          <span className="flex items-center gap-1.5">
            <Gamepad2 className="h-4 w-4" />
            {gameCount} {gameCount === 1 ? "game" : "games"}
          </span>
          {avgRating > 0 && (
            <span className="flex items-center gap-1.5">
              <Star className="h-4 w-4 text-amber-400 fill-amber-400" />
              {avgRating.toFixed(1)}
            </span>
          )}
          {consoleCount > 0 && (
            <span className="flex items-center gap-1.5">
              <Monitor className="h-4 w-4" />
              {consoleCount} {consoleCount === 1 ? "platform" : "platforms"}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
