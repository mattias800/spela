package api

// launchGamesByConsole maps a lowercase console abbreviation to the list of
// titles that shipped at (or very near) that console's launch. Titles are
// stored in their commonly-known English form; matching against the `games`
// table is case- and punctuation-insensitive (see matchLaunchGames).
//
// Sourced from Wikipedia's "List of launch games for the ..." pages. We
// prefer the North American launch lineup when a console shipped in multiple
// regions with different launch libraries, with two exceptions:
//   - Consoles that never shipped in NA (Famicom Disk System, PC-FX, etc.)
//     use the Japanese launch.
//   - PAL-only launches (e.g. Amiga CD32) use the European launch.
//
// The list is intentionally curated rather than derived from release-date
// data; IGDB's per-region release metadata is inconsistent for older and
// smaller consoles, and "launch title" is an editorial judgement that
// Wikipedia already handles well (pre-orders, launch-window bundles,
// kiosk demos). A console not in this map renders no section rather than
// an empty one.
var launchGamesByConsole = map[string][]string{
	// Nintendo Entertainment System — North America, 1985-10-18.
	// The "Black Box" launch lineup. Donkey Kong Jr. Math shipped at
	// launch in the NA test market; included for completeness.
	"nes": {
		"Super Mario Bros.",
		"Duck Hunt",
		"Hogan's Alley",
		"10-Yard Fight",
		"Baseball",
		"Clu Clu Land",
		"Excitebike",
		"Golf",
		"Gumshoe",
		"Gyromite",
		"Ice Climber",
		"Kung Fu",
		"Mach Rider",
		"Pinball",
		"Soccer",
		"Stack-Up",
		"Tennis",
		"Wild Gunman",
		"Wrecking Crew",
		"Donkey Kong Jr. Math",
		"Popeye",
		"Urban Champion",
	},

	// Super Nintendo Entertainment System — North America, 1991-08-23.
	"snes": {
		"Super Mario World",
		"F-Zero",
		"Pilotwings",
		"Gradius III",
		"SimCity",
		"Populous",
		"Super R-Type",
		"U.N. Squadron",
		"ActRaiser",
		"Final Fight",
		"Drakkhen",
		"The Chessmaster",
		"HAL's Hole in One Golf",
		"D-Force",
		"Big Run",
		"Lagoon",
	},

	// Sega Genesis / Mega Drive — North America, 1989-08-14.
	"gen": {
		"Altered Beast",
		"Space Harrier II",
		"Super Thunder Blade",
		"Tommy Lasorda Baseball",
		"Thunder Force II",
		"Mystic Defender",
		"Ghouls'n Ghosts",
		"Revenge of Shinobi",
		"Golden Axe",
		"Last Battle",
		"Truxton",
		"Forgotten Worlds",
		"Pat Riley's Basketball",
		"Rambo III",
	},

	// Sega Master System — North America, 1986-06.
	"sms": {
		"Hang-On",
		"Safari Hunt",
		"Astro Warrior",
		"Black Belt",
		"Choplifter",
		"Great Baseball",
		"Great Soccer",
		"My Hero",
		"Pro Wrestling",
		"Teddy Boy",
		"Transbot",
		"World Grand Prix",
	},

	// Nintendo 64 — North America, 1996-09-29.
	"n64": {
		"Super Mario 64",
		"Pilotwings 64",
	},

	// Nintendo GameCube — North America, 2001-11-18.
	"gc": {
		"Luigi's Mansion",
		"Star Wars Rogue Squadron II: Rogue Leader",
		"Super Monkey Ball",
		"Wave Race: Blue Storm",
		"Madden NFL 2002",
		"Crazy Taxi",
		"Tony Hawk's Pro Skater 3",
		"Dave Mirra Freestyle BMX 2",
		"All-Star Baseball 2002",
		"NHL Hitz 20-02",
		"Batman: Vengeance",
		"Disney's Tarzan Untamed",
		"Top Gun: Combat Zones",
		"Oni",
		"Extreme-G 3",
	},

	// Game Boy — worldwide, 1989 (Japan 1989-04-21, NA 1989-07-31).
	"gb": {
		"Tetris",
		"Super Mario Land",
		"Alleyway",
		"Baseball",
		"Yakuman",
		"Tennis",
	},

	// Game Boy Color — North America, 1998-11-19.
	"gbc": {
		"Tetris DX",
		"Game Boy Gallery 2",
		"Wario Land II",
		"Centipede",
		"Pocket Bomberman",
	},

	// Game Boy Advance — North America, 2001-06-11.
	"gba": {
		"Super Mario Advance",
		"F-Zero: Maximum Velocity",
		"Castlevania: Circle of the Moon",
		"Tony Hawk's Pro Skater 2",
		"Rayman Advance",
		"Pitfall: The Mayan Adventure",
		"GT Advance Championship Racing",
		"Iridion 3D",
		"Konami Krazy Racers",
		"Ready 2 Rumble Boxing: Round 2",
		"Fire Pro Wrestling",
		"Earthworm Jim",
		"Pinobee: Wings of Adventure",
	},

	// PlayStation — North America, 1995-09-09.
	"psx": {
		"Ridge Racer",
		"Battle Arena Toshinden",
		"Street Fighter: The Movie",
		"ESPN Extreme Games",
		"Rayman",
		"Twisted Metal",
		"Warhawk",
		"NBA Jam Tournament Edition",
		"Kileak: The DNA Imperative",
		"Raiden Project",
		"Power Serve 3D Tennis",
		"Total Eclipse Turbo",
	},

	// Sega Saturn — North America, 1995-05-11.
	"sat": {
		"Virtua Fighter",
		"Daytona USA",
		"Panzer Dragoon",
		"Clockwork Knight",
		"Worldwide Soccer: Sega International Victory Goal",
		"Bug!",
		"Pebble Beach Golf Links",
	},

	// Sega Dreamcast — North America, 1999-09-09.
	"dc": {
		"Sonic Adventure",
		"Soul Calibur",
		"Ready 2 Rumble Boxing",
		"NFL 2K",
		"NFL Blitz 2000",
		"Blue Stinger",
		"The House of the Dead 2",
		"TrickStyle",
		"Power Stone",
		"Hydro Thunder",
		"Mortal Kombat Gold",
		"Monaco Grand Prix",
		"Tokyo Xtreme Racer",
		"TNN Motorsports Hardcore Heat",
		"Pen Pen TriIcelon",
		"Flag to Flag",
		"Air Force Delta",
	},

	// PlayStation 2 — North America, 2000-10-26.
	"ps2": {
		"Tekken Tag Tournament",
		"Ridge Racer V",
		"SSX",
		"TimeSplitters",
		"Smuggler's Run",
		"Madden NFL 2001",
		"Armored Core 2",
		"Dynasty Warriors 2",
		"Kessen",
		"FantaVision",
		"Gungriffon Blaze",
		"Midnight Club: Street Racing",
		"NHL 2001",
		"Silent Scope",
		"Street Fighter EX3",
		"Summoner",
		"Swing Away Golf",
		"Unreal Tournament",
		"Wild Wild Racing",
		"X Squad",
		"Eternal Ring",
		"Evergrace",
		"Orphen: Scion of Sorcery",
		"ESPN X-Games Snowboarding",
	},

	// PlayStation Portable — North America, 2005-03-24.
	"psp": {
		"Twisted Metal: Head-On",
		"Wipeout Pure",
		"Lumines",
		"Metal Gear Acid",
		"Ridge Racer",
		"Spider-Man 2",
		"NFL Street 2: Unleashed",
		"Tony Hawk's Underground 2 Remix",
		"World Tour Soccer",
		"Need for Speed: Underground Rivals",
		"Ape Escape: On the Loose",
		"Darkstalkers Chronicle: The Chaos Tower",
		"Dynasty Warriors",
		"Mercury",
		"Untold Legends: Brotherhood of the Blade",
		"NBA Street Showdown",
		"MLB",
		"Rengoku: The Tower of Purgatory",
	},

	// Nintendo DS — North America, 2004-11-21.
	"nds": {
		"Super Mario 64 DS",
		"Feel the Magic: XY/XX",
		"Madden NFL 2005",
		"Mr. Driller: Drill Spirits",
		"Asphalt Urban GT",
		"Ridge Racer DS",
		"Spider-Man 2",
		"The Urbz: Sims in the City",
		"Tiger Woods PGA Tour",
		"Ping Pals",
		"Zoo Keeper",
		"Sprung",
	},

	// Nintendo 3DS — North America, 2011-03-27.
	"3ds": {
		"Super Street Fighter IV 3D Edition",
		"Pilotwings Resort",
		"Nintendogs + Cats",
		"Rayman 3D",
		"The Sims 3",
		"LEGO Star Wars III: The Clone Wars",
		"Asphalt 3D",
		"Madden NFL Football",
		"Bust-A-Move Universe",
		"Combat of Giants: Dinosaurs 3D",
		"Dual Pen Sports",
		"Samurai Warriors: Chronicles",
		"Super Monkey Ball 3D",
		"Tom Clancy's Splinter Cell 3D",
		"Ridge Racer 3D",
	},

	// TurboGrafx-16 / PC Engine — North America, 1989-08-29.
	"pce": {
		"Keith Courage in Alpha Zones",
		"The Legendary Axe",
		"China Warrior",
		"Victory Run",
		"Blazing Lazers",
		"Vigilante",
		"Alien Crush",
		"Dungeon Explorer",
		"R-Type",
	},

	// Atari Jaguar — North America, 1993-11-23.
	"jag": {
		"Cybermorph",
		"Crescent Galaxy",
		"Evolution: Dino Dudes",
		"Raiden",
		"Trevor McFur in the Crescent Galaxy",
	},

	// 3DO Interactive Multiplayer — North America, 1993-10-04.
	"3do": {
		"Crash 'n Burn",
		"Total Eclipse",
		"Twisted: The Game Show",
		"Escape from Monster Manor",
		"John Madden Football",
		"Lemmings",
		"The Horde",
		"Shock Wave",
		"Stellar 7: Draxon's Revenge",
		"Way of the Warrior",
	},

	// Virtual Boy — North America, 1995-08-14.
	"vb": {
		"Mario's Tennis",
		"Red Alarm",
		"Galactic Pinball",
		"Teleroboxer",
	},

	// Sega Game Gear — North America, 1991-04-26.
	"gg": {
		"Columns",
		"Super Monaco GP",
		"G-LOC: Air Battle",
		"Psychic World",
		"Mickey Mouse: Castle of Illusion",
		"Chase H.Q.",
		"Pengo",
		"Revenge of Drancon",
		"Woody Pop",
	},

	// WonderSwan — Japan, 1999-03-04.
	"ws": {
		"Chocobo no Fushigi na Dungeon: For WonderSwan",
		"GunPey",
		"Super Robot Taisen Compact",
		"Tarepanda no Gunpey",
		"Dicing Knight Period",
	},

	// Neo Geo Pocket — Japan, 1998-10-28 (mono); NGP Color — Japan, 1999-03-19.
	"ngp": {
		"Baseball Stars",
		"King of Fighters R-1",
		"Melon-chan no Seichou Nikki",
		"Neo Geo Cup '98",
		"Samurai Shodown!",
		"Shougi no Tatsujin",
	},

	// Atari 2600 / VCS — North America, 1977-09-11.
	"a26": {
		"Combat",
		"Air-Sea Battle",
		"Basic Math",
		"Blackjack",
		"Indy 500",
		"Star Ship",
		"Street Racer",
		"Surround",
		"Video Olympics",
	},

	// Atari 7800 — North America, 1986-05 (limited) / 1986-09 (wide).
	"a78": {
		"Asteroids",
		"Centipede",
		"Dig Dug",
		"Food Fight",
		"Galaga",
		"Joust",
		"Ms. Pac-Man",
		"Pole Position II",
		"Robotron: 2084",
		"Xevious",
	},

	// Sega CD — North America, 1992-10-15.
	"scd": {
		"Sherlock Holmes: Consulting Detective",
		"Sol-Feace",
		"Cobra Command",
		"Night Trap",
		"Sewer Shark",
		"Black Hole Assault",
		"Chuck Rock",
		"Prince of Persia",
		"Wing Commander",
	},

	// Sega 32X — North America, 1994-11-21.
	"32x": {
		"Doom",
		"Virtua Racing Deluxe",
		"Star Wars Arcade",
		"Cosmic Carnage",
		"Metal Head",
	},

	// Neo Geo (AES home console) — Japan/NA, 1990.
	"neogeo": {
		"Nam-1975",
		"Baseball Stars Professional",
		"Top Player's Golf",
		"Magician Lord",
		"Cyber-Lip",
		"The Super Spy",
		"Riding Hero",
		"League Bowling",
	},

	// Atari Lynx — North America, 1989-09-01.
	"lynx": {
		"California Games",
		"Electrocop",
		"Blue Lightning",
		"Gates of Zendocon",
	},

	// Famicom Disk System — Japan, 1986-02-21.
	"fds": {
		"The Legend of Zelda",
		"Baseball",
		"Mahjong",
		"Soccer",
		"Tennis",
		"Golf",
	},

	// ColecoVision — North America, 1982-08.
	"cv": {
		"Donkey Kong",
		"Cosmic Avenger",
		"Lady Bug",
		"Mouse Trap",
		"Smurf: Rescue in Gargamel's Castle",
		"Venture",
		"Zaxxon",
		"Carnival",
		"Cosmic Crisis",
		"Head to Head Baseball",
		"Head to Head Football",
	},

	// Mattel Intellivision — North America, 1979-12.
	"intv": {
		"Las Vegas Poker & Blackjack",
		"Math Fun",
		"Backgammon",
		"Checkers",
		"Electric Company Math Fun",
		"Armor Battle",
		"Auto Racing",
		"Major League Baseball",
		"NBA Basketball",
		"NFL Football",
		"NHL Hockey",
	},

	// Magnavox Odyssey 2 — North America, 1978.
	"o2": {
		"Speedway!",
		"Spin-Out!",
		"Crypto-Logic!",
		"Math-A-Magic!",
		"Echo!",
		"Armored Encounter!",
		"Sub Chase!",
		"Las Vegas Blackjack!",
	},

	// Fairchild Channel F — North America, 1976-11.
	"chaf": {
		"Tic-Tac-Toe, Shooting Gallery, Doodle, and Quadra-Doodle",
		"Desert Fox, Shooting Gallery",
		"Video Blackjack",
		"Spitfire",
	},

	// Atari 5200 — North America, 1982-11.
	"a52": {
		"Super Breakout",
		"Missile Command",
		"Pac-Man",
		"Galaxian",
		"Centipede",
		"Defender",
		"Star Raiders",
		"Space Invaders",
		"Realsports Football",
		"Asteroids",
	},

	// GCE Vectrex — North America, 1982-11.
	"vec": {
		"Mine Storm",
		"Star Trek: The Motion Picture",
		"Scramble",
		"Berzerk",
		"Armor Attack",
		"Rip Off",
		"HeadsUp: Soccer/Football",
		"Hyperchase",
	},

	// PC Engine SuperGrafx — Japan, 1989-12-08.
	"sgx": {
		"Battle Ace",
		"Daimakaimura",
	},

	// PC-FX — Japan, 1994-12-23.
	"pcfx": {
		"Battle Heat",
		"Team Innocent: The Point of No Return",
	},

	// TurboGrafx-CD / PC Engine CD — Japan, 1988-12-04.
	"pcecd": {
		"Fighting Street",
		"No-Ri-Ko",
		"Bikkuriman World",
		"CD-ROM^2 System Card",
	},

	// Nintendo Wii — North America, 2006-11-19. Launch lineup of 21 titles.
	"wii": {
		"Wii Sports",
		"The Legend of Zelda: Twilight Princess",
		"Excite Truck",
		"Red Steel",
		"Rayman Raving Rabbids",
		"Trauma Center: Second Opinion",
		"Madden NFL 07",
		"Tony Hawk's Downhill Jam",
		"Call of Duty 3",
		"Marvel: Ultimate Alliance",
		"Need for Speed: Carbon",
		"Tom Clancy's Splinter Cell: Double Agent",
		"Cars",
		"Avatar: The Last Airbender",
		"GT Pro Series",
		"Happy Feet",
		"Open Season",
		"SpongeBob SquarePants: Creature from the Krusty Krab",
		"Super Monkey Ball: Banana Blitz",
		"World Series of Poker: Tournament of Champions",
	},

	// Sinclair ZX Spectrum — UK, 1982-04. The "launch lineup" concept is
	// fuzzier for an 8-bit home computer than for a console; this list is
	// the bundled "Horizons" cassette sampler that shipped with the 16K /
	// 48K plus the most prominent launch-window first-party releases.
	"zxs": {
		"Horizons",
		"Psion Chess",
		"VU-Calc",
		"Jet-Pac",
		"Pssst",
		"Cookie",
		"Tranz Am",
	},

	// Amstrad CPC — UK, 1984-06 (also EU and JP later that year). The
	// "Roland in" series and the bundled "Welcome" tape were the canonical
	// introduction to the platform; the rest are launch-window first-party
	// releases.
	"cpc": {
		"Roland on the Ropes",
		"Roland in the Caves",
		"Roland Goes Digging",
		"Roland in Time",
		"Roland in Space",
		"Sultan's Maze",
		"Oh Mummy",
		"Harrier Attack",
	},

	// Sharp X68000 — Japan, 1987-03. The platform shipped with built-in
	// arcade-port quality; this is its launch and earliest-window arcade
	// conversions, the platform's defining draw.
	"x68k": {
		"Gradius",
		"Out Run",
		"Tetris",
		"Hyper Crazy Climber",
		"Marble Madness",
		"Castle Excellent",
	},

	// TIC-80 fantasy console doesn't have a launch lineup in the
	// traditional sense; intentionally omitted. The console-detail page
	// renders no launch section when a key is absent.
}

// launchGamesFor returns the curated launch-title list for a console, keyed
// by lowercase abbreviation. Returns nil if no list is curated.
func launchGamesFor(abbr string) []string {
	if titles, ok := launchGamesByConsole[abbr]; ok {
		return titles
	}
	return nil
}
