import { cn } from "@/lib/cn";

const LETTERS = [
  "#",
  "A",
  "B",
  "C",
  "D",
  "E",
  "F",
  "G",
  "H",
  "I",
  "J",
  "K",
  "L",
  "M",
  "N",
  "O",
  "P",
  "Q",
  "R",
  "S",
  "T",
  "U",
  "V",
  "W",
  "X",
  "Y",
  "Z",
];

interface AlphabetBarProps {
  activeLetter?: string;
  onLetterClick: (letter: string | undefined) => void;
  orientation?: "vertical" | "horizontal";
}

export function AlphabetBar({
  activeLetter,
  onLetterClick,
  orientation = "horizontal",
}: AlphabetBarProps) {
  const isVertical = orientation === "vertical";

  return (
    <nav data-comp="AlphabetBar"
      aria-label="Alphabet quick-jump"
      className={cn(
        "flex gap-0.5",
        isVertical
          ? "flex-col items-center"
          : "flex-wrap items-center justify-center",
      )}
    >
      {LETTERS.map((letter) => {
        const isActive = activeLetter === letter;
        return (
          <button
            key={letter}
            type="button"
            onClick={() => onLetterClick(isActive ? undefined : letter)}
            className={cn(
              "flex items-center justify-center text-xs font-medium transition-colors",
              isVertical
                ? "h-6 w-6 rounded"
                : "h-7 min-w-[1.75rem] px-1 rounded-md",
              isActive
                ? "bg-brand-500 text-white"
                : "text-surface-400 hover:text-surface-100 hover:bg-surface-800",
            )}
            aria-label={`Jump to ${letter === "#" ? "numbers" : letter}`}
            aria-pressed={isActive}
          >
            {letter}
          </button>
        );
      })}
    </nav>
  );
}
