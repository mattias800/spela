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

// consoleMetadata holds the metadata to apply to an existing console.
type consoleMetadata struct {
	Abbreviation string
	Code         string
	MakerCode    string
	MediaCode    string
	ReleaseYear  *int
	UnitsSold    *int64
	Summary      *string
}

// SeedConsoleMetadata updates existing consoles with metadata (code, maker, media type, etc.).
func SeedConsoleMetadata(db *gorm.DB) error {
	metadata := []consoleMetadata{
		// 2nd Generation
		{Abbreviation: "A26", Code: "a26", MakerCode: "atari", MediaCode: "cartridge", ReleaseYear: intPtr(1977), UnitsSold: int64Ptr(30000000), Summary: strPtr("The Atari 2600 popularized microprocessor-based home gaming and became the dominant console of the late 1970s and early 1980s. Its massive library of over 500 games helped establish video gaming as a mainstream entertainment medium, despite the infamous 1983 market crash.")},
		{Abbreviation: "A52", Code: "a52", MakerCode: "atari", MediaCode: "cartridge", ReleaseYear: intPtr(1982), UnitsSold: int64Ptr(1000000), Summary: strPtr("The Atari 5200 was Atari's successor to the 2600, featuring improved graphics and analog joystick controllers. Despite its technical improvements, it struggled commercially due to controller reliability issues and competition from the ColecoVision.")},
		{Abbreviation: "CV", Code: "colecovision", MakerCode: "coleco", MediaCode: "cartridge", ReleaseYear: intPtr(1982), UnitsSold: int64Ptr(2000000), Summary: strPtr("The ColecoVision offered near-arcade-quality graphics and was bundled with Donkey Kong, making it a strong competitor in the early 1980s. Its expansion module allowed backward compatibility with Atari 2600 games, but the 1983 video game crash cut its lifespan short.")},

		// 3rd Generation
		{Abbreviation: "NES", Code: "nes", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1983), UnitsSold: int64Ptr(61910000), Summary: strPtr("The Nintendo Entertainment System revived the video game industry after the 1983 crash and established Nintendo as a dominant force in gaming. With iconic franchises like Super Mario Bros., The Legend of Zelda, and Metroid, it defined the template for home console gaming for decades to come.")},
		{Abbreviation: "SMS", Code: "sms", MakerCode: "sega", MediaCode: "cartridge", ReleaseYear: intPtr(1985), UnitsSold: int64Ptr(13000000), Summary: strPtr("The Sega Master System was Sega's first major home console and a strong competitor to the NES in Europe and Brazil. While it was technically superior to the NES, Nintendo's exclusive licensing agreements limited its game library and market share in North America and Japan.")},
		{Abbreviation: "A78", Code: "a78", MakerCode: "atari", MediaCode: "cartridge", ReleaseYear: intPtr(1986), UnitsSold: int64Ptr(3770000), Summary: strPtr("The Atari 7800 was designed to be backward compatible with the Atari 2600 and featured improved graphics rivaling the NES. Delayed by two years due to Atari's sale to Jack Tramiel, it arrived too late to challenge Nintendo's dominance despite its competitive hardware.")},

		// 4th Generation
		{Abbreviation: "PCE", Code: "pce", MakerCode: "nec", MediaCode: "hucard", ReleaseYear: intPtr(1987), UnitsSold: int64Ptr(10000000), Summary: strPtr("The TurboGrafx-16, developed jointly by NEC and Hudson Soft, was the first console of the 16-bit era. It used compact HuCard cartridges and was hugely popular in Japan, though it struggled against the Genesis and SNES in Western markets.")},
		{Abbreviation: "GEN", Code: "genesis", MakerCode: "sega", MediaCode: "cartridge", ReleaseYear: intPtr(1988), UnitsSold: int64Ptr(30750000), Summary: strPtr("The Sega Genesis, known as the Mega Drive outside North America, was Sega's most successful console and a fierce rival to the SNES. Its aggressive marketing campaign and Sonic the Hedgehog made it the first serious challenger to Nintendo's dominance in the home console market.")},
		{Abbreviation: "PCECD", Code: "pcecd", MakerCode: "nec", MediaCode: "cd-rom", ReleaseYear: intPtr(1988), UnitsSold: nil, Summary: strPtr("The TurboGrafx-CD was a CD-ROM add-on for the TurboGrafx-16, making it one of the first CD-based gaming platforms. It enabled larger games with CD-quality audio and full-motion video, pioneering the shift from cartridges to optical media in console gaming.")},
		{Abbreviation: "GB", Code: "gb", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1989), UnitsSold: int64Ptr(118690000), Summary: strPtr("The Game Boy launched portable gaming into the mainstream with its compact design and exceptional battery life. Bundled with Tetris at launch, it dominated the handheld market for over a decade despite its monochrome green-tinted screen.")},
		{Abbreviation: "LYNX", Code: "lynx", MakerCode: "atari", MediaCode: "cartridge", ReleaseYear: intPtr(1989), UnitsSold: int64Ptr(3000000), Summary: strPtr("The Atari Lynx was the first handheld console with a color LCD screen and hardware sprite scaling. Despite its technical superiority over the Game Boy, its large size, high price, and short battery life limited its commercial success.")},
		{Abbreviation: "SNES", Code: "snes", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(49100000), Summary: strPtr("The Super Nintendo Entertainment System delivered a generational leap with Mode 7 graphics, advanced sound capabilities, and an extraordinary game library. Titles like Super Mario World, A Link to the Past, and Chrono Trigger are widely regarded as some of the greatest games ever made.")},
		{Abbreviation: "GG", Code: "gamegear", MakerCode: "sega", MediaCode: "cartridge", ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(10620000), Summary: strPtr("The Sega Game Gear was a portable console with a full-color backlit screen, positioned as a direct competitor to the Game Boy. Essentially a portable Master System, it offered superior graphics but suffered from short battery life and a higher price point.")},
		{Abbreviation: "NEOGEO", Code: "neogeo", MakerCode: "snk", MediaCode: "cartridge", ReleaseYear: intPtr(1990), UnitsSold: int64Ptr(1000000), Summary: strPtr("The Neo Geo AES brought arcade-perfect gaming to the home by using identical hardware to SNK's arcade cabinets. Its premium price tag and expensive cartridges made it a luxury item, but its fighting game library featuring The King of Fighters and Fatal Fury was unmatched.")},
		{Abbreviation: "SCD", Code: "segacd", MakerCode: "sega", MediaCode: "cd-rom", ReleaseYear: intPtr(1991), UnitsSold: int64Ptr(2240000), Summary: strPtr("The Sega CD was a CD-ROM add-on for the Genesis that enabled full-motion video, CD-quality audio, and larger game worlds. While it produced standout titles like Sonic CD and Lunar, many of its games relied heavily on grainy FMV gimmicks.")},
		{Abbreviation: "CDI", Code: "cdi", MakerCode: "philips", MediaCode: "cd-rom", ReleaseYear: intPtr(1991), UnitsSold: int64Ptr(570000), Summary: strPtr("The Philips CD-i was an interactive multimedia player that also functioned as a game console. Despite its ambitious vision as an all-in-one entertainment device, it is best remembered for its poorly received Zelda and Mario games licensed from Nintendo.")},

		// 5th Generation
		{Abbreviation: "3DO", Code: "3do", MakerCode: "panasonic", MediaCode: "cd-rom", ReleaseYear: intPtr(1993), UnitsSold: int64Ptr(2000000), Summary: strPtr("The 3DO Interactive Multiplayer was designed by EA founder Trip Hawkins and manufactured by Panasonic, LG, and Sanyo under an open licensing model. Its high launch price of $699 limited adoption despite impressive hardware and notable titles like Road Rash and Return Fire.")},
		{Abbreviation: "JAG", Code: "jaguar", MakerCode: "atari", MediaCode: "cartridge", ReleaseYear: intPtr(1993), UnitsSold: int64Ptr(250000), Summary: strPtr("The Atari Jaguar was marketed as the first 64-bit console, though its complex multi-chip architecture made it difficult to develop for. It was Atari's final home console, and despite titles like Alien vs Predator and Tempest 2000, its small library led to commercial failure.")},
		{Abbreviation: "ACD32", Code: "acd32", MakerCode: "commodore", MediaCode: "cd-rom", ReleaseYear: intPtr(1993), UnitsSold: nil, Summary: strPtr("The Amiga CD32 was the world's first 32-bit CD-ROM-based home console, built on the Amiga 1200 architecture. Commodore's bankruptcy in 1994 cut short its promising start, particularly in Europe where it had gained meaningful traction in its brief time on the market.")},
		{Abbreviation: "PSX", Code: "psx", MakerCode: "sony", MediaCode: "cd-rom", ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(102490000), Summary: strPtr("The PlayStation was Sony's debut in the console market and became one of the best-selling consoles of all time. Its CD-ROM format attracted third-party developers with lower manufacturing costs, spawning landmark franchises like Final Fantasy VII, Metal Gear Solid, and Crash Bandicoot.")},
		{Abbreviation: "SAT", Code: "saturn", MakerCode: "sega", MediaCode: "cd-rom", ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(9260000), Summary: strPtr("The Sega Saturn featured powerful 2D capabilities and a dual-CPU architecture that was notoriously difficult to program for. While it thrived in Japan with titles like Nights into Dreams and Virtua Fighter 2, its surprise early launch in North America damaged retailer relationships and developer support.")},
		{Abbreviation: "NEOCD", Code: "neocd", MakerCode: "snk", MediaCode: "cd-rom", ReleaseYear: intPtr(1994), UnitsSold: nil, Summary: strPtr("The Neo Geo CD was SNK's more affordable alternative to the expensive AES cartridge system, using CD-ROM media to drastically reduce game prices. However, its single-speed CD drive resulted in extremely long loading times that frustrated players despite access to the same excellent arcade library.")},
		{Abbreviation: "32X", Code: "32x", MakerCode: "sega", MediaCode: "cartridge", ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(665000), Summary: strPtr("The Sega 32X was an add-on for the Genesis designed to extend its lifespan with 32-bit capabilities. Released just months before the Saturn, it confused consumers and developers alike, becoming one of the most notable commercial failures in console hardware history.")},
		{Abbreviation: "VB", Code: "virtualboy", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1995), UnitsSold: int64Ptr(770000), Summary: strPtr("The Virtual Boy was Nintendo's experimental tabletop console that displayed stereoscopic 3D graphics in red and black. Designed by Game Boy creator Gunpei Yokoi, it was discontinued after less than a year due to poor sales caused by its uncomfortable design and monochromatic display.")},
		{Abbreviation: "N64", Code: "n64", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1996), UnitsSold: int64Ptr(32930000), Summary: strPtr("The Nintendo 64 pioneered 3D console gaming with revolutionary titles like Super Mario 64, The Legend of Zelda: Ocarina of Time, and GoldenEye 007. Its decision to use cartridges over CD-ROMs limited third-party support but enabled faster load times and introduced the analog stick to mainstream gaming.")},
		{Abbreviation: "GBC", Code: "gbc", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(49020000), Summary: strPtr("The Game Boy Color added a color screen to the Game Boy line while maintaining backward compatibility with the original library. It extended the Game Boy platform's dominance with titles like Pokemon Gold and Silver, The Legend of Zelda: Oracle games, and Wario Land 3.")},
		{Abbreviation: "NGP", Code: "ngp", MakerCode: "snk", MediaCode: "cartridge", ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(2000000), Summary: strPtr("The Neo Geo Pocket, and its color successor the Neo Geo Pocket Color, was SNK's entry into the handheld market. Known for its excellent micro-switch joystick and strong fighting game library including SNK vs. Capcom: Match of the Millennium, it could not overcome the Game Boy Color's market dominance.")},
		{Abbreviation: "DC", Code: "dreamcast", MakerCode: "sega", MediaCode: "gd-rom", ReleaseYear: intPtr(1998), UnitsSold: int64Ptr(9130000), Summary: strPtr("The Dreamcast was Sega's final home console and the first of the sixth generation, featuring built-in modem support for online gaming. Despite critical acclaim and innovative titles like Shenmue, Jet Set Radio, and Sonic Adventure, it was unable to compete against the upcoming PlayStation 2 and was discontinued in 2001.")},
		{Abbreviation: "WS", Code: "wonderswan", MakerCode: "bandai", MediaCode: "cartridge", ReleaseYear: intPtr(1999), UnitsSold: int64Ptr(3500000), Summary: strPtr("The WonderSwan was designed by Gunpei Yokoi after leaving Nintendo and was sold exclusively in Japan. Its low price and excellent battery life attracted Square for exclusive Final Fantasy remakes, but the Game Boy Advance's arrival halted its momentum.")},
		{Abbreviation: "PCFX", Code: "pcfx", MakerCode: "nec", MediaCode: "cd-rom", ReleaseYear: intPtr(1994), UnitsSold: int64Ptr(400000), Summary: strPtr("The PC-FX was NEC's successor to the TurboGrafx-16, focusing on full-motion video and 2D graphics rather than competing in the 3D polygon race. Sold only in Japan, it failed to gain traction against the PlayStation and Saturn due to its lack of 3D capabilities and limited game library.")},

		// 6th Generation
		{Abbreviation: "PS2", Code: "ps2", MakerCode: "sony", MediaCode: "dvd-rom", ReleaseYear: intPtr(2000), UnitsSold: int64Ptr(155000000), Summary: strPtr("The PlayStation 2 is the best-selling video game console of all time, with a library of over 4,000 games spanning every genre. Its built-in DVD player, backward compatibility with PS1 games, and landmark titles like Grand Theft Auto: San Andreas, Shadow of the Colossus, and Final Fantasy X made it a cultural phenomenon.")},
		{Abbreviation: "GBA", Code: "gba", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(81510000), Summary: strPtr("The Game Boy Advance brought 32-bit gaming to Nintendo's handheld line with graphics comparable to the SNES. Its impressive library included Pokemon Ruby and Sapphire, The Legend of Zelda: The Minish Cap, and Metroid Fusion, cementing Nintendo's dominance in portable gaming.")},
		{Abbreviation: "GC", Code: "gamecube", MakerCode: "nintendo", MediaCode: "dvd-rom", ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(21740000), Summary: strPtr("The Nintendo GameCube used proprietary mini-DVDs and featured a distinctive compact design with a carrying handle. While it sold fewer units than the PS2 and Xbox, it hosted beloved exclusives like Super Smash Bros. Melee, Metroid Prime, and The Legend of Zelda: The Wind Waker.")},
		{Abbreviation: "XBOX", Code: "xbox", MakerCode: "microsoft", MediaCode: "dvd-rom", ReleaseYear: intPtr(2001), UnitsSold: int64Ptr(24000000), Summary: strPtr("The Xbox was Microsoft's first entry into the console market, featuring PC-derived hardware with a built-in hard drive and Ethernet port. Halo: Combat Evolved became its killer app and system seller, while Xbox Live pioneered unified online console gaming.")},
		{Abbreviation: "PKMN", Code: "pokemonmini", MakerCode: "nintendo", MediaCode: "cartridge", ReleaseYear: intPtr(2001), UnitsSold: nil, Summary: strPtr("The Pokemon Mini is the smallest cartridge-based game console ever produced by Nintendo. Designed as a novelty device tied to the Pokemon franchise, it featured a small monochrome screen, a rumble motor, and an infrared port for multiplayer, with a library of just ten games.")},

		// 7th Generation
		{Abbreviation: "NDS", Code: "nds", MakerCode: "nintendo", MediaCode: "game-card", ReleaseYear: intPtr(2004), UnitsSold: int64Ptr(154020000), Summary: strPtr("The Nintendo DS introduced dual screens and touch input to handheld gaming, creating entirely new gameplay possibilities. As the second best-selling console ever, it reached massive audiences with titles like Brain Age, Nintendogs, New Super Mario Bros., and Pokemon Diamond and Pearl.")},
		{Abbreviation: "PSP", Code: "psp", MakerCode: "sony", MediaCode: "umd", ReleaseYear: intPtr(2004), UnitsSold: int64Ptr(80000000), Summary: strPtr("The PlayStation Portable was Sony's first handheld console, featuring a widescreen display and near-PS2 quality graphics. Its UMD optical disc format enabled console-quality games like God of War: Chains of Olympus, Monster Hunter Freedom Unite, and Crisis Core: Final Fantasy VII.")},
		{Abbreviation: "X360", Code: "x360", MakerCode: "microsoft", MediaCode: "dvd-rom", ReleaseYear: intPtr(2005), UnitsSold: int64Ptr(84700000), Summary: strPtr("The Xbox 360 launched a year ahead of its competitors and established Xbox Live as the premier online gaming service. Its achievement system, party chat, and blockbuster exclusives like Gears of War and Halo 3 defined the era of connected console gaming.")},
		{Abbreviation: "WII", Code: "wii", MakerCode: "nintendo", MediaCode: "dvd-rom", ReleaseYear: intPtr(2006), UnitsSold: int64Ptr(101630000), Summary: strPtr("The Wii revolutionized gaming with its motion-sensing Wii Remote controller, attracting millions of non-traditional gamers. Wii Sports became a cultural phenomenon, and the console outsold both the PS3 and Xbox 360, proving that innovative gameplay could triumph over raw processing power.")},
		{Abbreviation: "PS3", Code: "ps3", MakerCode: "sony", MediaCode: "blu-ray", ReleaseYear: intPtr(2006), UnitsSold: int64Ptr(87400000), Summary: strPtr("The PlayStation 3 was the first console with a built-in Blu-ray player and featured the powerful Cell processor co-developed with IBM. Despite a rocky launch at a premium price, it recovered with critically acclaimed exclusives like The Last of Us, Uncharted 2, and free online multiplayer.")},

		// 8th Generation
		{Abbreviation: "3DS", Code: "3ds", MakerCode: "nintendo", MediaCode: "game-card", ReleaseYear: intPtr(2011), UnitsSold: int64Ptr(75940000), Summary: strPtr("The Nintendo 3DS delivered glasses-free stereoscopic 3D visuals and featured backward compatibility with DS games. Its StreetPass social features and strong library including Pokemon X/Y, Fire Emblem Awakening, and Animal Crossing: New Leaf made it Nintendo's most successful dedicated handheld after the original DS family.")},
		{Abbreviation: "WIIU", Code: "wiiu", MakerCode: "nintendo", MediaCode: "disc", ReleaseYear: intPtr(2012), UnitsSold: int64Ptr(13560000), Summary: strPtr("The Wii U featured an innovative GamePad controller with a built-in touchscreen for asymmetric gameplay and off-TV play. Despite underperforming commercially due to confused marketing and slow software output, it produced excellent first-party titles like Super Mario 3D World, Splatoon, and Super Smash Bros. for Wii U.")},
		{Abbreviation: "PS4", Code: "ps4", MakerCode: "sony", MediaCode: "blu-ray", ReleaseYear: intPtr(2013), UnitsSold: int64Ptr(117200000), Summary: strPtr("The PlayStation 4 focused on being a developer-friendly gaming powerhouse with a standard x86 architecture. Its massive install base and acclaimed exclusives like God of War, Horizon Zero Dawn, and Marvel's Spider-Man made it the dominant console of the eighth generation.")},
		{Abbreviation: "XONE", Code: "xone", MakerCode: "microsoft", MediaCode: "blu-ray", ReleaseYear: intPtr(2013), UnitsSold: int64Ptr(51000000), Summary: strPtr("The Xbox One initially focused on being an all-in-one entertainment hub before pivoting back to games. Microsoft introduced backward compatibility and the Xbox Game Pass subscription service, which became the most significant shift in console business models since digital distribution.")},

		// 9th Generation
		{Abbreviation: "NSW", Code: "nsw", MakerCode: "nintendo", MediaCode: "game-card", ReleaseYear: intPtr(2017), UnitsSold: int64Ptr(143420000), Summary: strPtr("The Nintendo Switch combined home console and portable gaming into a single hybrid device with detachable Joy-Con controllers. Its versatile design and critically acclaimed games like The Legend of Zelda: Breath of the Wild, Animal Crossing: New Horizons, and Mario Kart 8 Deluxe made it one of the best-selling consoles in history.")},
		{Abbreviation: "XSX", Code: "xsx", MakerCode: "microsoft", MediaCode: "digital", ReleaseYear: intPtr(2020), UnitsSold: int64Ptr(21000000), Summary: strPtr("The Xbox Series X/S continued Microsoft's shift toward a service-based model, with Game Pass at its core. The Series X offered the most powerful console hardware at launch, while the disc-less Series S provided an affordable entry point for next-gen gaming.")},
		{Abbreviation: "PS5", Code: "ps5", MakerCode: "sony", MediaCode: "blu-ray", ReleaseYear: intPtr(2020), UnitsSold: int64Ptr(67000000), Summary: strPtr("The PlayStation 5 introduced an ultra-fast custom SSD that virtually eliminated loading times and featured the innovative DualSense controller with haptic feedback and adaptive triggers. Exclusives like Demon's Souls, Ratchet & Clank: Rift Apart, and Astro Bot showcase its next-generation capabilities.")},

		// Home Computers (generation = 100)
		{Abbreviation: "C64", Code: "c64", MakerCode: "commodore", MediaCode: "floppy-disk", ReleaseYear: intPtr(1982), UnitsSold: int64Ptr(17000000), Summary: strPtr("The Commodore 64 is the best-selling single personal computer model of all time, renowned for its advanced sound chip (SID) and capable graphics. Its affordable price and massive software library of over 10,000 titles made it a cornerstone of 1980s home computing and gaming culture.")},
		{Abbreviation: "C128", Code: "c128", MakerCode: "commodore", MediaCode: "floppy-disk", ReleaseYear: intPtr(1985), UnitsSold: int64Ptr(4000000), Summary: strPtr("The Commodore 128 was the successor to the C64, featuring three operating modes including full C64 backward compatibility and a CP/M mode. While technically superior to its predecessor, most users simply ran it in C64 mode, and it never achieved the same cultural impact.")},
		{Abbreviation: "AMIGA", Code: "amiga", MakerCode: "commodore", MediaCode: "floppy-disk", ReleaseYear: intPtr(1985), UnitsSold: nil, Summary: strPtr("The Commodore Amiga was a revolutionary multimedia computer with custom chips for graphics, sound, and I/O that were years ahead of competing platforms. Its pre-emptive multitasking operating system and powerful capabilities made it the platform of choice for video production, music, and gaming in Europe throughout the late 1980s and early 1990s.")},
		{Abbreviation: "DOS", Code: "dos", MakerCode: "ibm", MediaCode: "floppy-disk", ReleaseYear: intPtr(1981), UnitsSold: nil, Summary: strPtr("DOS, the Disk Operating System for IBM PC compatibles, became the dominant personal computing platform through the 1980s and early 1990s. Its open architecture spawned the PC gaming industry, with classic titles like Doom, Commander Keen, and Civilization defining entire genres.")},
		{Abbreviation: "MSX1", Code: "msx1", MakerCode: "microsoft", MediaCode: "cartridge", ReleaseYear: intPtr(1983), UnitsSold: nil, Summary: strPtr("MSX was a standardized home computer architecture initiated by Microsoft and ASCII Corporation, adopted by manufacturers across Japan, South Korea, and Europe. It became a major gaming platform in Japan, launching the Metal Gear and Bomberman franchises.")},
		{Abbreviation: "MSX2", Code: "msx2", MakerCode: "microsoft", MediaCode: "cartridge", ReleaseYear: intPtr(1985), UnitsSold: nil, Summary: strPtr("MSX2 was the second generation of the MSX standard, offering improved graphics with more colors and hardware scrolling. It saw continued success in Japan and the Netherlands, with notable titles including Metal Gear 2: Solid Snake and Vampire Killer.")},
		{Abbreviation: "PET", Code: "pet", MakerCode: "commodore", MediaCode: "cartridge", ReleaseYear: intPtr(1977), UnitsSold: nil, Summary: strPtr("The Commodore PET was one of the first mass-produced personal computers, featuring an all-in-one design with built-in monitor and cassette drive. Primarily used in education and business, it established Commodore as a major force in the personal computer industry.")},
		{Abbreviation: "PLUS4", Code: "plus4", MakerCode: "commodore", MediaCode: "cartridge", ReleaseYear: intPtr(1984), UnitsSold: nil, Summary: strPtr("The Commodore Plus/4 was positioned as a business-oriented home computer with built-in productivity software. Despite improved BASIC and graphics capabilities over the C64, its incompatibility with the C64's software library and lack of hardware sprites limited its appeal to gamers and hobbyists.")},
		{Abbreviation: "VIC20", Code: "vic20", MakerCode: "commodore", MediaCode: "cartridge", ReleaseYear: intPtr(1980), UnitsSold: int64Ptr(2500000), Summary: strPtr("The Commodore VIC-20 was one of the first computers to sell over a million units, priced affordably at under $300. While limited to 5KB of RAM, it introduced an entire generation to home computing and programming, serving as a stepping stone to the legendary Commodore 64.")},

		// Arcade (generation = 101)
		{Abbreviation: "ARCADE", Code: "arcade", MakerCode: "", MediaCode: "arcade-board", ReleaseYear: intPtr(1971), UnitsSold: nil, Summary: strPtr("Arcade games have been a cornerstone of the video game industry since Computer Space in 1971 and Pong in 1972. From the golden age of Space Invaders and Pac-Man to modern fighting and rhythm games, arcades pioneered nearly every major gaming genre and remain a vibrant part of gaming culture worldwide.")},

		// Demo scenes (generation = 100)
		{Abbreviation: "ADEMO", Code: "ademo", MakerCode: "commodore", MediaCode: "floppy-disk", ReleaseYear: nil, UnitsSold: nil, Summary: strPtr("The Amiga demo scene was one of the most vibrant creative computing communities, pushing the Amiga's custom hardware to produce stunning audiovisual demonstrations. Originating in the late 1980s, it became a breeding ground for future game developers, digital artists, and musicians.")},
		{Abbreviation: "DDEMO", Code: "ddemo", MakerCode: "", MediaCode: "digital", ReleaseYear: nil, UnitsSold: nil, Summary: strPtr("The DOS demo scene produced creative real-time audiovisual programs that showcased programming skill and artistic expression on IBM PC compatibles. Demos like Second Reality by Future Crew became legendary, and the scene continues to thrive at events like Assembly and Revision.")},

		// ScummVM (generation = 100)
		{Abbreviation: "SCUMMVM", Code: "scummvm", MakerCode: "", MediaCode: "digital", ReleaseYear: nil, UnitsSold: nil, Summary: strPtr("ScummVM is a collection of game engine reimplementations that allows classic point-and-click adventure games to run on modern hardware. Originally created to run LucasArts SCUMM games like Monkey Island and Day of the Tentacle, it now supports hundreds of adventure games from numerous publishers.")},
	}

	for _, m := range metadata {
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

		if m.ReleaseYear != nil && (console.ReleaseYear == nil || *console.ReleaseYear != *m.ReleaseYear) {
			updates["release_year"] = *m.ReleaseYear
		}

		if m.UnitsSold != nil && (console.UnitsSold == nil || *console.UnitsSold != *m.UnitsSold) {
			updates["units_sold"] = *m.UnitsSold
		}

		if m.Summary != nil && (console.Summary == nil || *console.Summary != *m.Summary) {
			updates["summary"] = *m.Summary
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
