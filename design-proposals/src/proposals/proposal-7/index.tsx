import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import { consoles, snesGames, formatPlayTime, formatFileSize } from "@/mock-data";
import type { Game } from "@/mock-data";

// Disney+ / Magical Colorful Dark Theme
const bg = "#1a1d29";
const surface = "#252836";
const card = "#2a2d3a";
const blue = "#113CCF";
const purple = "#7B61FF";
const gold = "#F5C518";
const teal = "#0FC2C0";
const rose = "#FF6B8A";
const textPrimary = "#f0f0f0";
const textSecondary = "#9ca3af";
const font = "'Inter', system-ui, -apple-system, sans-serif";

// Each console gets a unique magical color
const consoleColors = [
  { bg: "linear-gradient(135deg, #F5C518, #e6a800)", glow: "#F5C518" },      // NES: gold
  { bg: "linear-gradient(135deg, #7B61FF, #5a3fd4)", glow: "#7B61FF" },      // SNES: purple
  { bg: "linear-gradient(135deg, #0FC2C0, #0a9e9c)", glow: "#0FC2C0" },      // GB: teal
  { bg: "linear-gradient(135deg, #113CCF, #0e2fa6)", glow: "#113CCF" },      // GBA: blue
  { bg: "linear-gradient(135deg, #FF6B8A, #e0516e)", glow: "#FF6B8A" },      // N64: rose
  { bg: "linear-gradient(135deg, #3B82F6, #2563EB)", glow: "#3B82F6" },      // Genesis: bright blue
  { bg: "linear-gradient(135deg, #A78BFA, #7C3AED)", glow: "#A78BFA" },      // PSX: lavender
  { bg: "linear-gradient(135deg, #FBBF24, #D97706)", glow: "#FBBF24" },      // Neo Geo: amber
  { bg: "linear-gradient(135deg, #34D399, #059669)", glow: "#34D399" },      // GBC: emerald
  { bg: "linear-gradient(135deg, #FB923C, #EA580C)", glow: "#FB923C" },      // Arcade: orange
];

const genreColors: Record<string, string> = {
  Platformer: gold,
  "Action RPG": purple,
  "Action-Adventure": teal,
  RPG: rose,
  Racing: blue,
  Fighting: "#FB923C",
  "Action Platformer": "#34D399",
};

function getConsoleColor(index: number) {
  return consoleColors[index % consoleColors.length];
}

function getConsoleColorById(consoleId: string) {
  const idx = consoles.findIndex((c) => c.id === consoleId);
  return getConsoleColor(idx >= 0 ? idx : 0);
}

// Console List Page
function ConsolesPage() {
  const navigate = useNavigate();

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textPrimary,
      }}
    >
      {/* Top Nav */}
      <nav
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "16px 48px",
          background: "#13152099",
          borderBottom: "1px solid #2a2d3a",
          backdropFilter: "blur(12px)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 32 }}>
          <span
            style={{
              fontSize: 24,
              fontWeight: 800,
              color: gold,
              letterSpacing: "0.04em",
            }}
          >
            Spela
          </span>
          <div style={{ display: "flex", gap: 24 }}>
            {["Home", "Consoles", "Favorites", "Downloads"].map((item, i) => (
              <span
                key={item}
                style={{
                  fontSize: 14,
                  fontWeight: i === 1 ? 600 : 400,
                  color: i === 1 ? textPrimary : textSecondary,
                  cursor: "pointer",
                  transition: "color 0.2s",
                }}
                onMouseEnter={(e) => (e.currentTarget.style.color = textPrimary)}
                onMouseLeave={(e) => {
                  if (i !== 1) e.currentTarget.style.color = textSecondary;
                }}
              >
                {item}
              </span>
            ))}
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <div
            style={{
              padding: "8px 20px",
              borderRadius: 24,
              background: "#2a2d3a",
              border: "1px solid #363a4a",
              fontSize: 13,
              color: textSecondary,
            }}
          >
            Search...
          </div>
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: "50%",
              background: `linear-gradient(135deg, ${blue}, ${purple})`,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 14,
              fontWeight: 700,
              color: "#fff",
            }}
          >
            U
          </div>
        </div>
      </nav>

      {/* Back to Gallery */}
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "16px 48px 0" }}>
        <a
          href="/"
          style={{
            fontSize: 13,
            color: textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
          onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
        >
          &larr; Back to Gallery
        </a>
      </div>

      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "24px 48px 60px" }}>
        {/* Hero Banner */}
        <div
          style={{
            borderRadius: 20,
            padding: "56px 56px",
            marginBottom: 48,
            background: `linear-gradient(135deg, ${blue}dd, ${purple}bb, ${teal}99)`,
            position: "relative",
            overflow: "hidden",
          }}
        >
          {/* Decorative sparkles */}
          <div
            style={{
              position: "absolute",
              top: 20,
              right: 60,
              fontSize: 28,
              opacity: 0.6,
            }}
          >
            &#10024;
          </div>
          <div
            style={{
              position: "absolute",
              top: 50,
              right: 140,
              fontSize: 18,
              opacity: 0.4,
            }}
          >
            &#10024;
          </div>
          <div
            style={{
              position: "absolute",
              bottom: 30,
              left: 80,
              fontSize: 22,
              opacity: 0.3,
            }}
          >
            &#10024;
          </div>
          <div
            style={{
              position: "absolute",
              top: 30,
              left: "40%",
              fontSize: 16,
              opacity: 0.5,
            }}
          >
            &#10024;
          </div>
          <div
            style={{
              position: "absolute",
              bottom: 20,
              right: "30%",
              fontSize: 24,
              opacity: 0.35,
            }}
          >
            &#10024;
          </div>
          <h1
            style={{
              fontSize: 48,
              fontWeight: 800,
              margin: 0,
              color: "#fff",
              lineHeight: 1.1,
              position: "relative",
            }}
          >
            Your Worlds Await
          </h1>
          <p
            style={{
              fontSize: 18,
              color: "rgba(255,255,255,0.8)",
              margin: "12px 0 0 0",
              position: "relative",
            }}
          >
            {consoles.length} magical worlds to explore, each with its own collection of adventures
          </p>
        </div>

        {/* Section Title */}
        <div style={{ marginBottom: 28 }}>
          <h2
            style={{
              fontSize: 24,
              fontWeight: 700,
              margin: 0,
              color: textPrimary,
            }}
          >
            Platforms
          </h2>
          <div
            style={{
              width: 60,
              height: 3,
              borderRadius: 2,
              background: `linear-gradient(90deg, ${gold}, ${gold}44)`,
              marginTop: 8,
            }}
          />
        </div>

        {/* Console Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
            gap: 20,
          }}
        >
          {consoles.map((c, i) => {
            const color = getConsoleColor(i);
            return (
              <div
                key={c.id}
                onClick={() => navigate(`console/${c.id}`)}
                style={{
                  borderRadius: 16,
                  padding: "28px 24px",
                  background: color.bg,
                  cursor: "pointer",
                  transition: "all 0.3s ease",
                  position: "relative",
                  overflow: "hidden",
                  boxShadow: `0 4px 20px ${color.glow}22`,
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = "scale(1.04)";
                  e.currentTarget.style.boxShadow = `0 8px 40px ${color.glow}55`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = "scale(1)";
                  e.currentTarget.style.boxShadow = `0 4px 20px ${color.glow}22`;
                }}
              >
                {/* Decorative circle */}
                <div
                  style={{
                    position: "absolute",
                    top: -30,
                    right: -30,
                    width: 100,
                    height: 100,
                    borderRadius: "50%",
                    background: "rgba(255,255,255,0.1)",
                    pointerEvents: "none",
                  }}
                />
                <div
                  style={{
                    fontSize: 36,
                    fontWeight: 800,
                    color: "#fff",
                    marginBottom: 8,
                    lineHeight: 1,
                    textShadow: "0 2px 8px rgba(0,0,0,0.2)",
                  }}
                >
                  {c.abbreviation}
                </div>
                <div
                  style={{
                    fontSize: 15,
                    fontWeight: 600,
                    color: "rgba(255,255,255,0.95)",
                    marginBottom: 12,
                  }}
                >
                  {c.name}
                </div>
                <span
                  style={{
                    display: "inline-block",
                    padding: "4px 12px",
                    borderRadius: 12,
                    background: "rgba(255,255,255,0.2)",
                    backdropFilter: "blur(4px)",
                    fontSize: 12,
                    fontWeight: 600,
                    color: "#fff",
                  }}
                >
                  {c.gameCount} games
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// Games Page
function GamesPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const con = consoles.find((c) => c.id === id) || consoles[0];
  const conColor = getConsoleColorById(con.id);
  const genres = Array.from(new Set(snesGames.map((g) => g.genre)));

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textPrimary,
      }}
    >
      {/* Top Nav */}
      <nav
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "16px 48px",
          background: "#13152099",
          borderBottom: "1px solid #2a2d3a",
          backdropFilter: "blur(12px)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 32 }}>
          <span
            style={{
              fontSize: 24,
              fontWeight: 800,
              color: gold,
              letterSpacing: "0.04em",
              cursor: "pointer",
            }}
            onClick={() => navigate("/proposal/7")}
          >
            Spela
          </span>
          <div style={{ display: "flex", gap: 24 }}>
            {["Home", "Consoles", "Favorites", "Downloads"].map((item, i) => (
              <span
                key={item}
                style={{
                  fontSize: 14,
                  fontWeight: i === 1 ? 600 : 400,
                  color: i === 1 ? textPrimary : textSecondary,
                  cursor: "pointer",
                }}
                onClick={() => i === 1 && navigate("/proposal/7")}
              >
                {item}
              </span>
            ))}
          </div>
        </div>
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: "50%",
            background: `linear-gradient(135deg, ${blue}, ${purple})`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 14,
            fontWeight: 700,
            color: "#fff",
          }}
        >
          U
        </div>
      </nav>

      {/* Back to Gallery */}
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "16px 48px 0" }}>
        <a
          href="/"
          style={{
            fontSize: 13,
            color: textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
          onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
        >
          &larr; Back to Gallery
        </a>
      </div>

      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "24px 48px 60px" }}>
        {/* Console Header */}
        <div style={{ marginBottom: 32 }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 10,
              marginBottom: 8,
              fontSize: 13,
              color: textSecondary,
            }}
          >
            <span
              onClick={() => navigate("/proposal/7")}
              style={{ cursor: "pointer", transition: "color 0.2s" }}
              onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
              onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
            >
              Consoles
            </span>
            <span style={{ color: "#444" }}>/</span>
          </div>
          <h1
            style={{
              fontSize: 40,
              fontWeight: 800,
              margin: 0,
              color: textPrimary,
            }}
          >
            {con.name}
          </h1>
          <div
            style={{
              width: 80,
              height: 3,
              borderRadius: 2,
              background: conColor.bg,
              marginTop: 10,
            }}
          />
          <p
            style={{
              fontSize: 15,
              color: textSecondary,
              margin: "10px 0 0 0",
            }}
          >
            {snesGames.length} titles
          </p>
        </div>

        {/* Genre Filter Chips */}
        <div style={{ display: "flex", gap: 10, marginBottom: 28, flexWrap: "wrap" }}>
          <span
            style={{
              padding: "6px 16px",
              borderRadius: 20,
              background: `${gold}22`,
              border: `1px solid ${gold}44`,
              fontSize: 13,
              fontWeight: 600,
              color: gold,
              cursor: "pointer",
            }}
          >
            All
          </span>
          {genres.map((genre, i) => {
            const chipColors = [teal, purple, rose, blue, gold, "#FB923C", "#34D399"];
            const c = chipColors[i % chipColors.length];
            return (
              <span
                key={genre}
                style={{
                  padding: "6px 16px",
                  borderRadius: 20,
                  background: `${c}11`,
                  border: `1px solid ${c}33`,
                  fontSize: 13,
                  fontWeight: 500,
                  color: `${c}cc`,
                  cursor: "pointer",
                  transition: "all 0.2s",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = `${c}22`;
                  e.currentTarget.style.borderColor = `${c}66`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = `${c}11`;
                  e.currentTarget.style.borderColor = `${c}33`;
                }}
              >
                {genre}
              </span>
            );
          })}
        </div>

        {/* Games Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
            gap: 20,
          }}
        >
          {snesGames.map((game) => {
            const genreColor = genreColors[game.genre] || purple;
            return (
              <div
                key={game.id}
                onClick={() => navigate(`../game/${game.id}`)}
                style={{
                  borderRadius: 16,
                  background: card,
                  overflow: "hidden",
                  cursor: "pointer",
                  transition: "all 0.3s ease",
                  boxShadow: "0 2px 8px rgba(0,0,0,0.2)",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = "translateY(-6px)";
                  e.currentTarget.style.boxShadow = `0 12px 40px ${conColor.glow}33`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = "translateY(0)";
                  e.currentTarget.style.boxShadow = "0 2px 8px rgba(0,0,0,0.2)";
                }}
              >
                {/* Cover Art */}
                <div style={{ position: "relative", aspectRatio: "3/4" }}>
                  <img
                    src={game.coverUrl}
                    alt={game.title}
                    style={{
                      width: "100%",
                      height: "100%",
                      objectFit: "cover",
                      display: "block",
                    }}
                  />
                  {/* Favorite heart */}
                  {game.isFavorite && (
                    <div
                      style={{
                        position: "absolute",
                        top: 10,
                        right: 10,
                        width: 28,
                        height: 28,
                        borderRadius: "50%",
                        background: "rgba(0,0,0,0.5)",
                        backdropFilter: "blur(8px)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontSize: 14,
                        color: gold,
                      }}
                    >
                      &#9829;
                    </div>
                  )}
                  {/* Rating badge */}
                  <div
                    style={{
                      position: "absolute",
                      bottom: 10,
                      left: 10,
                      padding: "3px 10px",
                      borderRadius: 10,
                      background: "rgba(0,0,0,0.6)",
                      backdropFilter: "blur(6px)",
                      fontSize: 12,
                      fontWeight: 600,
                      color: gold,
                    }}
                  >
                    {"★".repeat(Math.round(game.rating / 2))}
                  </div>
                </div>

                {/* Info */}
                <div style={{ padding: "14px 16px 18px" }}>
                  <div
                    style={{
                      fontSize: 14,
                      fontWeight: 700,
                      color: "#fff",
                      marginBottom: 6,
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {game.title}
                  </div>
                  <span
                    style={{
                      display: "inline-block",
                      padding: "2px 10px",
                      borderRadius: 8,
                      background: `${genreColor}18`,
                      border: `1px solid ${genreColor}33`,
                      fontSize: 11,
                      fontWeight: 500,
                      color: genreColor,
                    }}
                  >
                    {game.genre}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// Game Detail Page
function GameDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const game = snesGames.find((g) => g.id === id) || snesGames[0];
  const con = consoles.find((c) => c.id === game.consoleId) || consoles[0];
  const conColor = getConsoleColorById(con.id);
  const genreColor = genreColors[game.genre] || purple;

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textPrimary,
      }}
    >
      {/* Top Nav */}
      <nav
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "16px 48px",
          background: "#13152099",
          borderBottom: "1px solid #2a2d3a",
          backdropFilter: "blur(12px)",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 32 }}>
          <span
            style={{
              fontSize: 24,
              fontWeight: 800,
              color: gold,
              letterSpacing: "0.04em",
              cursor: "pointer",
            }}
            onClick={() => navigate("/proposal/7")}
          >
            Spela
          </span>
        </div>
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: "50%",
            background: `linear-gradient(135deg, ${blue}, ${purple})`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 14,
            fontWeight: 700,
            color: "#fff",
          }}
        >
          U
        </div>
      </nav>

      {/* Back to Gallery */}
      <div style={{ maxWidth: 1100, margin: "0 auto", padding: "16px 48px 0" }}>
        <a
          href="/"
          style={{
            fontSize: 13,
            color: textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
          onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
        >
          &larr; Back to Gallery
        </a>
      </div>

      <div style={{ maxWidth: 1100, margin: "0 auto", padding: "24px 48px 60px" }}>
        {/* Breadcrumb */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            marginBottom: 28,
            fontSize: 13,
            color: textSecondary,
          }}
        >
          <span
            onClick={() => navigate("/proposal/7")}
            style={{ cursor: "pointer", transition: "color 0.2s" }}
            onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
            onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
          >
            Consoles
          </span>
          <span style={{ color: "#444" }}>/</span>
          <span
            onClick={() => navigate(`/proposal/7/console/${con.id}`)}
            style={{ cursor: "pointer", transition: "color 0.2s" }}
            onMouseEnter={(e) => (e.currentTarget.style.color = gold)}
            onMouseLeave={(e) => (e.currentTarget.style.color = textSecondary)}
          >
            {con.name}
          </span>
          <span style={{ color: "#444" }}>/</span>
          <span style={{ color: textPrimary, fontWeight: 600 }}>{game.title}</span>
        </div>

        {/* Hero Section */}
        <div style={{ display: "flex", gap: 40, marginBottom: 40 }}>
          {/* Cover Art with magical glow */}
          <div
            style={{
              width: 280,
              flexShrink: 0,
            }}
          >
            <div
              style={{
                borderRadius: 20,
                overflow: "hidden",
                boxShadow: `0 8px 48px ${conColor.glow}44, 0 0 80px ${conColor.glow}22`,
              }}
            >
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "3/4",
                  objectFit: "cover",
                  display: "block",
                }}
              />
            </div>
          </div>

          {/* Info */}
          <div style={{ flex: 1, paddingTop: 8 }}>
            {/* Tags */}
            <div style={{ display: "flex", gap: 10, marginBottom: 16 }}>
              <span
                style={{
                  padding: "5px 16px",
                  borderRadius: 14,
                  background: conColor.bg,
                  fontSize: 13,
                  fontWeight: 600,
                  color: "#fff",
                }}
              >
                {con.name}
              </span>
              <span
                style={{
                  padding: "5px 16px",
                  borderRadius: 14,
                  background: `${genreColor}22`,
                  border: `1px solid ${genreColor}44`,
                  fontSize: 13,
                  fontWeight: 600,
                  color: genreColor,
                }}
              >
                {game.genre}
              </span>
            </div>

            <h1
              style={{
                fontSize: 44,
                fontWeight: 800,
                margin: "0 0 16px 0",
                lineHeight: 1.1,
                color: "#fff",
              }}
            >
              {game.title}
            </h1>

            {/* Gold Stars */}
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 20 }}>
              <div style={{ display: "flex", gap: 2 }}>
                {Array.from({ length: 5 }, (_, i) => (
                  <span
                    key={i}
                    style={{
                      fontSize: 20,
                      color: i < Math.round(game.rating / 2) ? gold : "#3a3d4a",
                    }}
                  >
                    &#9733;
                  </span>
                ))}
              </div>
              <span
                style={{
                  fontSize: 15,
                  fontWeight: 700,
                  color: gold,
                }}
              >
                {game.rating}/10
              </span>
            </div>

            <p
              style={{
                fontSize: 16,
                color: textSecondary,
                lineHeight: 1.7,
                margin: "0 0 24px 0",
              }}
            >
              {game.description}
            </p>

            {/* Metadata mini-cards */}
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr 1fr",
                gap: 12,
                marginBottom: 28,
              }}
            >
              {[
                { label: "Developer", value: game.developer, tint: `${purple}15` },
                { label: "Publisher", value: game.publisher, tint: `${teal}15` },
                { label: "Release", value: game.releaseDate, tint: `${blue}15` },
                {
                  label: "Players",
                  value: `${game.players} player${game.players > 1 ? "s" : ""}`,
                  tint: `${rose}15`,
                },
                { label: "File Size", value: formatFileSize(game.fileSize), tint: `${gold}12` },
                {
                  label: "Play Time",
                  value:
                    game.totalPlayTime > 0
                      ? formatPlayTime(game.totalPlayTime)
                      : "Not played",
                  tint: `${teal}15`,
                },
              ].map((item) => (
                <div
                  key={item.label}
                  style={{
                    padding: "14px 16px",
                    borderRadius: 14,
                    background: item.tint,
                    border: `1px solid ${card}`,
                  }}
                >
                  <div
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      textTransform: "uppercase",
                      letterSpacing: "0.06em",
                      color: textSecondary,
                      marginBottom: 4,
                    }}
                  >
                    {item.label}
                  </div>
                  <div
                    style={{
                      fontSize: 14,
                      fontWeight: 600,
                      color: textPrimary,
                    }}
                  >
                    {item.value}
                  </div>
                </div>
              ))}
            </div>

            {/* Play Button */}
            <div style={{ display: "flex", gap: 14 }}>
              <button
                style={{
                  padding: "16px 40px",
                  borderRadius: 28,
                  border: "none",
                  background: `linear-gradient(135deg, ${blue}, ${purple})`,
                  color: "#fff",
                  fontSize: 16,
                  fontWeight: 700,
                  cursor: "pointer",
                  transition: "all 0.3s",
                  boxShadow: `0 4px 20px ${blue}55`,
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.transform = "scale(1.05)";
                  e.currentTarget.style.boxShadow = `0 8px 30px ${blue}77`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = "scale(1)";
                  e.currentTarget.style.boxShadow = `0 4px 20px ${blue}55`;
                }}
              >
                <span style={{ fontSize: 16 }}>&#9654;</span>
                Play Now
              </button>
              <button
                style={{
                  padding: "16px 24px",
                  borderRadius: 28,
                  border: `2px solid ${gold}66`,
                  background: "transparent",
                  color: gold,
                  fontSize: 15,
                  fontWeight: 600,
                  cursor: "pointer",
                  transition: "all 0.3s",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = gold;
                  e.currentTarget.style.background = `${gold}11`;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = `${gold}66`;
                  e.currentTarget.style.background = "transparent";
                }}
              >
                {game.isFavorite ? "&#9829; Favorited" : "&#9825; Favorite"}
              </button>
            </div>
          </div>
        </div>

        {/* Screenshots */}
        {game.screenshotUrls.length > 0 && (
          <div>
            <h2
              style={{
                fontSize: 20,
                fontWeight: 700,
                margin: "0 0 16px 0",
                color: textPrimary,
              }}
            >
              Screenshots
            </h2>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${Math.min(game.screenshotUrls.length, 2)}, 1fr)`,
                gap: 16,
              }}
            >
              {game.screenshotUrls.map((url, i) => (
                <div
                  key={i}
                  style={{
                    borderRadius: 16,
                    overflow: "hidden",
                    boxShadow: `0 4px 20px ${conColor.glow}22`,
                    border: `1px solid ${conColor.glow}22`,
                  }}
                >
                  <img
                    src={url}
                    alt={`Screenshot ${i + 1}`}
                    style={{
                      width: "100%",
                      aspectRatio: "16/9",
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
    </div>
  );
}

// Main Export
export function Proposal7() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<GamesPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
