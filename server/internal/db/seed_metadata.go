package db

import (
	"fmt"
	"log/slog"

	"gorm.io/gorm"
)

// Helper functions for creating pointers to literal values.
func intPtr(v int) *int       { return &v }
func int64Ptr(v int64) *int64 { return &v }
func strPtr(v string) *string { return &v }

// SeedMediaTypeCategories inserts the broad media type categories if they don't exist.
func SeedMediaTypeCategories(db *gorm.DB) error {
	categories := []MediaTypeCategory{
		{Code: "cartridge", Name: "Cartridge"},
		{Code: "disc", Name: "Optical Disc"},
		{Code: "digital", Name: "Digital"},
		{Code: "board", Name: "Arcade Board"},
	}

	for _, cat := range categories {
		var existing MediaTypeCategory
		if err := db.Where("code = ?", cat.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := db.Create(&cat).Error; err != nil {
				return fmt.Errorf("seeding media type category %s: %w", cat.Code, err)
			}
			slog.Info("seeded media type category", "code", cat.Code)
		}
	}

	return nil
}

// SeedMediaTypes inserts the specific media type formats if they don't exist.
func SeedMediaTypes(db *gorm.DB) error {
	// Look up category IDs by code.
	categoryID := func(code string) (uint, error) {
		var cat MediaTypeCategory
		if err := db.Where("code = ?", code).First(&cat).Error; err != nil {
			return 0, fmt.Errorf("looking up media type category %q: %w", code, err)
		}
		return cat.ID, nil
	}

	cartridgeID, err := categoryID("cartridge")
	if err != nil {
		return err
	}
	discID, err := categoryID("disc")
	if err != nil {
		return err
	}
	digitalID, err := categoryID("digital")
	if err != nil {
		return err
	}
	boardID, err := categoryID("board")
	if err != nil {
		return err
	}

	mediaTypes := []MediaType{
		{Code: "cartridge", Name: "ROM Cartridge", CategoryID: cartridgeID},
		{Code: "game-card", Name: "Game Card", CategoryID: cartridgeID},
		{Code: "cd-rom", Name: "CD-ROM", CategoryID: discID},
		{Code: "gd-rom", Name: "GD-ROM", CategoryID: discID},
		{Code: "dvd-rom", Name: "DVD-ROM", CategoryID: discID},
		{Code: "blu-ray", Name: "Blu-ray Disc", CategoryID: discID},
		{Code: "umd", Name: "Universal Media Disc", CategoryID: discID},
		{Code: "digital", Name: "Digital Distribution", CategoryID: digitalID},
		{Code: "arcade-board", Name: "Arcade Board", CategoryID: boardID},
		{Code: "floppy-disk", Name: "Floppy Disk", CategoryID: discID},
		{Code: "hucard", Name: "HuCard", CategoryID: cartridgeID},
		{Code: "disc", Name: "Proprietary Disc", CategoryID: discID},
	}

	for _, mt := range mediaTypes {
		var existing MediaType
		if err := db.Where("code = ?", mt.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := db.Create(&mt).Error; err != nil {
				return fmt.Errorf("seeding media type %s: %w", mt.Code, err)
			}
			slog.Info("seeded media type", "code", mt.Code)
		}
	}

	return nil
}

// SeedHardwareMakers inserts the hardware manufacturer records if they don't exist.
func SeedHardwareMakers(db *gorm.DB) error {
	makers := []HardwareMaker{
		{Code: "nintendo", Name: "Nintendo"},
		{Code: "sega", Name: "Sega"},
		{Code: "sony", Name: "Sony"},
		{Code: "microsoft", Name: "Microsoft"},
		{Code: "atari", Name: "Atari"},
		{Code: "nec", Name: "NEC"},
		{Code: "snk", Name: "SNK"},
		{Code: "philips", Name: "Philips"},
		{Code: "commodore", Name: "Commodore"},
		{Code: "panasonic", Name: "Panasonic"},
		{Code: "bandai", Name: "Bandai"},
		{Code: "mattel", Name: "Mattel"},
		{Code: "coleco", Name: "Coleco"},
		{Code: "ibm", Name: "IBM"},
		{Code: "sharp", Name: "Sharp"},
		{Code: "fairchild", Name: "Fairchild"},
		{Code: "gce", Name: "GCE"},
		{Code: "magnavox", Name: "Magnavox"},
		// Umbrella maker for consoles that belong to no single manufacturer
		// (Arcade, DOS Demos, ScummVM). Keeps ConsoleResponse.maker always
		// non-null so the API contract matches the OpenAPI schema.
		{Code: "various", Name: "Various"},
	}

	for _, m := range makers {
		var existing HardwareMaker
		if err := db.Where("code = ?", m.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := db.Create(&m).Error; err != nil {
				return fmt.Errorf("seeding hardware maker %s: %w", m.Code, err)
			}
			slog.Info("seeded hardware maker", "code", m.Code)
		}
	}

	return nil
}

// makerID looks up a HardwareMaker by code and returns a pointer to its ID.
// Returns nil if the code is empty.
func makerID(db *gorm.DB, code string) *uint {
	if code == "" {
		return nil
	}
	var maker HardwareMaker
	if err := db.Where("code = ?", code).First(&maker).Error; err != nil {
		slog.Warn("hardware maker not found", "code", code)
		return nil
	}
	return &maker.ID
}

// mediaTypeID looks up a MediaType by code and returns a pointer to its ID.
// Returns nil if the code is empty.
func mediaTypeID(db *gorm.DB, code string) *uint {
	if code == "" {
		return nil
	}
	var mt MediaType
	if err := db.Where("code = ?", code).First(&mt).Error; err != nil {
		slog.Warn("media type not found", "code", code)
		return nil
	}
	return &mt.ID
}

// SeedConsoleMetadata applies the code, maker and media-type foreign keys
// from the console registry to the existing console rows. The catalog
// facts (release year, units sold, summary, tag) are no longer stored —
// they live in the registry and are derived into responses (#1443).
// Registry entries without metadata (empty Code/MakerCode/...) resolve to
// no-ops, since every update below is guarded on a non-empty value.
func SeedConsoleMetadata(db *gorm.DB) error {
	for _, m := range consoleRegistry {
		var console Console
		if err := db.Where("abbreviation = ?", m.Abbreviation).First(&console).Error; err != nil {
			if err == gorm.ErrRecordNotFound {
				slog.Warn("console not found for metadata seeding", "abbreviation", m.Abbreviation)
				continue
			}
			return fmt.Errorf("looking up console %s: %w", m.Abbreviation, err)
		}

		// Build update map with only non-nil fields.
		updates := map[string]interface{}{}

		if m.Code != "" && (console.Code == nil || *console.Code != m.Code) {
			updates["code"] = m.Code
		}

		mid := makerID(db, m.MakerCode)
		if mid != nil && (console.HardwareMakerID == nil || *console.HardwareMakerID != *mid) {
			updates["hardware_maker_id"] = *mid
		}

		mtid := mediaTypeID(db, m.MediaCode)
		if mtid != nil && (console.MediaTypeID == nil || *console.MediaTypeID != *mtid) {
			updates["media_type_id"] = *mtid
		}

		if len(updates) > 0 {
			if err := db.Model(&console).Updates(updates).Error; err != nil {
				return fmt.Errorf("updating console metadata for %s: %w", m.Abbreviation, err)
			}
			slog.Info("updated console metadata", "abbreviation", m.Abbreviation, "fields", len(updates))
		}
	}

	return nil
}
