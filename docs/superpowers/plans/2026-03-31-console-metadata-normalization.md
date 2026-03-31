# Console Metadata Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move console metadata (manufacturer, release year, media type, units sold, summary) from hardcoded player app data into the server database with proper normalized schema, and expose via API.

**Architecture:** Add three new tables (hardware_makers, media_types, media_type_categories) with foreign keys from consoles. Seed with static data sourced from IGDB. Update API responses to include nested metadata. Update web and player app to consume new fields.

**Tech Stack:** Go/GORM/Gin (server), React/TypeScript/TanStack Query (web), Kotlin Multiplatform/Compose (player)

---

### Task 1: Add new database models

**Files:**
- Modify: `server/internal/db/models.go`

- [ ] **Step 1: Add MediaTypeCategory model**

Add to `server/internal/db/models.go`:

```go
type MediaTypeCategory struct {
	ID   uint   `gorm:"primarykey" json:"id"`
	Code string `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name string `gorm:"size:64;not null" json:"name"`
}
```

- [ ] **Step 2: Add MediaType model**

Add to `server/internal/db/models.go`:

```go
type MediaType struct {
	ID         uint              `gorm:"primarykey" json:"id"`
	Code       string            `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name       string            `gorm:"size:64;not null" json:"name"`
	CategoryID uint              `gorm:"not null" json:"categoryId"`
	Category   MediaTypeCategory `gorm:"foreignKey:CategoryID" json:"category"`
}
```

- [ ] **Step 3: Add HardwareMaker model**

Add to `server/internal/db/models.go`:

```go
type HardwareMaker struct {
	ID        uint           `gorm:"primarykey" json:"id"`
	CreatedAt time.Time      `json:"createdAt"`
	UpdatedAt time.Time      `json:"updatedAt"`
	Code      string         `gorm:"uniqueIndex;size:32;not null" json:"code"`
	Name      string         `gorm:"size:128;not null" json:"name"`
	Consoles  []Console      `gorm:"foreignKey:HardwareMakerID" json:"consoles,omitempty"`
}
```

- [ ] **Step 4: Add new columns to Console model**

Update the existing `Console` struct in `server/internal/db/models.go` to add these fields after the existing `Playable` field:

```go
	Code             string          `gorm:"uniqueIndex;size:32" json:"code"`
	HardwareMakerID  *uint           `json:"hardwareMakerId"`
	HardwareMaker    *HardwareMaker  `gorm:"foreignKey:HardwareMakerID" json:"hardwareMaker,omitempty"`
	MediaTypeID      *uint           `json:"mediaTypeId"`
	MediaType        *MediaType      `gorm:"foreignKey:MediaTypeID" json:"mediaType,omitempty"`
	ReleaseYear      *int            `json:"releaseYear"`
	UnitsSold        *int64          `json:"unitsSold"`
	Summary          *string         `gorm:"type:text" json:"summary"`
```

Note: Use pointer types for nullable fields. `Code` is not nullable but initially empty for existing rows — the seed script will populate it.

- [ ] **Step 5: Register new models in AutoMigrate**

In `server/internal/db/database.go`, find the `db.AutoMigrate(...)` call and add the three new models BEFORE `&Console{}`:

```go
&MediaTypeCategory{},
&MediaType{},
&HardwareMaker{},
```

They must be before `&Console{}` so the foreign key targets exist when Console is migrated.

- [ ] **Step 6: Run tests to verify migration works**

Run: `cd server && go test ./internal/db/... -v -count=1`
Expected: PASS (existing tests should still pass with new columns)

- [ ] **Step 7: Commit**

```bash
git add server/internal/db/models.go server/internal/db/database.go
git commit -m "feat: add HardwareMaker, MediaType, MediaTypeCategory models + Console metadata columns"
```

---

### Task 2: Create seed data for reference tables and console metadata

**Files:**
- Create: `server/internal/db/seed_metadata.go`
- Modify: `server/internal/db/database.go`

- [ ] **Step 1: Create seed_metadata.go with media type category seeding**

Create `server/internal/db/seed_metadata.go`:

```go
package db

import (
	"fmt"
	"log/slog"

	"gorm.io/gorm"
)

func SeedMediaTypeCategories(database *gorm.DB) error {
	categories := []MediaTypeCategory{
		{Code: "cartridge", Name: "Cartridge"},
		{Code: "disc", Name: "Disc"},
		{Code: "digital", Name: "Digital"},
		{Code: "board", Name: "Board"},
	}
	for _, cat := range categories {
		var existing MediaTypeCategory
		if err := database.Where("code = ?", cat.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := database.Create(&cat).Error; err != nil {
				return fmt.Errorf("seeding media type category %s: %w", cat.Code, err)
			}
			slog.Info("seeded media type category", "code", cat.Code)
		}
	}
	return nil
}
```

- [ ] **Step 2: Add media type seeding function**

Add to `server/internal/db/seed_metadata.go`:

```go
func SeedMediaTypes(database *gorm.DB) error {
	// Helper to look up category ID by code
	categoryID := func(code string) uint {
		var cat MediaTypeCategory
		database.Where("code = ?", code).First(&cat)
		return cat.ID
	}

	types := []MediaType{
		{Code: "cartridge", Name: "Cartridge", CategoryID: categoryID("cartridge")},
		{Code: "game-card", Name: "Game Card", CategoryID: categoryID("cartridge")},
		{Code: "cd-rom", Name: "CD-ROM", CategoryID: categoryID("disc")},
		{Code: "gd-rom", Name: "GD-ROM", CategoryID: categoryID("disc")},
		{Code: "dvd-rom", Name: "DVD-ROM", CategoryID: categoryID("disc")},
		{Code: "blu-ray", Name: "Blu-ray", CategoryID: categoryID("disc")},
		{Code: "umd", Name: "UMD", CategoryID: categoryID("disc")},
		{Code: "digital", Name: "Digital", CategoryID: categoryID("digital")},
		{Code: "arcade-board", Name: "Arcade Board", CategoryID: categoryID("board")},
		{Code: "floppy-disk", Name: "Floppy Disk", CategoryID: categoryID("disc")},
		{Code: "hucard", Name: "HuCard", CategoryID: categoryID("cartridge")},
	}
	for _, mt := range types {
		var existing MediaType
		if err := database.Where("code = ?", mt.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := database.Create(&mt).Error; err != nil {
				return fmt.Errorf("seeding media type %s: %w", mt.Code, err)
			}
			slog.Info("seeded media type", "code", mt.Code)
		}
	}
	return nil
}
```

- [ ] **Step 3: Add hardware maker seeding function**

Add to `server/internal/db/seed_metadata.go`:

```go
func SeedHardwareMakers(database *gorm.DB) error {
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
	}
	for _, m := range makers {
		var existing HardwareMaker
		if err := database.Where("code = ?", m.Code).First(&existing).Error; err == gorm.ErrRecordNotFound {
			if err := database.Create(&m).Error; err != nil {
				return fmt.Errorf("seeding hardware maker %s: %w", m.Code, err)
			}
			slog.Info("seeded hardware maker", "code", m.Code)
		}
	}
	return nil
}
```

- [ ] **Step 4: Add console metadata seeding function**

Add to `server/internal/db/seed_metadata.go`:

```go
func SeedConsoleMetadata(database *gorm.DB) error {
	// Helper lookups
	makerID := func(code string) *uint {
		var m HardwareMaker
		if err := database.Where("code = ?", code).First(&m).Error; err != nil {
			return nil
		}
		id := m.ID
		return &id
	}
	mediaTypeID := func(code string) *uint {
		var mt MediaType
		if err := database.Where("code = ?", code).First(&mt).Error; err != nil {
			return nil
		}
		id := mt.ID
		return &id
	}
	intPtr := func(v int) *int { return &v }
	int64Ptr := func(v int64) *int64 { return &v }
	strPtr := func(v string) *string { return &v }

	type consoleMeta struct {
		Abbreviation    string
		Code            string
		HardwareMakerID *uint
		MediaTypeID     *uint
		ReleaseYear     *int
		UnitsSold       *int64
		Summary         *string
	}

	metas := []consoleMeta{
		{Abbreviation: "NES", Code: "nes", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1983), UnitsSold: int64Ptr(61910000), Summary: strPtr("The Nintendo Entertainment System (NES) is an 8-bit home video game console developed and manufactured by Nintendo. It was first released in Japan in 1983 as the Family Computer (Famicom), and was later released in North America, Europe, and Australia. The NES helped revitalize the US video game industry after the 1983 crash.")},
		{Abbreviation: "SMS", Code: "sms", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1985), UnitsSold: int64Ptr(13000000), Summary: strPtr("The Sega Master System is an 8-bit home video game console manufactured by Sega. It was originally a remodeled export version of the Sega Mark III, the third iteration of the SG-1000 series of consoles.")},
		{Abbreviation: "SNES", Code: "snes", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(49100000), Summary: strPtr("The Super Nintendo Entertainment System (SNES) is a 16-bit home video game console developed by Nintendo. In Japan, it is called the Super Famicom. It was Nintendo's second programmable home console, following the NES.")},
		{Abbreviation: "GEN", Code: "genesis", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1988), UnitsSold: int64Ptr(30750000), Summary: strPtr("The Sega Genesis, known as the Mega Drive outside North America, is a 16-bit home video game console developed and sold by Sega. The Genesis was Sega's third console and the successor to the Master System.")},
		{Abbreviation: "GB", Code: "gb", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1989), UnitsSold: int64Ptr(118690000), Summary: strPtr("The Game Boy is an 8-bit handheld game console developed and manufactured by Nintendo. The first handheld in the Game Boy family, it was first released in Japan in 1989. It is the second most popular gaming system of all time.")},
		{Abbreviation: "GG", Code: "gamegear", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(10620000), Summary: strPtr("The Sega Game Gear is an 8-bit handheld game console released by Sega. It was Sega's first handheld game console and was developed in response to Nintendo's Game Boy.")},
		{Abbreviation: "PCE", Code: "pce", HardwareMakerID: makerID("nec"), MediaTypeID: mediaTypeID("hucard"), ReleaseYear: intPtr(1987), UnitsSold: int64Ptr(10000000), Summary: strPtr("The TurboGrafx-16 (known as PC Engine in Japan) is a home video game console jointly developed by Hudson Soft and NEC. It was the first console to have an optional CD-ROM add-on.")},
		{Abbreviation: "PCECD", Code: "pcecd", HardwareMakerID: makerID("nec"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1988), Summary: strPtr("The TurboGrafx-CD (PC Engine CD-ROM²) is a CD-ROM add-on for the TurboGrafx-16. It was the first video game console to use CD-ROMs as a storage medium.")},
		{Abbreviation: "NEOGEO", Code: "neogeo", HardwareMakerID: makerID("snk"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(1000000), Summary: strPtr("The Neo Geo is a cartridge-based arcade system and home video game console released by SNK. The home system was one of the most powerful and expensive consoles of its era.")},
		{Abbreviation: "NEOCD", Code: "neocd", HardwareMakerID: makerID("snk"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1994), Summary: strPtr("The Neo Geo CD is a home video game console by SNK. It is a CD-ROM-based version of the Neo Geo cartridge system, offering the same games at a lower price point.")},
		{Abbreviation: "LYNX", Code: "lynx", HardwareMakerID: makerID("atari"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1989), UnitsSold: int64Ptr(3000000), Summary: strPtr("The Atari Lynx is a handheld game console released by Atari. It was the world's first handheld electronic game with a color LCD display, and the first with hardware-accelerated graphics.")},
		{Abbreviation: "SCD", Code: "segacd", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1991), UnitsSold: int64Ptr(2240000), Summary: strPtr("The Sega CD, known as Mega-CD outside North America, is a CD-ROM accessory for the Sega Genesis. It allowed the Genesis to play CD-based games with enhanced audio and full-motion video.")},
		{Abbreviation: "CDI", Code: "cdi", HardwareMakerID: makerID("philips"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1991), UnitsSold: int64Ptr(570000), Summary: strPtr("The Philips CD-i is an interactive multimedia CD player developed and marketed by Philips. It was designed as a multimedia platform but also served as a game console.")},
		{Abbreviation: "PSX", Code: "psx", HardwareMakerID: makerID("sony"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(102490000), Summary: strPtr("The PlayStation is a home video game console developed and marketed by Sony Computer Entertainment. It was the first of the PlayStation series of consoles and handheld game devices. It introduced CD-ROMs as the standard storage medium for gaming.")},
		{Abbreviation: "N64", Code: "n64", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1996), UnitsSold: int64Ptr(32930000), Summary: strPtr("The Nintendo 64 (N64) is a home video game console developed and marketed by Nintendo. Named for its 64-bit central processing unit, it was Nintendo's third home console.")},
		{Abbreviation: "SAT", Code: "saturn", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(9260000), Summary: strPtr("The Sega Saturn is a 32-bit home video game console developed by Sega. It was the successor to the Sega Genesis and competed with the PlayStation and Nintendo 64.")},
		{Abbreviation: "GBC", Code: "gbc", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(49020000), Summary: strPtr("The Game Boy Color (GBC) is a handheld game console manufactured by Nintendo. It is the successor to the original Game Boy and added a color screen while maintaining backward compatibility.")},
		{Abbreviation: "JAG", Code: "jaguar", HardwareMakerID: makerID("atari"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1993), UnitsSold: int64Ptr(250000), Summary: strPtr("The Atari Jaguar is a home video game console developed by Atari Corporation. It was the last game console to be marketed under the Atari brand until the Atari VCS in 2020.")},
		{Abbreviation: "VB", Code: "virtualboy", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1995), UnitsSold: int64Ptr(770000), Summary: strPtr("The Virtual Boy is a 32-bit table-top video game console developed and manufactured by Nintendo. It was the first console to display true 3D graphics using a parallax effect.")},
		{Abbreviation: "3DO", Code: "3do", HardwareMakerID: makerID("panasonic"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1993), UnitsSold: int64Ptr(2000000), Summary: strPtr("The 3DO Interactive Multiplayer is a home video game console. The 3DO technology was designed by The 3DO Company and manufactured by several companies including Panasonic, Goldstar, and Sanyo.")},
		{Abbreviation: "NGP", Code: "ngp", HardwareMakerID: makerID("snk"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(2000000), Summary: strPtr("The Neo Geo Pocket is a monochrome handheld game console released by SNK. It was later succeeded by the Neo Geo Pocket Color.")},
		{Abbreviation: "WS", Code: "wonderswan", HardwareMakerID: makerID("bandai"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1999), UnitsSold: int64Ptr(3500000), Summary: strPtr("The WonderSwan is a handheld game console released by Bandai. It was designed by Gunpei Yokoi, the creator of the Game Boy, after he left Nintendo.")},
		{Abbreviation: "32X", Code: "32x", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(665000), Summary: strPtr("The Sega 32X is an add-on for the Sega Genesis that adds 32-bit processing capabilities. It was designed to extend the life of the Genesis as a transitional device before the Saturn.")},
		{Abbreviation: "PS2", Code: "ps2", HardwareMakerID: makerID("sony"), MediaTypeID: mediaTypeID("dvd-rom"), ReleaseYear: intPtr(2000), UnitsSold: int64Ptr(155000000), Summary: strPtr("The PlayStation 2 (PS2) is a home video game console developed by Sony. It is the best-selling video game console of all time, having sold over 155 million units worldwide.")},
		{Abbreviation: "DC", Code: "dreamcast", HardwareMakerID: makerID("sega"), MediaTypeID: mediaTypeID("gd-rom"), ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(9130000), Summary: strPtr("The Dreamcast is a home video game console released by Sega. It was the first sixth-generation console and featured built-in modem for online play. It was Sega's last home console.")},
		{Abbreviation: "GC", Code: "gamecube", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("dvd-rom"), ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(21740000), Summary: strPtr("The Nintendo GameCube is a home video game console released by Nintendo. It is the successor to the Nintendo 64 and uses proprietary miniDVD-based discs.")},
		{Abbreviation: "GBA", Code: "gba", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(81510000), Summary: strPtr("The Game Boy Advance (GBA) is a 32-bit handheld game console developed and manufactured by Nintendo. It is the successor to the Game Boy Color.")},
		{Abbreviation: "XBOX", Code: "xbox", HardwareMakerID: makerID("microsoft"), MediaTypeID: mediaTypeID("dvd-rom"), ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(24000000), Summary: strPtr("The Xbox is a home video game console and the first installment in the Xbox series of consoles manufactured by Microsoft. It was released as Microsoft's entry into the gaming console market.")},
		{Abbreviation: "WII", Code: "wii", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("dvd-rom"), ReleaseYear: intPtr(2006), UnitsSold: int64Ptr(101630000), Summary: strPtr("The Wii is a home video game console released by Nintendo. Its motion-sensing controllers and accessible design made it one of the best-selling consoles of all time.")},
		{Abbreviation: "PS3", Code: "ps3", HardwareMakerID: makerID("sony"), MediaTypeID: mediaTypeID("blu-ray"), ReleaseYear: intPtr(2006), UnitsSold: int64Ptr(87400000), Summary: strPtr("The PlayStation 3 (PS3) is a home video game console developed by Sony. It was the first console to use Blu-ray discs as its primary storage medium.")},
		{Abbreviation: "X360", Code: "x360", HardwareMakerID: makerID("microsoft"), MediaTypeID: mediaTypeID("dvd-rom"), ReleaseYear: intPtr(2005), UnitsSold: int64Ptr(84700000), Summary: strPtr("The Xbox 360 is a home video game console developed by Microsoft. It is the second console in the Xbox series and competed with Sony's PlayStation 3 and Nintendo's Wii.")},
		{Abbreviation: "PSP", Code: "psp", HardwareMakerID: makerID("sony"), MediaTypeID: mediaTypeID("umd"), ReleaseYear: intPtr(2004), UnitsSold: int64Ptr(80000000), Summary: strPtr("The PlayStation Portable (PSP) is a handheld game console developed and marketed by Sony. It was the first handheld console to use an optical disc format, the Universal Media Disc (UMD).")},
		{Abbreviation: "NDS", Code: "nds", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("game-card"), ReleaseYear: intPtr(2004), UnitsSold: int64Ptr(154020000), Summary: strPtr("The Nintendo DS is a handheld game console produced by Nintendo. Its distinguishing feature is its dual screens, the bottom one being a touchscreen.")},
		{Abbreviation: "3DS", Code: "3ds", HardwareMakerID: makerID("nintendo"), MediaTypeID: mediaTypeID("game-card"), ReleaseYear: intPtr(2011), UnitsSold: int64Ptr(75940000), Summary: strPtr("The Nintendo 3DS is a handheld game console produced by Nintendo. It is capable of displaying stereoscopic 3D effects without the use of 3D glasses or additional accessories.")},
		{Abbreviation: "A26", Code: "a26", HardwareMakerID: makerID("atari"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1977), UnitsSold: int64Ptr(30000000), Summary: strPtr("The Atari 2600 is a home video game console developed and produced by Atari. It is credited with popularizing the use of microprocessor-based hardware and game cartridges.")},
		{Abbreviation: "A52", Code: "a52", HardwareMakerID: makerID("atari"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1982), UnitsSold: int64Ptr(1000000), Summary: strPtr("The Atari 5200 SuperSystem is a home video game console introduced by Atari in 1982 as a higher-end complement to the popular Atari 2600.")},
		{Abbreviation: "A78", Code: "a78", HardwareMakerID: makerID("atari"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1986), UnitsSold: int64Ptr(3770000), Summary: strPtr("The Atari 7800 ProSystem is a home video game console developed by Atari. It is almost fully backward-compatible with the Atari 2600.")},
		{Abbreviation: "C64", Code: "c64", HardwareMakerID: makerID("commodore"), MediaTypeID: mediaTypeID("floppy-disk"), ReleaseYear: intPtr(1982), UnitsSold: int64Ptr(17000000), Summary: strPtr("The Commodore 64 is an 8-bit home computer introduced by Commodore International. It is listed in the Guinness World Records as the highest-selling single computer model of all time.")},
		{Abbreviation: "AMIGA", Code: "amiga", HardwareMakerID: makerID("commodore"), MediaTypeID: mediaTypeID("floppy-disk"), ReleaseYear: intPtr(1985), Summary: strPtr("The Amiga is a family of personal computers introduced by Commodore. The Amiga provided a significant upgrade from 8-bit computers and was notable for its advanced multimedia capabilities.")},
		{Abbreviation: "ACD32", Code: "acd32", HardwareMakerID: makerID("commodore"), MediaTypeID: mediaTypeID("cd-rom"), ReleaseYear: intPtr(1993), Summary: strPtr("The Amiga CD32 is a home video game console developed by Commodore. It was the first 32-bit CD-ROM-based console and was based on Commodore's Amiga 1200 computer.")},
		{Abbreviation: "DOS", Code: "dos", HardwareMakerID: makerID("ibm"), MediaTypeID: mediaTypeID("floppy-disk"), ReleaseYear: intPtr(1981), Summary: strPtr("DOS (Disk Operating System) was the dominant operating system for IBM PC compatible personal computers during the 1980s and early 1990s. It became the primary platform for early PC gaming.")},
		{Abbreviation: "MSX1", Code: "msx1", HardwareMakerID: makerID("microsoft"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1983), Summary: strPtr("MSX is a standardized home computer architecture announced by Microsoft and ASCII Corporation in 1983. It was a standard across many manufacturers in Japan, South Korea, and parts of Europe and South America.")},
		{Abbreviation: "MSX2", Code: "msx2", HardwareMakerID: makerID("microsoft"), MediaTypeID: mediaTypeID("cartridge"), ReleaseYear: intPtr(1985), Summary: strPtr("MSX2 is the second generation of the MSX home computer standard. It added enhanced graphics capabilities and more memory compared to the original MSX.")},
		{Abbreviation: "ARCADE", Code: "arcade", HardwareMakerID: nil, MediaTypeID: mediaTypeID("arcade-board"), ReleaseYear: intPtr(1971), Summary: strPtr("Arcade games are coin-operated entertainment machines typically installed in public businesses such as restaurants, bars, and dedicated amusement arcades. They span the entire history of video gaming.")},
		{Abbreviation: "ADEMO", Code: "ademo", HardwareMakerID: makerID("commodore"), MediaTypeID: mediaTypeID("floppy-disk"), Summary: strPtr("Amiga demo scene productions. The Amiga demoscene is a computer art subculture focused on producing demos — non-interactive multimedia presentations.")},
		{Abbreviation: "DDEMO", Code: "ddemo", HardwareMakerID: nil, MediaTypeID: mediaTypeID("digital"), Summary: strPtr("DOS demo scene productions. The PC demoscene produces audiovisual presentations that run in real-time, showcasing programming, art, and music skills.")},
	}

	for _, m := range metas {
		var console Console
		if err := database.Where("abbreviation = ?", m.Abbreviation).First(&console).Error; err != nil {
			slog.Warn("console not found for metadata seeding", "abbreviation", m.Abbreviation)
			continue
		}
		updates := map[string]interface{}{
			"code": m.Code,
		}
		if m.HardwareMakerID != nil {
			updates["hardware_maker_id"] = *m.HardwareMakerID
		}
		if m.MediaTypeID != nil {
			updates["media_type_id"] = *m.MediaTypeID
		}
		if m.ReleaseYear != nil {
			updates["release_year"] = *m.ReleaseYear
		}
		if m.UnitsSold != nil {
			updates["units_sold"] = *m.UnitsSold
		}
		if m.Summary != nil {
			updates["summary"] = *m.Summary
		}
		if err := database.Model(&console).Updates(updates).Error; err != nil {
			return fmt.Errorf("updating console metadata for %s: %w", m.Abbreviation, err)
		}
		slog.Info("seeded console metadata", "abbreviation", m.Abbreviation, "code", m.Code)
	}
	return nil
}
```

- [ ] **Step 2: Call seed functions from database initialization**

In `server/internal/db/database.go`, find where `SeedConsoles(db)` is called (in the `Initialize` function) and add the metadata seed calls AFTER `SeedConsoles`:

```go
if err := SeedMediaTypeCategories(db); err != nil {
	return nil, fmt.Errorf("seeding media type categories: %w", err)
}
if err := SeedMediaTypes(db); err != nil {
	return nil, fmt.Errorf("seeding media types: %w", err)
}
if err := SeedHardwareMakers(db); err != nil {
	return nil, fmt.Errorf("seeding hardware makers: %w", err)
}
if err := SeedConsoleMetadata(db); err != nil {
	return nil, fmt.Errorf("seeding console metadata: %w", err)
}
```

- [ ] **Step 3: Run tests**

Run: `cd server && go test ./internal/db/... -v -count=1`
Expected: PASS

- [ ] **Step 4: Run server locally to verify seeding**

Run: `cd server && source .env && go run ./cmd/server`
Expected: Server starts, logs show "seeded media type category", "seeded media type", "seeded hardware maker", "seeded console metadata" entries. Check database has populated data.

- [ ] **Step 5: Commit**

```bash
git add server/internal/db/seed_metadata.go server/internal/db/database.go
git commit -m "feat: seed hardware makers, media types, and console metadata"
```

---

### Task 3: Update API responses and console handlers

**Files:**
- Modify: `server/internal/api/responses.go`
- Modify: `server/internal/api/console_handler.go`

- [ ] **Step 1: Add response types for maker and media type**

Add to `server/internal/api/responses.go`:

```go
type MediaTypeCategoryResponse struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

type MediaTypeResponse struct {
	Code     string                    `json:"code"`
	Name     string                    `json:"name"`
	Category MediaTypeCategoryResponse `json:"category"`
}

type HardwareMakerResponse struct {
	Code string `json:"code"`
	Name string `json:"name"`
}

type MakerDetailResponse struct {
	Code         string            `json:"code"`
	Name         string            `json:"name"`
	ConsoleCount int               `json:"consoleCount"`
	Consoles     []ConsoleResponse `json:"consoles,omitempty"`
}
```

- [ ] **Step 2: Update ConsoleResponse with new fields**

Update the `ConsoleResponse` struct in `server/internal/api/responses.go` to add these fields:

```go
	Code             string                 `json:"code"`
	Maker            *HardwareMakerResponse `json:"maker"`
	MediaType        *MediaTypeResponse     `json:"mediaType"`
	ReleaseYear      *int                   `json:"releaseYear"`
	UnitsSold        *int64                 `json:"unitsSold"`
	Summary          *string                `json:"summary"`
```

- [ ] **Step 3: Update ToConsoleResponse function**

Update the `ToConsoleResponse` function in `server/internal/api/responses.go` to populate the new fields:

```go
func ToConsoleResponse(c db.Console) ConsoleResponse {
	exts := strings.Split(c.Extensions, ",")
	for i := range exts {
		exts[i] = strings.TrimSpace(exts[i])
	}

	ratio := parseAspectRatio(c.CoverAspect)

	abbr := strings.ToLower(c.Abbreviation)
	code := c.Code
	if code == "" {
		code = abbr // fallback for un-seeded consoles
	}

	resp := ConsoleResponse{
		ID:               abbr,
		Code:             code,
		CreatedAt:        c.CreatedAt,
		UpdatedAt:        c.UpdatedAt,
		Name:             c.Name,
		Abbreviation:     c.Abbreviation,
		Extensions:       exts,
		DefaultCore:      c.DefaultCore,
		EmulatorJSCore:   c.EmulatorJSCore,
		CoverAspectRatio: ratio,
		ColorTheme:       c.ColorTheme,
		Generation:       c.Generation,
		IconURL:          "/api/consoles/" + abbr + "/icon",
		LogoURL:          "/api/consoles/" + abbr + "/logo",
		LogoPngURL:       "/api/consoles/" + abbr + "/logo.png",
		GameCount:        c.GameCount,
		SaveStateSupport: c.SaveStateSupport,
		BrowserPlayable:  c.EmulatorJSCore != "",
		Playable:         c.Playable,
		ReleaseYear:      c.ReleaseYear,
		UnitsSold:        c.UnitsSold,
		Summary:          c.Summary,
	}

	if c.HardwareMaker != nil {
		resp.Maker = &HardwareMakerResponse{
			Code: c.HardwareMaker.Code,
			Name: c.HardwareMaker.Name,
		}
	}

	if c.MediaType != nil {
		resp.MediaType = &MediaTypeResponse{
			Code: c.MediaType.Code,
			Name: c.MediaType.Name,
			Category: MediaTypeCategoryResponse{
				Code: c.MediaType.Category.Code,
				Name: c.MediaType.Category.Name,
			},
		}
	}

	return resp
}
```

- [ ] **Step 4: Update ListConsoles handler to preload relationships**

In `server/internal/api/console_handler.go`, update the `ListConsoles` function. Change the query from:

```go
if err := h.DB.Order("generation ASC, name ASC").Find(&consoles).Error; err != nil {
```

to:

```go
if err := h.DB.Preload("HardwareMaker").Preload("MediaType").Preload("MediaType.Category").Order("generation ASC, name ASC").Find(&consoles).Error; err != nil {
```

- [ ] **Step 5: Update any single-console handler to preload and lookup by code**

Find any handler that looks up a console by abbreviation (e.g. `ListConsoleGames`) and update to also try the `code` column, and add preloading. Example pattern:

```go
var console db.Console
if err := h.DB.Preload("HardwareMaker").Preload("MediaType").Preload("MediaType.Category").
    Where("LOWER(abbreviation) = ? OR code = ?", id, id).First(&console).Error; err != nil {
```

This ensures both old abbreviation-based and new code-based lookups work.

- [ ] **Step 6: Run tests**

Run: `cd server && go test ./internal/api/... -v -count=1`
Expected: PASS (existing tests should pass — they test structure, new fields are additive)

- [ ] **Step 7: Commit**

```bash
git add server/internal/api/responses.go server/internal/api/console_handler.go
git commit -m "feat: include maker, media type, and metadata in console API responses"
```

---

### Task 4: Add maker API endpoints

**Files:**
- Create: `server/internal/api/maker_handler.go`
- Create: `server/internal/api/maker_handler_test.go`
- Modify: `server/internal/api/router.go`

- [ ] **Step 1: Create maker handler**

Create `server/internal/api/maker_handler.go`:

```go
package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"gorm.io/gorm"

	"spela/internal/db"
)

type MakerHandler struct {
	DB *gorm.DB
}

func (h *MakerHandler) ListMakers(c *gin.Context) {
	var makers []db.HardwareMaker
	if err := h.DB.Order("name ASC").Find(&makers).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch makers"})
		return
	}

	result := make([]MakerDetailResponse, 0, len(makers))
	for _, m := range makers {
		var count int64
		h.DB.Model(&db.Console{}).
			Joins("JOIN games ON games.console_id = consoles.id AND games.is_primary = ? AND games.is_pre_release = ?", true, false).
			Where("consoles.hardware_maker_id = ?", m.ID).
			Distinct("consoles.id").
			Count(&count)

		if count > 0 {
			result = append(result, MakerDetailResponse{
				Code:         m.Code,
				Name:         m.Name,
				ConsoleCount: int(count),
			})
		}
	}

	c.JSON(http.StatusOK, result)
}

func (h *MakerHandler) GetMaker(c *gin.Context) {
	code := c.Param("code")

	var maker db.HardwareMaker
	if err := h.DB.Where("code = ?", code).First(&maker).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "maker not found"})
		return
	}

	var consoles []db.Console
	h.DB.Preload("HardwareMaker").Preload("MediaType").Preload("MediaType.Category").
		Where("hardware_maker_id = ?", maker.ID).
		Order("generation ASC, name ASC").
		Find(&consoles)

	// Attach game counts
	for i := range consoles {
		var count int64
		h.DB.Model(&db.Game{}).Where("console_id = ? AND is_primary = ? AND is_pre_release = ?", consoles[i].ID, true, false).Count(&count)
		consoles[i].GameCount = int(count)
	}

	consoleResponses := make([]ConsoleResponse, 0)
	for _, con := range consoles {
		if con.GameCount > 0 {
			consoleResponses = append(consoleResponses, ToConsoleResponse(con))
		}
	}

	c.JSON(http.StatusOK, MakerDetailResponse{
		Code:         maker.Code,
		Name:         maker.Name,
		ConsoleCount: len(consoleResponses),
		Consoles:     consoleResponses,
	})
}
```

- [ ] **Step 2: Register maker routes**

In `server/internal/api/router.go`, add after the console handler setup:

```go
makerHandler := &MakerHandler{DB: cfg.DB}
```

And in the routes section:

```go
api.GET("/makers", makerHandler.ListMakers)
api.GET("/makers/:code", makerHandler.GetMaker)
```

- [ ] **Step 3: Write maker handler tests**

Create `server/internal/api/maker_handler_test.go`:

```go
package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"spela/internal/db"
)

func setupMakerTestEnv(t *testing.T) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(
		&db.MediaTypeCategory{},
		&db.MediaType{},
		&db.HardwareMaker{},
		&db.Console{},
		&db.Game{},
	)
	require.NoError(t, err)

	// Seed reference data
	require.NoError(t, db.SeedMediaTypeCategories(database))
	require.NoError(t, db.SeedMediaTypes(database))
	require.NoError(t, db.SeedHardwareMakers(database))
	require.NoError(t, db.SeedConsoles(database))
	require.NoError(t, db.SeedConsoleMetadata(database))

	gin.SetMode(gin.TestMode)
	router := gin.New()

	return database, router
}

func TestListMakers_ReturnsOnlyMakersWithGames(t *testing.T) {
	database, router := setupMakerTestEnv(t)

	handler := &MakerHandler{DB: database}
	router.GET("/api/makers", handler.ListMakers)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/makers", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var makers []MakerDetailResponse
	err := json.Unmarshal(w.Body.Bytes(), &makers)
	require.NoError(t, err)
	// No games seeded, so no makers should be returned
	assert.Empty(t, makers)
}

func TestGetMaker_NotFound(t *testing.T) {
	database, router := setupMakerTestEnv(t)

	handler := &MakerHandler{DB: database}
	router.GET("/api/makers/:code", handler.GetMaker)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/makers/nonexistent", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetMaker_Found(t *testing.T) {
	database, router := setupMakerTestEnv(t)

	handler := &MakerHandler{DB: database}
	router.GET("/api/makers/:code", handler.GetMaker)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/makers/nintendo", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var maker MakerDetailResponse
	err := json.Unmarshal(w.Body.Bytes(), &maker)
	require.NoError(t, err)
	assert.Equal(t, "nintendo", maker.Code)
	assert.Equal(t, "Nintendo", maker.Name)
}
```

- [ ] **Step 4: Run tests**

Run: `cd server && go test ./internal/api/... -v -count=1`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/internal/api/maker_handler.go server/internal/api/maker_handler_test.go server/internal/api/router.go
git commit -m "feat: add maker list and detail API endpoints"
```

---

### Task 5: Update web frontend types and console hero banner

**Files:**
- Modify: `web/src/types/api.ts`
- Modify: `web/src/components/console-hero-banner.tsx`

- [ ] **Step 1: Update TypeScript Console interface**

In `web/src/types/api.ts`, add new interfaces and update Console:

```typescript
export interface HardwareMaker {
  code: string;
  name: string;
}

export interface MediaTypeCategory {
  code: string;
  name: string;
}

export interface MediaType {
  code: string;
  name: string;
  category: MediaTypeCategory;
}
```

Add to the existing `Console` interface:

```typescript
  code: string;
  maker: HardwareMaker | null;
  mediaType: MediaType | null;
  releaseYear: number | null;
  unitsSold: number | null;
  summary: string | null;
```

- [ ] **Step 2: Update console hero banner to show metadata**

In `web/src/components/console-hero-banner.tsx`, update the metadata row section (after the game count span) to include maker and release year:

```tsx
        {/* Metadata row */}
        <div className="flex flex-wrap items-center justify-center gap-3 mt-4">
          <span className="text-sm font-medium text-white/70">
            {count} {count === 1 ? "game" : "games"}
          </span>
          {consoleData?.maker && (
            <span className="text-sm font-medium text-white/70">
              {consoleData.maker.name}
            </span>
          )}
          {consoleData?.releaseYear && (
            <span className="text-sm font-medium text-white/70">
              {consoleData.releaseYear}
            </span>
          )}
          {consoleData?.mediaType && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 backdrop-blur-sm px-3 py-1 text-xs font-medium text-white/90">
              {consoleData.mediaType.name}
            </span>
          )}
          {consoleData?.saveStateSupport && (
```

- [ ] **Step 3: Run web tests**

Run: `cd web && npm run test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add web/src/types/api.ts web/src/components/console-hero-banner.tsx
git commit -m "feat: show console metadata (maker, year, media type) in web hero banner"
```

---

### Task 6: Update player app models and remove hardcoded metadata

**Files:**
- Modify: `player/shared/.../data/remote/dto/Dtos.kt`
- Modify: `player/shared/.../data/remote/dto/DtoMappers.kt`
- Modify: `player/shared/.../domain/model/Models.kt`
- Modify: `player/shared/.../feature/library/ConsoleMetadata.kt`
- Modify: `player/shared/.../feature/library/ConsoleComponents.kt`

- [ ] **Step 1: Add DTO classes for maker and media type**

In `player/shared/.../data/remote/dto/Dtos.kt`, add:

```kotlin
@Serializable
data class HardwareMakerDto(
    val code: String,
    val name: String,
)

@Serializable
data class MediaTypeCategoryDto(
    val code: String,
    val name: String,
)

@Serializable
data class MediaTypeDto(
    val code: String,
    val name: String,
    val category: MediaTypeCategoryDto,
)
```

Add to `ConsoleDto`:

```kotlin
    val code: String = "",
    val maker: HardwareMakerDto? = null,
    val mediaType: MediaTypeDto? = null,
    val releaseYear: Int? = null,
    val unitsSold: Long? = null,
    val summary: String? = null,
```

- [ ] **Step 2: Update Console domain model**

In `player/shared/.../domain/model/Models.kt`, add to the `Console` data class:

```kotlin
    val code: String = "",
    val makerName: String? = null,
    val makerCode: String? = null,
    val mediaTypeName: String? = null,
    val releaseYear: Int? = null,
    val unitsSold: Long? = null,
    val summary: String? = null,
```

- [ ] **Step 3: Update DTO mapper**

In `player/shared/.../data/remote/dto/DtoMappers.kt`, update `ConsoleDto.toDomain()` to map new fields:

```kotlin
fun ConsoleDto.toDomain(): Console = Console(
    id = id,
    name = name,
    abbreviation = abbreviation,
    gameCount = gameCount,
    colorTheme = colorTheme,
    coverAspectRatio = coverAspectRatio,
    defaultCore = defaultCore,
    iconUrl = iconUrl,
    logoUrl = logoPngUrl.ifEmpty { logoUrl },
    saveStateSupport = saveStateSupport,
    browserPlayable = browserPlayable,
    playable = playable,
    generation = generation,
    code = code,
    makerName = maker?.name,
    makerCode = maker?.code,
    mediaTypeName = mediaType?.name,
    releaseYear = releaseYear,
    unitsSold = unitsSold,
    summary = summary,
)
```

- [ ] **Step 4: Remove hardcoded ConsoleInfo from ConsoleMetadata.kt**

In `player/shared/.../feature/library/ConsoleMetadata.kt`, remove the `ConsoleInfo` data class and `getConsoleInfo()` function. Keep `getConsoleGradient()` and `getConsoleColor()` — those are UI-specific and don't come from the API.

- [ ] **Step 5: Update ConsoleComponents.kt to use API data**

In `player/shared/.../feature/library/ConsoleComponents.kt`, update the console card info row. Find where `getConsoleInfo(console.abbreviation)` is called and replace with API data:

Change:
```kotlin
val consoleInfo = getConsoleInfo(console.abbreviation)
```

To use the Console model's new fields directly:
```kotlin
// In the info row:
val info = buildString {
    append("${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}")
    if (console.makerName != null) {
        append(" · ${console.makerName}")
    }
    if (console.releaseYear != null) {
        append(" · ${console.releaseYear}")
    }
}
```

- [ ] **Step 6: Build player app to verify**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add player/shared/src/
git commit -m "feat: use API console metadata in player app, remove hardcoded ConsoleInfo"
```

---

### Task 7: Update existing console handler tests

**Files:**
- Modify: `server/internal/api/console_handler_test.go`

- [ ] **Step 1: Update test setup to include new model migrations**

In `server/internal/api/console_handler_test.go`, update the `setupConsoleTestEnv` function to include new models in AutoMigrate:

```go
err = database.AutoMigrate(
    &db.MediaTypeCategory{},
    &db.MediaType{},
    &db.HardwareMaker{},
    &db.Console{},
    // ... existing models
)
```

And add seed calls:

```go
require.NoError(t, db.SeedMediaTypeCategories(database))
require.NoError(t, db.SeedMediaTypes(database))
require.NoError(t, db.SeedHardwareMakers(database))
// existing SeedConsoles call
require.NoError(t, db.SeedConsoleMetadata(database))
```

- [ ] **Step 2: Add test for console response including metadata**

Add a new test to verify metadata is present in console responses:

```go
func TestListConsoles_IncludesMetadata(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	// Create a game so the console appears in the list
	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	database.Create(&db.Game{
		Title:       "Test Game",
		ConsoleID:   nes.ID,
		IsPrimary:   true,
		IsPreRelease: false,
	})

	handler := &ConsoleHandler{DB: database, Storage: store}
	router.GET("/api/consoles", handler.ListConsoles)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	err := json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)
	require.Len(t, consoles, 1)

	nesResp := consoles[0]
	assert.Equal(t, "nes", nesResp.Code)
	assert.NotNil(t, nesResp.Maker)
	assert.Equal(t, "nintendo", nesResp.Maker.Code)
	assert.Equal(t, "Nintendo", nesResp.Maker.Name)
	assert.NotNil(t, nesResp.MediaType)
	assert.Equal(t, "cartridge", nesResp.MediaType.Code)
	assert.Equal(t, "cartridge", nesResp.MediaType.Category.Code)
	assert.NotNil(t, nesResp.ReleaseYear)
	assert.Equal(t, 1983, *nesResp.ReleaseYear)
	assert.NotNil(t, nesResp.UnitsSold)
	assert.NotNil(t, nesResp.Summary)
}
```

- [ ] **Step 3: Run all server tests**

Run: `cd server && go test ./... -v -count=1`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add server/internal/api/console_handler_test.go
git commit -m "test: update console handler tests for metadata and add metadata assertion test"
```
