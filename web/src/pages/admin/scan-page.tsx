import { PageLayout, SectionList } from "@/components/layout";
import { ScanCard } from "@/features/admin/components/scan-card";
import { ScrapeCard } from "@/features/admin/components/scrape-card";
import { GroupingStatus } from "@/features/admin/components/grouping-status";
import { ScrapeStatusCard } from "@/features/admin/components/scrape-status-card";

export function AdminScanPage() {
  return (
    <PageLayout title="Library Scan" subtitle="Scan game directories and update metadata.">
      <SectionList className="max-w-3xl">
        <GroupingStatus />

        <div className="grid gap-5 md:grid-cols-2">
          <ScanCard />
          <ScrapeCard />
        </div>

        <ScrapeStatusCard />
      </SectionList>
    </PageLayout>
  );
}

export default AdminScanPage;
