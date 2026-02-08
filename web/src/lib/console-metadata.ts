import type { LucideIcon } from "lucide-react";
import {
  Gamepad2,
  Smartphone,
  Monitor,
  Joystick,
  Tv,
} from "lucide-react";

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
  atari2600: {
    icon: Tv,
    gradient: "from-amber-600 to-brown-900",
    color: "#d69e2e",
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
  return consoleStyles[key] ?? consoleStyles[abbreviationAliases[key] ?? ""] ?? defaultStyle;
}
