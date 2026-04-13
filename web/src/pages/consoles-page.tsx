import { useMemo } from "react";
import { Gamepad2 } from "lucide-react";
import { ConsoleCard } from "@/components/console-card";
import { ConsoleCardSkeleton, EmptyState } from "@/components/ui";
import { useConsoles } from "@/hooks/use-consoles";
import type { Console } from "@/types/api";
import { PageLayout, SectionList } from "@/components/layout";

interface GenerationInfo {
  label: string;
  years: string;
}

function generationInfo(generation: number): GenerationInfo {
  switch (generation) {
    case 2:
      return { label: "2nd Generation", years: "1976–1982" };
    case 3:
      return { label: "3rd Generation", years: "1983–1987" };
    case 4:
      return { label: "4th Generation", years: "1987–1993" };
    case 5:
      return { label: "5th Generation", years: "1993–1998" };
    case 6:
      return { label: "6th Generation", years: "1998–2005" };
    case 7:
      return { label: "7th Generation", years: "2005–2012" };
    case 8:
      return { label: "8th Generation", years: "2012–2020" };
    case 9:
      return { label: "9th Generation", years: "2020–present" };
    case 100:
      return { label: "Home Computers", years: "1977–1995" };
    case 101:
      return { label: "Arcade", years: "1971–present" };
    default:
      return { label: "Other", years: "" };
  }
}

function groupByGeneration(
  consoles: Console[],
): { generation: number; info: GenerationInfo; consoles: Console[] }[] {
  const map = new Map<number, Console[]>();
  for (const c of consoles) {
    const gen = c.generation || 0;
    if (!map.has(gen)) map.set(gen, []);
    map.get(gen)!.push(c);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a - b)
    .map(([gen, items]) => ({
      generation: gen,
      info: generationInfo(gen),
      consoles: items,
    }));
}

export function ConsolesPage() {
  const { data: consoles, isLoading } = useConsoles();
  const groups = useMemo(
    () => (consoles ? groupByGeneration(consoles) : []),
    [consoles],
  );

  return (
    <PageLayout title="Consoles" subtitle="Browse your game library by platform.">
      <SectionList>
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {Array.from({ length: 8 }, (_, i) => (
            <ConsoleCardSkeleton key={i} />
          ))}
        </div>
      ) : !consoles || consoles.length === 0 ? (
        <EmptyState
          icon={Gamepad2}
          title="No consoles found"
          description="No game consoles have been detected yet. Run a library scan to discover your games."
        />
      ) : (
        <div className="space-y-8">
          {groups.map((group) => (
            <section key={group.generation}>
              <div className="flex items-baseline gap-3 mb-4">
                <h2 className="text-lg font-semibold text-surface-200">
                  {group.info.label}
                </h2>
                {group.info.years && (
                  <span className="text-sm text-surface-500">
                    {group.info.years}
                  </span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
                {group.consoles.map((c) => (
                  <ConsoleCard key={c.id} console={c} />
                ))}
              </div>
            </section>
          ))}
        </div>
      )}
    </SectionList>
    </PageLayout>
  );
}
