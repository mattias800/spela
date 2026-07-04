package scraper

const (
	scrapeQueueTypeScrape            = "scrape"
	scrapeQueueTypeRAFetch           = "ra_fetch"
	scrapeQueueTypeTitleRootBackfill = "title_root_backfill"

	scrapeQueuePriorityMaintenance = -10

	scrapeJobModeTitleRootBackfill = "title_root_backfill"

	backfillTitleRootIGDBFlag = "backfill_title_root_igdb_v1"
)

func maintenanceScrapeJobModes() []string {
	return []string{scrapeJobModeTitleRootBackfill}
}
