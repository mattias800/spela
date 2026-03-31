import type { LucideIcon } from "lucide-react";
import { Gamepad2, Smartphone, Monitor, Joystick, Tv } from "lucide-react";

interface ConsoleStyle {
  icon: LucideIcon;
  gradient: string;
  color: string;
}

const consoleStyles: Record<string, ConsoleStyle> = {
  nes: {
    icon: Gamepad2,
    gradient: "from-red-600 to-red-900",
    color: "#e53e3e",
  },
  snes: {
    icon: Gamepad2,
    gradient: "from-purple-600 to-indigo-900",
    color: "#805ad5",
  },
  gb: {
    icon: Smartphone,
    gradient: "from-green-600 to-green-900",
    color: "#38a169",
  },
  gbc: {
    icon: Smartphone,
    gradient: "from-teal-500 to-green-800",
    color: "#319795",
  },
  gba: {
    icon: Smartphone,
    gradient: "from-indigo-500 to-purple-800",
    color: "#667eea",
  },
  n64: {
    icon: Joystick,
    gradient: "from-green-500 to-blue-700",
    color: "#48bb78",
  },
  nds: {
    icon: Smartphone,
    gradient: "from-gray-400 to-gray-700",
    color: "#a0aec0",
  },
  sms: {
    icon: Tv,
    gradient: "from-blue-500 to-blue-800",
    color: "#4299e1",
  },
  genesis: {
    icon: Gamepad2,
    gradient: "from-blue-700 to-black",
    color: "#2b6cb0",
  },
  saturn: {
    icon: Monitor,
    gradient: "from-gray-600 to-gray-900",
    color: "#718096",
  },
  psx: {
    icon: Gamepad2,
    gradient: "from-gray-500 to-blue-900",
    color: "#a0aec0",
  },
  psp: {
    icon: Smartphone,
    gradient: "from-gray-700 to-black",
    color: "#4a5568",
  },
  neogeo: {
    icon: Joystick,
    gradient: "from-yellow-500 to-red-700",
    color: "#ecc94b",
  },
  neocd: {
    icon: Joystick,
    gradient: "from-yellow-500 to-red-700",
    color: "#ecc94b",
  },
  arcade: {
    icon: Joystick,
    gradient: "from-yellow-400 to-orange-700",
    color: "#f6ad55",
  },
  tg16: {
    icon: Tv,
    gradient: "from-orange-500 to-red-800",
    color: "#ed8936",
  },
  pcecd: {
    icon: Tv,
    gradient: "from-orange-500 to-red-800",
    color: "#ed8936",
  },
  atari2600: {
    icon: Tv,
    gradient: "from-slate-800 to-slate-900",
    color: "#1e293b",
  },
  gg: {
    icon: Smartphone,
    gradient: "from-blue-600 to-blue-900",
    color: "#2563eb",
  },
  scd: {
    icon: Tv,
    gradient: "from-gray-700 to-blue-900",
    color: "#334155",
  },
  "32x": {
    icon: Gamepad2,
    gradient: "from-gray-800 to-black",
    color: "#1e293b",
  },
  dc: {
    icon: Gamepad2,
    gradient: "from-orange-500 to-blue-700",
    color: "#f97316",
  },
  vb: {
    icon: Monitor,
    gradient: "from-red-800 to-red-950",
    color: "#991b1b",
  },
  "3ds": {
    icon: Smartphone,
    gradient: "from-sky-950 to-slate-950",
    color: "#0c4a6e",
  },
  gc: {
    icon: Gamepad2,
    gradient: "from-indigo-500 to-purple-800",
    color: "#6f5fa6",
  },
  a52: {
    icon: Tv,
    gradient: "from-amber-700 to-amber-950",
    color: "#b45309",
  },
  a78: {
    icon: Tv,
    gradient: "from-slate-800 to-slate-900",
    color: "#1e293b",
  },
  lynx: {
    icon: Smartphone,
    gradient: "from-yellow-600 to-yellow-900",
    color: "#ca8a04",
  },
  jag: {
    icon: Gamepad2,
    gradient: "from-red-700 to-gray-900",
    color: "#b91c1c",
  },
  ngp: {
    icon: Smartphone,
    gradient: "from-gray-500 to-gray-800",
    color: "#6b7280",
  },
  ws: {
    icon: Smartphone,
    gradient: "from-indigo-600 to-indigo-900",
    color: "#4f46e5",
  },
  pcfx: {
    icon: Tv,
    gradient: "from-teal-600 to-teal-900",
    color: "#0d9488",
  },
  cv: {
    icon: Tv,
    gradient: "from-sky-600 to-sky-900",
    color: "#0284c7",
  },
  pkmn: {
    icon: Smartphone,
    gradient: "from-yellow-400 to-yellow-700",
    color: "#facc15",
  },
  ps2: {
    icon: Gamepad2,
    gradient: "from-blue-800 to-blue-950",
    color: "#1e40af",
  },
  c64: {
    icon: Monitor,
    gradient: "from-blue-500 to-purple-800",
    color: "#6366f1",
  },
  dos: {
    icon: Monitor,
    gradient: "from-green-700 to-green-950",
    color: "#15803d",
  },
  amiga: {
    icon: Monitor,
    gradient: "from-red-600 to-blue-800",
    color: "#dc2626",
  },
  acd32: {
    icon: Gamepad2,
    gradient: "from-purple-600 to-indigo-900",
    color: "#6c5eb5",
  },
  ps3: {
    icon: Gamepad2,
    gradient: "from-gray-700 to-blue-950",
    color: "#003087",
  },
  ps4: {
    icon: Gamepad2,
    gradient: "from-blue-800 to-blue-950",
    color: "#003087",
  },
  ps5: {
    icon: Gamepad2,
    gradient: "from-white to-blue-900",
    color: "#003087",
  },
  xbox: {
    icon: Gamepad2,
    gradient: "from-green-600 to-green-900",
    color: "#107c10",
  },
  x360: {
    icon: Gamepad2,
    gradient: "from-green-600 to-green-900",
    color: "#107c10",
  },
  xone: {
    icon: Gamepad2,
    gradient: "from-green-700 to-green-950",
    color: "#107c10",
  },
  xsx: {
    icon: Gamepad2,
    gradient: "from-green-800 to-black",
    color: "#107c10",
  },
  wii: {
    icon: Gamepad2,
    gradient: "from-gray-300 to-gray-600",
    color: "#c0c0c0",
  },
  wiiu: {
    icon: Gamepad2,
    gradient: "from-sky-500 to-sky-800",
    color: "#009ac7",
  },
  nsw: {
    icon: Gamepad2,
    gradient: "from-red-500 to-red-800",
    color: "#e60012",
  },
  "3do": {
    icon: Gamepad2,
    gradient: "from-gray-400 to-gray-700",
    color: "#c0c0c0",
  },
  c128: {
    icon: Monitor,
    gradient: "from-blue-500 to-purple-800",
    color: "#6c5eb5",
  },
  pet: {
    icon: Monitor,
    gradient: "from-blue-400 to-purple-700",
    color: "#6c5eb5",
  },
  plus4: {
    icon: Monitor,
    gradient: "from-blue-400 to-purple-700",
    color: "#6c5eb5",
  },
  vic20: {
    icon: Monitor,
    gradient: "from-blue-500 to-purple-800",
    color: "#6c5eb5",
  },
  cdi: {
    icon: Gamepad2,
    gradient: "from-green-700 to-green-950",
    color: "#006633",
  },
  msx1: {
    icon: Monitor,
    gradient: "from-blue-500 to-blue-800",
    color: "#4a86c8",
  },
  msx2: {
    icon: Monitor,
    gradient: "from-blue-500 to-blue-800",
    color: "#4a86c8",
  },
};

// Map backend abbreviations to our internal keys
const abbreviationAliases: Record<string, string> = {
  gen: "genesis",
  md: "genesis",
  sat: "saturn",
  pce: "tg16",
  a26: "atari2600",
  mame: "arcade",
};

const defaultStyle: ConsoleStyle = {
  icon: Gamepad2,
  gradient: "from-surface-600 to-surface-900",
  color: "#adb5bd",
};

export function getConsoleStyle(abbreviation: string): ConsoleStyle {
  const key = abbreviation.toLowerCase();
  return (
    consoleStyles[key] ??
    consoleStyles[abbreviationAliases[key] ?? ""] ??
    defaultStyle
  );
}
