import { Link } from "react-router-dom";
import { ArrowLeft, ExternalLink } from "lucide-react";

interface CreditEntry {
  name: string;
  url: string;
  license: string;
  description: string;
}

const credits: CreditEntry[] = [
  {
    name: "retro-game-console-icons",
    url: "https://github.com/KyleBing/retro-game-console-icons",
    license: "GPL-3.0",
    description:
      "Console hardware icons used in the library view.",
  },
  {
    name: "console-logos",
    url: "https://github.com/PRO100BYTE/console-logos",
    license: "Free to use",
    description:
      "Console logo SVGs by Dan Patrick used in console detail pages.",
  },
  {
    name: "Icons8",
    url: "https://icons8.com",
    license: "Free with attribution",
    description:
      "GameCube and 3DS console icons used in the library view.",
  },
  {
    name: "EmulatorJS",
    url: "https://emulatorjs.org",
    license: "GPL-3.0",
    description:
      "Browser-based emulation frontend powering web play.",
  },
  {
    name: "libretro / RetroArch",
    url: "https://www.libretro.com",
    license: "GPL-3.0",
    description:
      "Emulation API and cores used by the native player app.",
  },
  {
    name: "RetroAchievements",
    url: "https://retroachievements.org",
    license: "Community project",
    description: "Achievement system for retro games.",
  },
];

export function LicensesPage() {
  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <div className="flex items-center gap-3">
        <Link
          to="/preferences"
          className="p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
        >
          <ArrowLeft className="h-5 w-5" />
        </Link>
        <h1 className="text-2xl font-bold text-surface-100">
          Credits & Licenses
        </h1>
      </div>

      <p className="text-surface-400 text-sm">
        Spela uses the following open-source projects and resources.
      </p>

      <div className="space-y-4">
        {credits.map((entry) => (
          <div
            key={entry.name}
            className="rounded-xl border border-surface-800/50 bg-surface-900/50 p-5"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1">
                <h3 className="text-base font-semibold text-surface-100">
                  {entry.name}
                </h3>
                <p className="text-sm text-surface-400">{entry.description}</p>
                <p className="text-xs text-surface-500">
                  License: {entry.license}
                </p>
              </div>
              <a
                href={entry.url}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-shrink-0 p-2 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800/50 transition-colors"
              >
                <ExternalLink className="h-4 w-4" />
              </a>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
