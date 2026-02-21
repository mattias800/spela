import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";

// ─── Material You / Google Theme (Light) ─────────────────────────────
const M = {
  bg: "#fffbfe",
  surface: "#ffffff",
  surfaceTint: "#f7f2fa",
  surfaceVariant: "#e7e0ec",
  primary: "#6750a4",
  primaryContainer: "#eaddff",
  onPrimary: "#ffffff",
  onPrimaryContainer: "#21005d",
  secondary: "#625b71",
  secondaryContainer: "#e8def8",
  tertiary: "#7d5260",
  tertiaryContainer: "#ffd8e4",
  coral: "#FF6F61",
  onSurface: "#1c1b1f",
  onSurfaceVariant: "#49454f",
  outline: "#79747e",
  outlineVariant: "#cac4d0",
  font: "'Google Sans', 'Product Sans', 'Roboto', system-ui, sans-serif",
};

// Tonal surface tints for console cards
const tonalTints = [
  "#f3edf7", "#edf3f7", "#f7edf3", "#edf7f0", "#f7f3ed",
  "#f0edf7", "#edf7f5", "#f7edee", "#edf0f7", "#f5f7ed",
];

// Nav rail icons (using unicode symbols)
const navItems = [
  { label: "Home", icon: "\u2302", active: false },
  { label: "Consoles", icon: "\u25A3", active: true },
  { label: "Games", icon: "\u25B6", active: false },
  { label: "Favorites", icon: "\u2665", active: false },
  { label: "Settings", icon: "\u2699", active: false },
];

// ─── Material Layout with Nav Rail ───────────────────────────────────
function MaterialLayout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();

  return (
    <div
      style={{
        minHeight: "100vh",
        background: M.bg,
        fontFamily: M.font,
        color: M.onSurface,
        display: "flex",
      }}
    >
      {/* Navigation Rail */}
      <nav
        style={{
          width: 80,
          minHeight: "100vh",
          background: M.surface,
          borderRight: `1px solid ${M.outlineVariant}`,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          paddingTop: 16,
          gap: 4,
          flexShrink: 0,
        }}
      >
        {/* App icon at top */}
        <div
          onClick={() => navigate("/")}
          style={{
            width: 56,
            height: 56,
            borderRadius: 16,
            background: M.primaryContainer,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 20,
            fontWeight: 700,
            color: M.primary,
            cursor: "pointer",
            marginBottom: 20,
          }}
        >
          S
        </div>

        {navItems.map((item) => (
          <div
            key={item.label}
            onClick={() => navigate("/")}
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              cursor: "pointer",
              padding: "4px 0",
              width: "100%",
            }}
          >
            {/* Pill indicator */}
            <div
              style={{
                width: 56,
                height: 32,
                borderRadius: 16,
                background: item.active ? M.secondaryContainer : "transparent",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 18,
                color: item.active ? M.onSurface : M.onSurfaceVariant,
                transition: "background 0.2s",
              }}
            >
              {item.icon}
            </div>
            <span
              style={{
                fontSize: 11,
                fontWeight: item.active ? 600 : 500,
                color: item.active ? M.onSurface : M.onSurfaceVariant,
                marginTop: 2,
              }}
            >
              {item.label}
            </span>
          </div>
        ))}
      </nav>

      {/* Main content area */}
      <div style={{ flex: 1, overflow: "auto" }}>
        {children}
      </div>
    </div>
  );
}

// ─── Console List Page ──────────────────────────────────────────────
function ConsolesPage() {
  const navigate = useNavigate();
  const totalGames = consoles.reduce((a, c) => a + c.gameCount, 0);

  return (
    <MaterialLayout>
      <div style={{ padding: "24px 32px 60px", maxWidth: 1100, margin: "0 auto" }}>
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            fontSize: 14,
            color: M.primary,
            textDecoration: "none",
            marginBottom: 24,
            fontWeight: 500,
          }}
        >
          ← Back to Gallery
        </a>

        {/* Top App Bar area */}
        <div style={{ marginBottom: 24 }}>
          <h1
            style={{
              fontSize: 28,
              fontWeight: 400,
              margin: "0 0 16px 0",
              color: M.onSurface,
            }}
          >
            Spela
          </h1>

          {/* Large rounded search bar */}
          <div
            style={{
              width: "100%",
              height: 56,
              borderRadius: 28,
              background: M.surfaceVariant,
              display: "flex",
              alignItems: "center",
              padding: "0 24px",
              gap: 12,
            }}
          >
            <span style={{ fontSize: 20, color: M.onSurfaceVariant }}>
              &#128269;
            </span>
            <span
              style={{
                fontSize: 16,
                color: M.onSurfaceVariant,
                fontWeight: 400,
              }}
            >
              Search platforms and games
            </span>
          </div>
        </div>

        {/* Section heading */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 20,
          }}
        >
          <h2
            style={{
              fontSize: 22,
              fontWeight: 400,
              margin: 0,
              color: M.onSurface,
            }}
          >
            Platforms
          </h2>
          <span
            style={{
              fontSize: 14,
              color: M.onSurfaceVariant,
              fontWeight: 500,
            }}
          >
            {consoles.length} systems · {totalGames} games
          </span>
        </div>

        {/* Console cards grid - 3 columns */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 16,
          }}
        >
          {consoles.map((c, i) => (
            <div
              key={c.id}
              onClick={() => navigate(`console/${c.id}`)}
              style={{
                cursor: "pointer",
                background: tonalTints[i % tonalTints.length],
                borderRadius: 20,
                padding: 24,
                position: "relative",
                overflow: "hidden",
                transition: "all 0.2s ease",
                boxShadow: "0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = "0 4px 12px rgba(0,0,0,0.12), 0 2px 4px rgba(0,0,0,0.08)";
                e.currentTarget.style.transform = "translateY(-2px)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = "0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)";
                e.currentTarget.style.transform = "translateY(0)";
              }}
            >
              {/* Subtle tonal accent circle in background */}
              <div
                style={{
                  position: "absolute",
                  top: -20,
                  right: -20,
                  width: 100,
                  height: 100,
                  borderRadius: "50%",
                  background: `${c.colorTheme}15`,
                  pointerEvents: "none",
                }}
              />

              {/* Console icon placeholder */}
              <div
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: 16,
                  background: `${c.colorTheme}20`,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  marginBottom: 16,
                  fontSize: 12,
                  fontWeight: 700,
                  color: c.colorTheme,
                }}
              >
                {c.abbreviation.slice(0, 3)}
              </div>

              {/* Console name */}
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 500,
                  margin: "0 0 8px 0",
                  color: M.onSurface,
                  lineHeight: 1.3,
                }}
              >
                {c.name}
              </h3>

              {/* Game count pill badge */}
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 6,
                  padding: "4px 12px",
                  borderRadius: 8,
                  background: `${c.colorTheme}12`,
                  fontSize: 13,
                  fontWeight: 500,
                  color: M.onSurfaceVariant,
                }}
              >
                <span style={{ color: c.colorTheme, fontWeight: 600 }}>
                  {c.gameCount}
                </span>
                games
              </div>
            </div>
          ))}
        </div>

        {/* FAB */}
        <div
          style={{
            position: "fixed",
            bottom: 32,
            right: 32,
            width: 56,
            height: 56,
            borderRadius: 16,
            background: M.primaryContainer,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 28,
            color: M.primary,
            cursor: "pointer",
            boxShadow: "0 4px 8px rgba(0,0,0,0.15), 0 2px 4px rgba(0,0,0,0.1)",
            transition: "all 0.2s",
          }}
        >
          +
        </div>
      </div>
    </MaterialLayout>
  );
}

// ─── Games Page ─────────────────────────────────────────────────────
function GamesPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const con = consoles.find((c) => c.id === id) ?? consoles[0];

  const chipFilters = ["All", "RPG", "Platformer", "Action", "Racing"];

  return (
    <MaterialLayout>
      <div style={{ padding: "24px 32px 60px", maxWidth: 1100, margin: "0 auto" }}>
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            fontSize: 14,
            color: M.primary,
            textDecoration: "none",
            marginBottom: 16,
            fontWeight: 500,
          }}
        >
          ← Back to Gallery
        </a>

        {/* Top app bar with console name */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 8,
          }}
        >
          <span
            onClick={() => navigate("/")}
            style={{
              fontSize: 14,
              color: M.primary,
              cursor: "pointer",
              fontWeight: 500,
            }}
          >
            Platforms
          </span>
          <span style={{ color: M.outline }}>›</span>
          <span
            style={{
              fontSize: 14,
              color: M.onSurface,
              fontWeight: 500,
            }}
          >
            {con.name}
          </span>
        </div>

        <h1
          style={{
            fontSize: 28,
            fontWeight: 400,
            margin: "0 0 4px 0",
            color: M.onSurface,
          }}
        >
          {con.name}
        </h1>
        <p
          style={{
            fontSize: 14,
            color: M.onSurfaceVariant,
            margin: "0 0 20px 0",
          }}
        >
          {snesGames.length} games available
        </p>

        {/* Filter chips row */}
        <div
          style={{
            display: "flex",
            gap: 8,
            marginBottom: 24,
            flexWrap: "wrap",
          }}
        >
          {chipFilters.map((chip, i) => (
            <div
              key={chip}
              style={{
                padding: "8px 16px",
                borderRadius: 8,
                border: i === 0 ? "none" : `1px solid ${M.outline}`,
                background: i === 0 ? M.secondaryContainer : "transparent",
                color: i === 0 ? M.onSurface : M.onSurfaceVariant,
                fontSize: 14,
                fontWeight: 500,
                cursor: "pointer",
                transition: "all 0.15s",
              }}
            >
              {chip}
            </div>
          ))}
        </div>

        {/* Games grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
            gap: 16,
          }}
        >
          {snesGames.map((game) => (
            <div
              key={game.id}
              onClick={() => navigate(`../game/${game.id}`)}
              style={{
                cursor: "pointer",
                background: M.surfaceTint,
                borderRadius: 16,
                overflow: "hidden",
                transition: "all 0.2s ease",
                boxShadow: "0 1px 3px rgba(0,0,0,0.06)",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = "0 4px 12px rgba(0,0,0,0.1)";
                e.currentTarget.style.transform = "translateY(-2px)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = "0 1px 3px rgba(0,0,0,0.06)";
                e.currentTarget.style.transform = "translateY(0)";
              }}
            >
              {/* Cover */}
              <div style={{ position: "relative" }}>
                <img
                  src={game.coverUrl}
                  alt={game.title}
                  style={{
                    width: "100%",
                    aspectRatio: "3 / 4",
                    objectFit: "cover",
                    display: "block",
                  }}
                />

                {/* Favorite heart */}
                <div
                  style={{
                    position: "absolute",
                    top: 10,
                    right: 10,
                    width: 36,
                    height: 36,
                    borderRadius: "50%",
                    background: "rgba(255,255,255,0.9)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: 18,
                    color: game.isFavorite ? M.tertiary : M.outline,
                    boxShadow: "0 1px 4px rgba(0,0,0,0.12)",
                  }}
                >
                  {game.isFavorite ? "\u2665" : "\u2661"}
                </div>

                {/* Genre chip on image */}
                <div
                  style={{
                    position: "absolute",
                    bottom: 10,
                    left: 10,
                    padding: "4px 10px",
                    borderRadius: 6,
                    background: M.tertiaryContainer,
                    fontSize: 11,
                    fontWeight: 500,
                    color: M.tertiary,
                  }}
                >
                  {game.genre}
                </div>
              </div>

              {/* Title */}
              <div style={{ padding: "12px 16px 14px" }}>
                <h4
                  style={{
                    fontSize: 14,
                    fontWeight: 500,
                    margin: "0 0 4px 0",
                    color: M.onSurface,
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    lineHeight: 1.3,
                  }}
                >
                  {game.title}
                </h4>
                <div
                  style={{
                    fontSize: 12,
                    color: M.onSurfaceVariant,
                    fontWeight: 400,
                  }}
                >
                  {game.developer}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </MaterialLayout>
  );
}

// ─── Game Detail Page ───────────────────────────────────────────────
function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id) ?? snesGames[0];

  return (
    <MaterialLayout>
      <div style={{ padding: "24px 32px 60px", maxWidth: 960, margin: "0 auto" }}>
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 8,
            fontSize: 14,
            color: M.primary,
            textDecoration: "none",
            marginBottom: 16,
            fontWeight: 500,
          }}
        >
          ← Back to Gallery
        </a>

        {/* Breadcrumb */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            fontSize: 14,
            color: M.onSurfaceVariant,
            marginBottom: 24,
          }}
        >
          <span
            onClick={() => navigate("/")}
            style={{ cursor: "pointer", color: M.primary, fontWeight: 500 }}
          >
            Platforms
          </span>
          <span style={{ color: M.outline }}>›</span>
          <span
            onClick={() => navigate(`../console/${game.consoleId}`)}
            style={{ cursor: "pointer", color: M.primary, fontWeight: 500 }}
          >
            {game.consoleName}
          </span>
          <span style={{ color: M.outline }}>›</span>
          <span style={{ fontWeight: 500, color: M.onSurface }}>{game.title}</span>
        </div>

        {/* Main layout: cover + info */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "280px 1fr",
            gap: 32,
            marginBottom: 40,
          }}
        >
          {/* Cover in rounded container */}
          <div>
            <div
              style={{
                borderRadius: 24,
                overflow: "hidden",
                boxShadow: "0 4px 16px rgba(0,0,0,0.1)",
              }}
            >
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "3 / 4",
                  objectFit: "cover",
                  display: "block",
                }}
              />
            </div>
          </div>

          {/* Info section */}
          <div>
            {/* Title - headlineLarge style */}
            <h1
              style={{
                fontSize: 32,
                fontWeight: 400,
                margin: "0 0 8px 0",
                color: M.onSurface,
                lineHeight: 1.2,
              }}
            >
              {game.title}
            </h1>

            <p
              style={{
                fontSize: 14,
                color: M.onSurfaceVariant,
                margin: "0 0 20px 0",
              }}
            >
              {game.developer} · {game.publisher}
            </p>

            {/* Chip row */}
            <div
              style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 8,
                marginBottom: 24,
              }}
            >
              {[
                { label: game.genre, bg: M.secondaryContainer, color: M.secondary },
                { label: `${game.players} Player${game.players > 1 ? "s" : ""}`, bg: M.tertiaryContainer, color: M.tertiary },
                { label: game.releaseDate.slice(0, 4), bg: `${M.primary}15`, color: M.primary },
                { label: `${game.rating}/10`, bg: "#fef3c7", color: "#92400e" },
              ].map((chip) => (
                <div
                  key={chip.label}
                  style={{
                    padding: "6px 14px",
                    borderRadius: 8,
                    background: chip.bg,
                    fontSize: 13,
                    fontWeight: 500,
                    color: chip.color,
                  }}
                >
                  {chip.label}
                </div>
              ))}
            </div>

            {/* Tonal info container */}
            <div
              style={{
                background: M.surfaceTint,
                borderRadius: 16,
                padding: 20,
                marginBottom: 24,
              }}
            >
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: 16,
                }}
              >
                {[
                  { label: "Play Time", value: formatPlayTime(game.totalPlayTime) },
                  { label: "File Size", value: formatFileSize(game.fileSize) },
                  { label: "Console", value: game.consoleName },
                  {
                    label: "Last Played",
                    value: game.lastPlayedAt
                      ? new Date(game.lastPlayedAt).toLocaleDateString()
                      : "Never",
                  },
                ].map((item) => (
                  <div key={item.label}>
                    <div
                      style={{
                        fontSize: 12,
                        color: M.onSurfaceVariant,
                        fontWeight: 500,
                        marginBottom: 2,
                      }}
                    >
                      {item.label}
                    </div>
                    <div
                      style={{
                        fontSize: 15,
                        fontWeight: 500,
                        color: M.onSurface,
                      }}
                    >
                      {item.value}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Action buttons */}
            <div style={{ display: "flex", gap: 12 }}>
              {/* Filled tonal Play button */}
              <button
                style={{
                  padding: "14px 40px",
                  borderRadius: 20,
                  border: "none",
                  background: M.primaryContainer,
                  color: M.onPrimaryContainer,
                  fontSize: 15,
                  fontWeight: 500,
                  fontFamily: M.font,
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  transition: "all 0.15s",
                  boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow = "0 2px 6px rgba(0,0,0,0.15)";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow = "0 1px 3px rgba(0,0,0,0.1)";
                }}
              >
                &#9654; Play
              </button>

              {/* Outlined Favorite button */}
              <button
                style={{
                  padding: "14px 28px",
                  borderRadius: 20,
                  border: `1px solid ${M.outline}`,
                  background: "transparent",
                  color: game.isFavorite ? M.tertiary : M.onSurfaceVariant,
                  fontSize: 15,
                  fontWeight: 500,
                  fontFamily: M.font,
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  transition: "all 0.15s",
                }}
              >
                {game.isFavorite ? "\u2665" : "\u2661"}{" "}
                {game.isFavorite ? "Favorited" : "Favorite"}
              </button>
            </div>
          </div>
        </div>

        {/* Description in body text */}
        <div style={{ marginBottom: 40 }}>
          <h2
            style={{
              fontSize: 22,
              fontWeight: 400,
              margin: "0 0 12px 0",
              color: M.onSurface,
            }}
          >
            About
          </h2>
          <p
            style={{
              fontSize: 15,
              lineHeight: 1.75,
              color: M.onSurfaceVariant,
              margin: 0,
              maxWidth: 720,
            }}
          >
            {game.description}
          </p>
        </div>

        {/* Screenshots in rounded cards */}
        {game.screenshotUrls.length > 0 && (
          <div style={{ marginBottom: 48 }}>
            <h2
              style={{
                fontSize: 22,
                fontWeight: 400,
                margin: "0 0 16px 0",
                color: M.onSurface,
              }}
            >
              Screenshots
            </h2>
            <div
              style={{
                display: "flex",
                gap: 12,
                overflowX: "auto",
                paddingBottom: 8,
              }}
            >
              {game.screenshotUrls.map((url, i) => (
                <div
                  key={i}
                  style={{
                    flexShrink: 0,
                    width: 300,
                    borderRadius: 16,
                    overflow: "hidden",
                    boxShadow: "0 1px 4px rgba(0,0,0,0.08)",
                  }}
                >
                  <img
                    src={url}
                    alt={`Screenshot ${i + 1}`}
                    style={{
                      width: "100%",
                      aspectRatio: "16 / 9",
                      objectFit: "cover",
                      display: "block",
                    }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </MaterialLayout>
  );
}

// ─── Proposal 5 ─────────────────────────────────────────────────────
export function Proposal5() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="console/:id" element={<GamesPage />} />
      <Route path="game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
