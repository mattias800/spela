export interface Console {
  id: string;
  name: string;
  abbreviation: string;
  gameCount: number;
  colorTheme: string;
}

export interface Game {
  id: string;
  title: string;
  consoleId: string;
  consoleName: string;
  coverUrl: string;
  description: string;
  developer: string;
  publisher: string;
  releaseDate: string;
  genre: string;
  players: number;
  rating: number;
  fileSize: number;
  isFavorite: boolean;
  totalPlayTime: number;
  lastPlayedAt: string | null;
  screenshotUrls: string[];
}

export const consoles: Console[] = [
  { id: "1", name: "Nintendo Entertainment System", abbreviation: "NES", gameCount: 47, colorTheme: "#e53e3e" },
  { id: "2", name: "Super Nintendo", abbreviation: "SNES", gameCount: 62, colorTheme: "#805ad5" },
  { id: "3", name: "Game Boy", abbreviation: "GB", gameCount: 34, colorTheme: "#38a169" },
  { id: "4", name: "Game Boy Advance", abbreviation: "GBA", gameCount: 28, colorTheme: "#667eea" },
  { id: "5", name: "Nintendo 64", abbreviation: "N64", gameCount: 19, colorTheme: "#48bb78" },
  { id: "6", name: "Sega Genesis", abbreviation: "Genesis", gameCount: 41, colorTheme: "#2b6cb0" },
  { id: "7", name: "PlayStation", abbreviation: "PSX", gameCount: 55, colorTheme: "#a0aec0" },
  { id: "8", name: "Neo Geo", abbreviation: "NeoGeo", gameCount: 12, colorTheme: "#ecc94b" },
  { id: "9", name: "Game Boy Color", abbreviation: "GBC", gameCount: 22, colorTheme: "#319795" },
  { id: "10", name: "Arcade", abbreviation: "Arcade", gameCount: 38, colorTheme: "#f6ad55" },
];

// Placeholder cover URLs - using colored placeholder boxes
function cover(seed: number): string {
  // We'll use inline SVG data URIs as placeholders
  const colors = [
    "1a1a2e", "16213e", "0f3460", "533483", "e94560",
    "1b262c", "0f4c75", "3282b8", "bbe1fa", "2d3436",
    "6c5ce7", "a29bfe", "fd79a8", "e17055", "00b894",
    "fdcb6e", "e84393", "0984e3", "00cec9", "6c5ce7",
  ];
  const c = colors[seed % colors.length];
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="300" height="400" viewBox="0 0 300 400"><rect width="300" height="400" fill="#${c}"/><text x="150" y="200" fill="white" font-family="sans-serif" font-size="14" text-anchor="middle" opacity="0.6">Cover Art</text></svg>`)}`;
}

function screenshot(seed: number): string {
  const colors = ["2d3436", "636e72", "0984e3", "6c5ce7", "e17055", "00b894"];
  const c = colors[seed % colors.length];
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"><rect width="640" height="360" fill="#${c}"/><text x="320" y="180" fill="white" font-family="sans-serif" font-size="16" text-anchor="middle" opacity="0.5">Screenshot</text></svg>`)}`;
}

export const snesGames: Game[] = [
  {
    id: "g1", title: "Super Mario World", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(0), description: "Mario and Luigi must travel across Dinosaur Land to rescue Princess Toadstool from Bowser and his Koopalings. Along the way, they discover Yoshi, a dinosaur who can eat enemies and gain special powers.",
    developer: "Nintendo EAD", publisher: "Nintendo", releaseDate: "1990-11-21", genre: "Platformer",
    players: 2, rating: 9, fileSize: 524288, isFavorite: true, totalPlayTime: 7200, lastPlayedAt: "2025-01-15T10:30:00Z",
    screenshotUrls: [screenshot(0), screenshot(1), screenshot(2), screenshot(3)],
  },
  {
    id: "g2", title: "The Legend of Zelda: A Link to the Past", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(1), description: "Link must rescue the seven descendants of the Sages and Princess Zelda, then defeat Ganon, who has been manipulating events from the Dark World.",
    developer: "Nintendo EAD", publisher: "Nintendo", releaseDate: "1991-11-21", genre: "Action RPG",
    players: 1, rating: 10, fileSize: 1048576, isFavorite: true, totalPlayTime: 14400, lastPlayedAt: "2025-01-12T14:00:00Z",
    screenshotUrls: [screenshot(2), screenshot(3)],
  },
  {
    id: "g3", title: "Super Metroid", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(2), description: "Samus Aran returns to planet Zebes to retrieve the last Metroid larva from the Space Pirates in this atmospheric action-adventure classic.",
    developer: "Nintendo R&D1", publisher: "Nintendo", releaseDate: "1994-03-19", genre: "Action-Adventure",
    players: 1, rating: 10, fileSize: 3145728, isFavorite: false, totalPlayTime: 3600, lastPlayedAt: "2024-12-20T08:00:00Z",
    screenshotUrls: [screenshot(4), screenshot(5)],
  },
  {
    id: "g4", title: "Chrono Trigger", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(3), description: "A group of young adventurers travel through time to prevent a global catastrophe. Features a unique battle system and multiple endings.",
    developer: "Square", publisher: "Square", releaseDate: "1995-03-11", genre: "RPG",
    players: 1, rating: 10, fileSize: 4194304, isFavorite: true, totalPlayTime: 28800, lastPlayedAt: "2025-01-10T20:00:00Z",
    screenshotUrls: [screenshot(0), screenshot(5)],
  },
  {
    id: "g5", title: "Final Fantasy VI", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(4), description: "In a world where magic has been forgotten, a young woman with mysterious powers joins a rebel group to battle an empire bent on world domination.",
    developer: "Square", publisher: "Square", releaseDate: "1994-04-02", genre: "RPG",
    players: 1, rating: 9, fileSize: 3145728, isFavorite: false, totalPlayTime: 21600, lastPlayedAt: "2024-11-05T19:00:00Z",
    screenshotUrls: [screenshot(1), screenshot(3)],
  },
  {
    id: "g6", title: "Donkey Kong Country", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(5), description: "Donkey Kong and Diddy Kong must recover their stolen banana hoard from King K. Rool and his Kremling army.",
    developer: "Rare", publisher: "Nintendo", releaseDate: "1994-11-18", genre: "Platformer",
    players: 2, rating: 8, fileSize: 4194304, isFavorite: false, totalPlayTime: 5400, lastPlayedAt: "2024-10-15T16:30:00Z",
    screenshotUrls: [screenshot(2), screenshot(4)],
  },
  {
    id: "g7", title: "Super Mario Kart", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(6), description: "Race against friends or the CPU as your favorite Nintendo characters in this kart-racing classic that defined the genre.",
    developer: "Nintendo EAD", publisher: "Nintendo", releaseDate: "1992-08-27", genre: "Racing",
    players: 2, rating: 8, fileSize: 524288, isFavorite: true, totalPlayTime: 10800, lastPlayedAt: "2025-01-08T21:00:00Z",
    screenshotUrls: [screenshot(0), screenshot(1)],
  },
  {
    id: "g8", title: "Street Fighter II Turbo", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(7), description: "The definitive version of the fighting game classic with faster gameplay, new special moves, and the ability to play as the boss characters.",
    developer: "Capcom", publisher: "Capcom", releaseDate: "1993-07-11", genre: "Fighting",
    players: 2, rating: 9, fileSize: 3145728, isFavorite: false, totalPlayTime: 7200, lastPlayedAt: "2024-09-20T13:00:00Z",
    screenshotUrls: [screenshot(3), screenshot(5)],
  },
  {
    id: "g9", title: "EarthBound", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(8), description: "A quirky RPG where a boy named Ness and his friends must save the world from an alien invasion, traveling through a bizarre modern-day setting.",
    developer: "Ape/HAL Laboratory", publisher: "Nintendo", releaseDate: "1994-08-27", genre: "RPG",
    players: 1, rating: 9, fileSize: 3145728, isFavorite: true, totalPlayTime: 18000, lastPlayedAt: "2024-12-01T11:00:00Z",
    screenshotUrls: [screenshot(1), screenshot(4)],
  },
  {
    id: "g10", title: "Mega Man X", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(9), description: "In the year 21XX, a new generation of reploids threatens humanity. Mega Man X must stop the Maverick uprising led by Sigma.",
    developer: "Capcom", publisher: "Capcom", releaseDate: "1993-12-17", genre: "Action Platformer",
    players: 1, rating: 9, fileSize: 1572864, isFavorite: false, totalPlayTime: 4500, lastPlayedAt: "2024-08-15T09:00:00Z",
    screenshotUrls: [screenshot(2), screenshot(5)],
  },
  {
    id: "g11", title: "Secret of Mana", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(10), description: "Three heroes embark on a quest to restore the Mana Sword and save the world from an ancient evil in this action RPG with real-time combat.",
    developer: "Square", publisher: "Square", releaseDate: "1993-08-06", genre: "Action RPG",
    players: 3, rating: 8, fileSize: 2097152, isFavorite: false, totalPlayTime: 9000, lastPlayedAt: "2024-07-22T17:00:00Z",
    screenshotUrls: [screenshot(0), screenshot(3)],
  },
  {
    id: "g12", title: "Super Castlevania IV", consoleId: "2", consoleName: "Super Nintendo",
    coverUrl: cover(11), description: "Simon Belmont returns to Dracula's castle in this atmospheric action-platformer with eight-directional whipping and Mode 7 effects.",
    developer: "Konami", publisher: "Konami", releaseDate: "1991-10-31", genre: "Action Platformer",
    players: 1, rating: 8, fileSize: 1048576, isFavorite: false, totalPlayTime: 3600, lastPlayedAt: null,
    screenshotUrls: [screenshot(4), screenshot(5)],
  },
];

export function formatPlayTime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  const hours = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1048576).toFixed(1)} MB`;
}
