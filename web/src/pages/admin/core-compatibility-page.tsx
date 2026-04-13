import { AlertTriangle, CheckCircle2, Cpu } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Section, Skeleton, Badge } from "@/components/ui";
import { useCoreCompatibility } from "@/hooks/use-admin";

export function CoreCompatibilityPage() {
  const { data, isLoading, isError } = useCoreCompatibility();

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-3xl">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-64 w-full rounded-2xl" />
        </SectionList>
      </PageLayout>
    );
  }

  const consoles = data?.consoles ?? [];
  const mismatchCount = consoles.filter((c) => !c.matched).length;

  return (
    <PageLayout title="Core Compatibility" subtitle="Compare emulator cores used on native devices versus the web browser. Mismatched cores mean save states are not portable between platforms.">
      <SectionList className="max-w-3xl">
      {mismatchCount > 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-warning-500/30 bg-warning-500/10 px-4 py-3">
          <AlertTriangle className="h-5 w-5 text-warning-500 flex-shrink-0 mt-0.5" />
          <p className="text-sm text-warning-400">
            {mismatchCount} console{mismatchCount !== 1 ? "s" : ""} ha
            {mismatchCount !== 1 ? "ve" : "s"} different cores for native and
            web play. Save states created on one platform may not work on the
            other.
          </p>
        </div>
      )}

      <Section>
        <div className="px-5 pt-5 pb-2">
          <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
            <Cpu className="h-5 w-5 text-brand-400" />
            Consoles
          </h2>
        </div>
        <div className="px-5 pb-5">
          {isError ? (
            <p className="text-sm text-danger-500">
              Failed to load core compatibility data.
            </p>
          ) : consoles.length === 0 ? (
            <p className="text-sm text-surface-400">
              No console data available.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-surface-800 text-left">
                    <th className="py-2 pr-4 font-medium text-surface-400">
                      Console
                    </th>
                    <th className="py-2 pr-4 font-medium text-surface-400">
                      Native Core
                    </th>
                    <th className="py-2 pr-4 font-medium text-surface-400">
                      Web Core
                    </th>
                    <th className="py-2 font-medium text-surface-400">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {consoles.map((entry) => (
                    <tr
                      key={entry.consoleId}
                      className={
                        !entry.matched
                          ? "bg-warning-500/5"
                          : "hover:bg-surface-800/30"
                      }
                    >
                      <td className="py-2.5 pr-4 text-surface-100 font-medium">
                        {entry.consoleName}
                      </td>
                      <td className="py-2.5 pr-4 text-surface-300 font-mono text-xs">
                        {entry.nativeCore || (
                          <span className="text-surface-500 italic">none</span>
                        )}
                      </td>
                      <td className="py-2.5 pr-4 text-surface-300 font-mono text-xs">
                        {entry.webCore || (
                          <span className="text-surface-500 italic">none</span>
                        )}
                      </td>
                      <td className="py-2.5">
                        {entry.matched ? (
                          <Badge variant="success">
                            <CheckCircle2 className="h-3 w-3 mr-1" />
                            Matched
                          </Badge>
                        ) : (
                          <Badge variant="warning">
                            <AlertTriangle className="h-3 w-3 mr-1" />
                            Mismatched
                          </Badge>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Section>
    </SectionList>
    </PageLayout>
  );
}
