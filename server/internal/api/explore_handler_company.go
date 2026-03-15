package api

import (
	"fmt"
	"log/slog"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	"gorm.io/gorm"
)

// lookupCompanyInfo looks up company metadata by name. If a Company record
// exists in the database, it is returned immediately. If not, a background
// fetch from IGDB is triggered (lazy fetching) and the current request
// returns without company data. If the record is older than 30 days, a
// background refresh is triggered.
func (h *ExploreHandler) lookupCompanyInfo(name string) *CompanyInfo {
	if name == "" {
		return nil
	}

	var company db.Company
	if err := h.DB.Where("LOWER(name) = LOWER(?)", name).First(&company).Error; err != nil {
		// No record found — trigger background fetch if IGDB is configured
		h.triggerCompanyFetch(name)
		return nil
	}

	// Check if the record is stale (older than 30 days) — refresh in background
	if time.Since(company.UpdatedAt) > 30*24*time.Hour {
		h.triggerCompanyFetch(name)
	}

	return companyToInfo(&company)
}

// companyToInfo converts a db.Company record to a CompanyInfo response.
func companyToInfo(company *db.Company) *CompanyInfo {
	if company == nil {
		return nil
	}

	info := &CompanyInfo{
		LogoURL:      resolveImageURL(company.LogoURL),
		Description:  company.Description,
		FoundedYear:  company.FoundedYear,
		Country:      igdb.CountryName(company.Country),
		WebsiteURL:   company.WebsiteURL,
		WikipediaURL: company.WikipediaURL,
	}

	// Only return non-empty info
	if info.LogoURL == "" && info.Description == "" && info.FoundedYear == 0 &&
		info.Country == "" && info.WebsiteURL == "" && info.WikipediaURL == "" {
		return nil
	}

	return info
}

// triggerCompanyFetch starts a background goroutine to fetch company data
// from IGDB and store it in the database.
func (h *ExploreHandler) triggerCompanyFetch(name string) {
	if h.IGDBClient == nil || !h.IGDBClient.IsConfigured() {
		return
	}

	go func() {
		detail, err := h.IGDBClient.SearchCompanyByName(name)
		if err != nil {
			slog.Warn("background company fetch failed", "name", name, "error", err)
			return
		}
		if detail == nil {
			slog.Debug("company not found on IGDB", "name", name)
			return
		}

		storeCompanyDetail(h.DB, detail, h.Scraper)
	}()
}

// storeCompanyDetail creates or updates a Company record from IGDB data.
// If sc is non-nil, the company logo is downloaded and cached locally.
func storeCompanyDetail(database *gorm.DB, detail *igdb.CompanyDetail, sc *scraper.Scraper) {
	if detail == nil {
		return
	}

	logoURL := ""
	if detail.LogoImageID != "" && sc != nil {
		cdnURL := igdb.CompanyLogoURL(detail.LogoImageID)
		subpath := fmt.Sprintf("companies/%d/logo.png", detail.ID)
		if path := sc.DownloadExternalImage(cdnURL, subpath); path != "" {
			logoURL = path
		}
		// If download fails, logoURL stays empty — no CDN fallback
	}

	// Extract founding year from start_date Unix timestamp
	foundedYear := 0
	if detail.StartDate > 0 {
		foundedYear = time.Unix(detail.StartDate, 0).Year()
	}

	var existing db.Company
	if err := database.Where("igdb_company_id = ?", detail.ID).First(&existing).Error; err != nil {
		// Create new
		company := db.Company{
			IGDBCompanyID: detail.ID,
			Name:          detail.Name,
			Description:   detail.Description,
			LogoURL:       logoURL,
			Country:       detail.Country,
			FoundedYear:   foundedYear,
			WebsiteURL:    detail.WebsiteURL,
			WikipediaURL:  detail.WikipediaURL,
		}
		if err := database.Create(&company).Error; err != nil {
			slog.Warn("failed to create company record", "name", detail.Name, "error", err)
		} else {
			slog.Info("stored company metadata", "name", detail.Name, "igdbId", detail.ID)
		}
	} else {
		// Update existing
		updates := map[string]interface{}{
			"name":          detail.Name,
			"description":   detail.Description,
			"logo_url":      logoURL,
			"country":       detail.Country,
			"founded_year":  foundedYear,
			"website_url":   detail.WebsiteURL,
			"wikipedia_url": detail.WikipediaURL,
		}
		if err := database.Model(&existing).Updates(updates).Error; err != nil {
			slog.Warn("failed to update company record", "name", detail.Name, "error", err)
		}
	}
}
