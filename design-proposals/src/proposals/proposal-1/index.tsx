import { useState } from "react";
import { Routes, Route, useNavigate, useParams, Link } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";

/* ------------------------------------------------------------------ */
/*  Nintendo Switch / eShop Inspired - Proposal 1                      */
/*  Light, colorful, friendly, joyful. Rounded cards, neon pops.       */
/* ------------------------------------------------------------------ */

const colors = {
  bg: "#FFFFFF",
  surface: "#FFFFFF",
  primary: "#E60012",
  primaryHover: "#CC0010",
  text: "#1a1a1a",
  textSecondary: "#666666",
  neonBlue: "#009DC4",
  neonRed: "#FF3C28",
  neonYellow: "#D4CC00",
  neonGreen: "#00B800",
};

const accentColors = [
  colors.neonBlue,
  colors.neonRed,
  colors.neonGreen,
  "#ff7b00",
  colors.primary,
  "#8b5cf6",
  colors.neonBlue,
  "#ff3c28",
  colors.neonGreen,
  "#ff7b00",
];

function getAccentColor(index: number): string {
  return accentColors[index % accentColors.length];
}

/* ---- Top Navigation Bar ---- */
function TopNav() {
  const navigate = useNavigate();

  return (
    <div
      style={{
        position: "sticky",
        top: 0,
        zIndex: 100,
        background: colors.surface,
        borderBottom: "1px solid #eee",
        padding: "0 40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        height: 64,
      }}
    >
      <div
        style={{ cursor: "pointer", display: "flex", alignItems: "center", gap: 12 }}
        onClick={() => navigate("/")}
      >
        <span
          style={{
            fontSize: 26,
            fontWeight: 800,
            color: colors.primary,
            letterSpacing: "0.04em",
            fontFamily: "'Inter', system-ui, sans-serif",
          }}
        >
          Spela
        </span>
      </div>
      <div style={{ display: "flex", gap: 32, alignItems: "center" }}>
        {["Home", "Favorites", "Recent"].map((label, i) => (
          <span
            key={label}
            style={{
              fontSize: 15,
              fontWeight: i === 0 ? 700 : 500,
              color: i === 0 ? colors.text : colors.textSecondary,
              cursor: "pointer",
              transition: "color 0.2s",
            }}
            onClick={() => {
              if (label === "Home") navigate("/");
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = colors.primary;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = i === 0 ? colors.text : colors.textSecondary;
            }}
          >
            {label}
          </span>
        ))}
        {/* Search icon */}
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            background: "#f0f0f0",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
            fontSize: 16,
          }}
        >
          {"\u2315"}
        </div>
        {/* User avatar */}
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            background: colors.primary,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: 14,
            fontWeight: 700,
          }}
        >
          U
        </div>
      </div>
    </div>
  );
}

/* ---- Console Card with Colored Left Accent ---- */
function ConsoleCard({
  console: c,
  index,
  onClick,
}: {
  console: (typeof consoles)[0];
  index: number;
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  const accent = getAccentColor(index);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: colors.surface,
        borderRadius: 16,
        cursor: "pointer",
        overflow: "hidden",
        display: "flex",
        alignItems: "center",
        transition: "all 0.25s ease",
        transform: hovered ? "translateY(-3px)" : "none",
        boxShadow: hovered
          ? "0 8px 24px rgba(0,0,0,0.1)"
          : "0 2px 8px rgba(0,0,0,0.04)",
      }}
    >
      {/* Left accent strip */}
      <div
        style={{
          width: 8,
          alignSelf: "stretch",
          background: accent,
          flexShrink: 0,
          borderRadius: "16px 0 0 16px",
        }}
      />
      <div
        style={{
          flex: 1,
          padding: "20px 24px",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div>
          <div
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: colors.text,
              marginBottom: 4,
            }}
          >
            {c.name}
          </div>
          <span
            style={{
              display: "inline-block",
              padding: "3px 10px",
              borderRadius: 12,
              background: `${accent}18`,
              color: accent,
              fontSize: 12,
              fontWeight: 700,
            }}
          >
            {c.gameCount} games
          </span>
        </div>
        <span
          style={{
            fontSize: 20,
            color: hovered ? accent : "#ccc",
            transition: "color 0.2s, transform 0.2s",
            transform: hovered ? "translateX(4px)" : "none",
          }}
        >
          {"\u203A"}
        </span>
      </div>
    </div>
  );
}

/* ---- Game Card ---- */
function GameCard({
  game,
  onClick,
}: {
  game: (typeof snesGames)[0];
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: colors.surface,
        borderRadius: 16,
        overflow: "hidden",
        cursor: "pointer",
        transition: "all 0.25s ease",
        transform: hovered ? "translateY(-4px)" : "none",
        boxShadow: hovered
          ? "0 8px 28px rgba(0,0,0,0.12)"
          : "0 2px 8px rgba(0,0,0,0.04)",
        position: "relative",
      }}
    >
      <div style={{ position: "relative" }}>
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
        {/* Favorite heart */}
        {(game.isFavorite || hovered) && (
          <div
            style={{
              position: "absolute",
              top: 10,
              right: 10,
              width: 30,
              height: 30,
              borderRadius: "50%",
              background: game.isFavorite ? colors.primary : "rgba(255,255,255,0.85)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 14,
              color: game.isFavorite ? "#fff" : "#ccc",
              transition: "all 0.2s",
            }}
          >
            {"\u2665"}
          </div>
        )}
      </div>
      <div style={{ padding: "12px 14px 14px" }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: colors.text,
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
            padding: "2px 8px",
            borderRadius: 8,
            background: "#f0f0f0",
            color: colors.textSecondary,
            fontSize: 11,
            fontWeight: 600,
          }}
        >
          {game.genre}
        </span>
      </div>
    </div>
  );
}

/* ---- Consoles Page ---- */
function ConsolesPage() {
  const navigate = useNavigate();

  return (
    <div>
      {/* Back to Gallery */}
      <div style={{ padding: "16px 40px 0" }}>
        <Link
          to="/"
          style={{
            fontSize: 13,
            color: colors.textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} Back to Gallery
        </Link>
      </div>

      {/* Hero Banner */}
      <div
        style={{
          margin: "24px 40px 36px",
          borderRadius: 20,
          padding: "48px 40px",
          background: `linear-gradient(135deg, ${colors.neonBlue}, ${colors.neonRed} 50%, ${colors.primary})`,
          position: "relative",
          overflow: "hidden",
        }}
      >
        {/* Decorative shapes */}
        <div
          style={{
            position: "absolute",
            top: -30,
            right: -20,
            width: 180,
            height: 180,
            borderRadius: "50%",
            background: "rgba(255,255,255,0.12)",
          }}
        />
        <div
          style={{
            position: "absolute",
            bottom: -40,
            right: 120,
            width: 120,
            height: 120,
            borderRadius: "50%",
            background: "rgba(255,255,255,0.08)",
          }}
        />
        <div style={{ position: "relative", zIndex: 1 }}>
          <div
            style={{
              fontSize: 36,
              fontWeight: 800,
              color: "#ffffff",
              marginBottom: 8,
              letterSpacing: "-0.01em",
            }}
          >
            Welcome to your game library!
          </div>
          <div
            style={{
              fontSize: 16,
              color: "rgba(255,255,255,0.85)",
              fontWeight: 500,
            }}
          >
            {consoles.length} consoles and hundreds of classic titles to explore
          </div>
        </div>
      </div>

      {/* Section Title */}
      <div style={{ padding: "0 40px", marginBottom: 20 }}>
        <h2
          style={{
            fontSize: 22,
            fontWeight: 800,
            color: colors.text,
            margin: 0,
          }}
        >
          Browse by Platform
        </h2>
      </div>

      {/* Console Grid */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(320, 1fr))",
          gap: 14,
          padding: "0 40px 60px",
          maxWidth: 1100,
        }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: 14,
          }}
        >
          {consoles.map((c, i) => (
            <ConsoleCard
              key={c.id}
              console={c}
              index={i}
              onClick={() => navigate(`console/${c.id}`)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/* ---- Console Detail / Games Page ---- */
function ConsoleDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const c = consoles.find((con) => con.id === id);

  if (!c)
    return (
      <div style={{ color: colors.text, padding: 48 }}>Console not found</div>
    );

  const accent = getAccentColor(consoles.indexOf(c));

  return (
    <div style={{ padding: "0 40px 60px" }}>
      {/* Back to Gallery */}
      <div style={{ paddingTop: 16, marginBottom: 8 }}>
        <Link
          to="/"
          style={{
            fontSize: 13,
            color: colors.textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} Back to Gallery
        </Link>
      </div>

      {/* Back link */}
      <div style={{ marginBottom: 20 }}>
        <span
          style={{
            fontSize: 14,
            color: colors.textSecondary,
            cursor: "pointer",
            transition: "color 0.2s",
          }}
          onClick={() => navigate("/")}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} All Consoles
        </span>
      </div>

      {/* Title with accent underline */}
      <div style={{ marginBottom: 24 }}>
        <h1
          style={{
            fontSize: 32,
            fontWeight: 800,
            color: colors.text,
            margin: "0 0 8px 0",
            display: "inline-block",
          }}
        >
          {c.name}
          <div
            style={{
              height: 4,
              borderRadius: 2,
              background: accent,
              marginTop: 6,
            }}
          />
        </h1>
        <div style={{ marginTop: 8 }}>
          <span
            style={{
              display: "inline-block",
              padding: "4px 14px",
              borderRadius: 14,
              background: `${accent}18`,
              color: accent,
              fontSize: 13,
              fontWeight: 700,
            }}
          >
            {snesGames.length} games
          </span>
        </div>
      </div>

      {/* Games Grid */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
          gap: 18,
          maxWidth: 1100,
        }}
      >
        {snesGames.map((game) => (
          <GameCard
            key={game.id}
            game={game}
            onClick={() => navigate(`../game/${game.id}`)}
          />
        ))}
      </div>
    </div>
  );
}

/* ---- Game Detail Page ---- */
function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id);

  if (!game)
    return (
      <div style={{ color: colors.text, padding: 48 }}>Game not found</div>
    );

  return (
    <div style={{ padding: "0 40px 60px", maxWidth: 1000, margin: "0 auto" }}>
      {/* Back to Gallery */}
      <div style={{ paddingTop: 16, marginBottom: 8 }}>
        <Link
          to="/"
          style={{
            fontSize: 13,
            color: colors.textSecondary,
            textDecoration: "none",
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} Back to Gallery
        </Link>
      </div>

      {/* Breadcrumb */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          fontSize: 13,
          color: colors.textSecondary,
          marginBottom: 32,
        }}
      >
        <span
          style={{ cursor: "pointer", transition: "color 0.2s" }}
          onClick={() => navigate("/")}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          Home
        </span>
        <span style={{ color: "#ccc" }}>/</span>
        <span
          style={{ cursor: "pointer", transition: "color 0.2s" }}
          onClick={() => navigate(`../console/${game.consoleId}`)}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.primary)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {game.consoleName}
        </span>
        <span style={{ color: "#ccc" }}>/</span>
        <span style={{ color: colors.text, fontWeight: 600 }}>{game.title}</span>
      </div>

      {/* Main layout: cover left, info right */}
      <div style={{ display: "flex", gap: 48 }}>
        {/* Cover art */}
        <div style={{ flexShrink: 0 }}>
          <div
            style={{
              width: 280,
              borderRadius: 16,
              overflow: "hidden",
              boxShadow: "0 8px 32px rgba(0,0,0,0.1)",
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
        <div style={{ flex: 1 }}>
          {/* Title */}
          <div style={{ marginBottom: 12 }}>
            <h1
              style={{
                fontSize: 32,
                fontWeight: 800,
                color: colors.text,
                margin: "0 0 8px 0",
              }}
            >
              {game.title}
            </h1>
            {/* Console badge */}
            <span
              style={{
                display: "inline-block",
                padding: "4px 12px",
                borderRadius: 10,
                background: `${colors.neonBlue}18`,
                color: colors.neonBlue,
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              {game.consoleName}
            </span>
          </div>

          {/* Metadata grid */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr 1fr",
              gap: "12px 24px",
              marginBottom: 24,
              padding: "16px 0",
              borderBottom: "1px solid #eee",
            }}
          >
            {[
              { label: "Genre", value: game.genre },
              { label: "Rating", value: `${game.rating}/10` },
              { label: "Players", value: `${game.players}` },
              { label: "Released", value: game.releaseDate },
              { label: "Developer", value: game.developer },
              { label: "Size", value: formatFileSize(game.fileSize) },
            ].map((item) => (
              <div key={item.label}>
                <div
                  style={{
                    fontSize: 11,
                    color: colors.textSecondary,
                    textTransform: "uppercase",
                    letterSpacing: "0.08em",
                    marginBottom: 2,
                  }}
                >
                  {item.label}
                </div>
                <div
                  style={{
                    fontSize: 14,
                    fontWeight: 600,
                    color: colors.text,
                  }}
                >
                  {item.value}
                </div>
              </div>
            ))}
          </div>

          {/* Play time stats */}
          {(game.totalPlayTime > 0 || game.lastPlayedAt) && (
            <div
              style={{
                display: "flex",
                gap: 28,
                marginBottom: 24,
              }}
            >
              {game.totalPlayTime > 0 && (
                <div>
                  <div
                    style={{
                      fontSize: 11,
                      color: colors.textSecondary,
                      textTransform: "uppercase",
                      letterSpacing: "0.08em",
                      marginBottom: 2,
                    }}
                  >
                    Play Time
                  </div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: colors.text }}>
                    {formatPlayTime(game.totalPlayTime)}
                  </div>
                </div>
              )}
              {game.lastPlayedAt && (
                <div>
                  <div
                    style={{
                      fontSize: 11,
                      color: colors.textSecondary,
                      textTransform: "uppercase",
                      letterSpacing: "0.08em",
                      marginBottom: 2,
                    }}
                  >
                    Last Played
                  </div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: colors.text }}>
                    {new Date(game.lastPlayedAt).toLocaleDateString()}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Buttons */}
          <div style={{ display: "flex", gap: 12, marginBottom: 28 }}>
            <button
              style={{
                padding: "14px 36px",
                background: colors.primary,
                color: "#fff",
                border: "none",
                borderRadius: 14,
                fontSize: 16,
                fontWeight: 700,
                cursor: "pointer",
                transition: "all 0.2s",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = colors.primaryHover;
                e.currentTarget.style.transform = "scale(1.03)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = colors.primary;
                e.currentTarget.style.transform = "scale(1)";
              }}
            >
              {"\u25B6"} Play
            </button>
            <button
              style={{
                padding: "14px 28px",
                background: "transparent",
                color: colors.primary,
                border: `2px solid ${colors.primary}`,
                borderRadius: 14,
                fontSize: 16,
                fontWeight: 700,
                cursor: "pointer",
                transition: "all 0.2s",
                display: "flex",
                alignItems: "center",
                gap: 6,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = `${colors.primary}10`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "transparent";
              }}
            >
              {"\u2665"} Favorite
            </button>
          </div>

          {/* Description */}
          <p
            style={{
              fontSize: 15,
              color: colors.text,
              lineHeight: 1.7,
              margin: 0,
            }}
          >
            {game.description}
          </p>
        </div>
      </div>

      {/* Screenshots */}
      {game.screenshotUrls.length > 0 && (
        <div style={{ marginTop: 40 }}>
          <h3
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: colors.text,
              marginBottom: 14,
            }}
          >
            Screenshots
          </h3>
          <div
            style={{
              display: "flex",
              gap: 12,
              overflowX: "auto",
              scrollbarWidth: "none",
            }}
          >
            {game.screenshotUrls.map((url, i) => (
              <img
                key={i}
                src={url}
                alt={`Screenshot ${i + 1}`}
                style={{
                  height: 160,
                  borderRadius: 14,
                  border: "1px solid #eee",
                  flexShrink: 0,
                }}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ---- Main Layout ---- */
export function Proposal1() {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: colors.bg,
        color: colors.text,
        fontFamily: "'Inter', system-ui, -apple-system, sans-serif",
      }}
    >
      <TopNav />
      <Routes>
        <Route path="/" element={<ConsolesPage />} />
        <Route path="console/:id" element={<ConsoleDetailPage />} />
        <Route path="game/:id" element={<GameDetailPage />} />
      </Routes>
    </div>
  );
}
