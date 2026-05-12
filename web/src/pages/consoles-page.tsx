import { useEffect, useMemo, useState } from "react";
import { Gamepad2 } from "lucide-react";
import { ConsoleCard } from "@/components/console-card";
import { ConsoleCardSkeleton, EmptyState, SegmentedControl } from "@/components/ui";
import { useBiosStatus } from "@/hooks/use-bios";
import { useConsoles } from "@/hooks/use-consoles";
import { PageLayout, SectionList } from "@/components/layout";
import {
  type ConsoleGrouping,
  groupConsoles,
} from "@/lib/console-grouping";

const STORAGE_KEY = "consoleListGrouping";

function readGroupingPreference(): ConsoleGrouping {
  if (typeof window === "undefined") return "generation";
  const v = window.localStorage.getItem(STORAGE_KEY);
  return v === "manufacturer" ? "manufacturer" : "generation";
}

export function ConsolesPage() {
  const { data: consoles, isLoading } = useConsoles();
  const { data: biosData } = useBiosStatus();
  const [grouping, setGrouping] = useState<ConsoleGrouping>(
    readGroupingPreference,
  );

  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(STORAGE_KEY, grouping);
  }, [grouping]);

  const groups = useMemo(
    () => (consoles ? groupConsoles(consoles, grouping) : []),
    [consoles, grouping],
  );

  // Set of consoleIds for which a required BIOS file is missing on disk.
  // Mirrors the player app's `state.consolesWithMissingBios` so both
  // clients flag the same consoles. See #933.
  const consolesWithMissingBios = useMemo(() => {
    const set = new Set<string>();
    for (const bc of biosData?.consoles ?? []) {
      if (bc.status === "missing" && bc.biosRequired) {
        set.add(bc.consoleId);
      }
    }
    return set;
  }, [biosData]);

  return (
    <PageLayout title="Consoles" subtitle="Browse your game library by platform.">
      <SectionList>
      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-5">
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
          <SegmentedControl<ConsoleGrouping>
            testId="console-grouping-toggle"
            label="Group by:"
            value={grouping}
            onChange={setGrouping}
            options={[
              {
                value: "generation",
                label: "Generation",
                testId: "console-grouping-generation",
              },
              {
                value: "manufacturer",
                label: "Manufacturer",
                testId: "console-grouping-manufacturer",
              },
            ]}
          />
          {groups.map((group) => (
            <section key={group.key}>
              <div className="flex items-baseline gap-3 mb-4">
                <h2 className="text-lg font-semibold text-surface-200">
                  {group.kind === "generation" ? group.info.label : group.makerName}
                </h2>
                {group.kind === "generation" && group.info.years && (
                  <span className="text-sm text-surface-500">
                    {group.info.years}
                  </span>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-5">
                {group.consoles.map((c) => (
                  <ConsoleCard
                    key={c.id}
                    console={c}
                    hasMissingBios={consolesWithMissingBios.has(c.id)}
                  />
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

export default ConsolesPage;
