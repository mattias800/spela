import { useState } from "react";
import { Routes, Route, useNavigate, useParams, Link } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";

/* ------------------------------------------------------------------ */
/*  Xbox Game Pass Inspired - Proposal 2                               */
/*  Dark, bold, confident. Green accents, clean sans-serif, glows.     */
/* ------------------------------------------------------------------ */

const colors = {
  bg: "#0e0e0e",
  surface: "#1a1a1a",
  card: "#242424",
  cardHover: "#2c2c2c",
  green: "#107c10",
  greenLight: "#1faa1f",
  greenGlow: "rgba(16,124,16,0.3)",
  text: "#ffffff",
  textSecondary: "#a0a0a0",
  textMuted: "#666666",
  border: "rgba(255,255,255,0.06)",
};

const consoleIcons: Record<string, string> = {
  "1": "#e53e3e",
  "2": "#805ad5",
  "3": "#38a169",
  "4": "#667eea",
  "5": "#48bb78",
  "6": "#2b6cb0",
  "7": "#a0aec0",
  "8": "#ecc94b",
  "9": "#319795",
  "10": "#f6ad55",
};

/* ---- Top Nav Bar ---- */
function TopNav() {
  const navigate = useNavigate();

  return (
    <div
      style={{
        position: "sticky",
        top: 0,
        zIndex: 100,
        background: "rgba(14,14,14,0.95)",
        backdropFilter: "blur(12px)",
        borderBottom: `1px solid ${colors.border}`,
        padding: "0 40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        height: 60,
      }}
    >
      <div
        style={{ cursor: "pointer", display: "flex", alignItems: "center", gap: 14 }}
        onClick={() => navigate("/")}
      >
        <span
          style={{
            fontSize: 22,
            fontWeight: 800,
            color: colors.text,
            letterSpacing: "0.06em",
            fontFamily: "'Inter', system-ui, sans-serif",
          }}
        >
          SPELA
        </span>
      </div>
      <div style={{ display: "flex", gap: 28, alignItems: "center" }}>
        {["Home", "My Library", "Browse"].map((label, i) => (
          <span
            key={label}
            style={{
              fontSize: 14,
              fontWeight: i === 0 ? 700 : 400,
              color: i === 0 ? colors.text : colors.textSecondary,
              cursor: "pointer",
              transition: "color 0.2s",
            }}
            onClick={() => {
              if (label === "Home") navigate("/");
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = colors.text;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = i === 0 ? colors.text : colors.textSecondary;
            }}
          >
            {label}
          </span>
        ))}
        {/* Search */}
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: "50%",
            background: colors.card,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
            fontSize: 15,
            color: colors.textSecondary,
          }}
        >
          {"\u2315"}
        </div>
        {/* Avatar */}
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: "50%",
            background: colors.green,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: 13,
            fontWeight: 700,
          }}
        >
          U
        </div>
      </div>
    </div>
  );
}

/* ---- Console Card (dark) ---- */
function ConsoleCard({
  console: c,
  onClick,
}: {
  console: (typeof consoles)[0];
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  const iconColor = consoleIcons[c.id] || colors.green;

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: colors.surface,
        borderRadius: 12,
        padding: "22px 24px",
        cursor: "pointer",
        transition: "all 0.25s ease",
        transform: hovered ? "translateY(-2px)" : "none",
        borderBottom: hovered ? `2px solid ${colors.green}` : "2px solid transparent",
        boxShadow: hovered
          ? `0 8px 24px rgba(0,0,0,0.4), 0 0 0 1px ${colors.border}`
          : `0 2px 8px rgba(0,0,0,0.2)`,
        display: "flex",
        alignItems: "center",
        gap: 16,
      }}
    >
      {/* Colored circle icon */}
      <div
        style={{
          width: 44,
          height: 44,
          borderRadius: "50%",
          background: `${iconColor}20`,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
      >
        <div
          style={{
            width: 18,
            height: 18,
            borderRadius: "50%",
            background: iconColor,
          }}
        />
      </div>
      <div style={{ flex: 1 }}>
        <div
          style={{
            fontSize: 16,
            fontWeight: 700,
            color: colors.text,
            marginBottom: 3,
          }}
        >
          {c.name}
        </div>
        <div style={{ fontSize: 13, color: colors.textSecondary }}>
          {c.gameCount} titles
        </div>
      </div>
      {/* Arrow */}
      <span
        style={{
          fontSize: 18,
          color: hovered ? colors.green : colors.textMuted,
          transition: "all 0.2s",
          transform: hovered ? "translateX(4px)" : "none",
        }}
      >
        {"\u203A"}
      </span>
    </div>
  );
}

/* ---- Game Card (dark with green glow) ---- */
function GameCard({
  game,
  onClick,
  large,
}: {
  game: (typeof snesGames)[0];
  onClick: () => void;
  large?: boolean;
}) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: colors.surface,
        borderRadius: 12,
        overflow: "hidden",
        cursor: "pointer",
        transition: "all 0.3s ease",
        transform: hovered ? "scale(1.03)" : "scale(1)",
        boxShadow: hovered
          ? `0 12px 32px rgba(0,0,0,0.5), 0 0 20px ${colors.greenGlow}`
          : "0 2px 8px rgba(0,0,0,0.3)",
      }}
    >
      <img
        src={game.coverUrl}
        alt={game.title}
        style={{
          width: "100%",
          aspectRatio: large ? "16/10" : "3/4",
          objectFit: "cover",
          display: "block",
        }}
      />
      <div style={{ padding: large ? "16px 20px" : "12px 14px" }}>
        <div
          style={{
            fontSize: large ? 18 : 14,
            fontWeight: 700,
            color: colors.text,
            marginBottom: 4,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {game.title}
        </div>
        <div
          style={{
            fontSize: large ? 13 : 12,
            color: colors.green,
            fontWeight: 600,
          }}
        >
          {game.genre}
        </div>
        {large && game.totalPlayTime > 0 && (
          <div style={{ fontSize: 12, color: colors.textSecondary, marginTop: 4 }}>
            {formatPlayTime(game.totalPlayTime)} played
          </div>
        )}
      </div>
    </div>
  );
}

/* ---- Horizontal Scroll Row ---- */
function ScrollRow({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 40 }}>
      <h2
        style={{
          fontSize: 18,
          fontWeight: 700,
          color: colors.text,
          margin: "0 0 16px 0",
        }}
      >
        {title}
      </h2>
      <div
        style={{
          display: "flex",
          gap: 14,
          overflowX: "auto",
          scrollbarWidth: "none",
          paddingBottom: 8,
        }}
      >
        {children}
      </div>
    </div>
  );
}

/* ---- Consoles Page ---- */
function ConsolesPage() {
  const navigate = useNavigate();
  const featured = consoles[0];

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
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} Back to Gallery
        </Link>
      </div>

      {/* Hero Section */}
      <div
        style={{
          margin: "24px 40px 40px",
          borderRadius: 16,
          padding: "56px 48px",
          background: `linear-gradient(135deg, ${colors.green} 0%, #0a4a0a 40%, ${colors.bg} 100%)`,
          position: "relative",
          overflow: "hidden",
          cursor: "pointer",
        }}
        onClick={() => navigate(`console/${featured.id}`)}
      >
        {/* Decorative glow */}
        <div
          style={{
            position: "absolute",
            top: -60,
            right: -40,
            width: 300,
            height: 300,
            borderRadius: "50%",
            background: `radial-gradient(circle, ${colors.greenGlow} 0%, transparent 70%)`,
          }}
        />
        <div style={{ position: "relative", zIndex: 1 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 700,
              color: colors.greenLight,
              textTransform: "uppercase",
              letterSpacing: "0.15em",
              marginBottom: 14,
            }}
          >
            Jump back in
          </div>
          <div
            style={{
              fontSize: 44,
              fontWeight: 900,
              color: colors.text,
              letterSpacing: "-0.02em",
              lineHeight: 1.1,
              marginBottom: 12,
            }}
          >
            {featured.name}
          </div>
          <div
            style={{
              fontSize: 16,
              color: "rgba(255,255,255,0.6)",
              marginBottom: 24,
            }}
          >
            {featured.gameCount} titles available
          </div>
          <div
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 8,
              padding: "12px 28px",
              background: colors.green,
              borderRadius: 8,
              fontSize: 15,
              fontWeight: 700,
              color: colors.text,
              transition: "background 0.2s",
            }}
          >
            Browse Library {"\u2192"}
          </div>
        </div>
      </div>

      {/* All Platforms */}
      <div style={{ padding: "0 40px 60px" }}>
        <h2
          style={{
            fontSize: 22,
            fontWeight: 800,
            color: colors.text,
            margin: "0 0 20px 0",
          }}
        >
          All Platforms
        </h2>

        {/* Horizontal scrolling row of console cards */}
        <div
          style={{
            display: "flex",
            gap: 14,
            overflowX: "auto",
            scrollbarWidth: "none",
            paddingBottom: 8,
          }}
        >
          {consoles.map((c) => (
            <div key={c.id} style={{ minWidth: 300, flexShrink: 0 }}>
              <ConsoleCard
                console={c}
                onClick={() => navigate(`console/${c.id}`)}
              />
            </div>
          ))}
        </div>

        {/* Nintendo row */}
        <div style={{ marginTop: 40 }}>
          <h3
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: colors.text,
              margin: "0 0 16px 0",
            }}
          >
            Nintendo
          </h3>
          <div
            style={{
              display: "flex",
              gap: 14,
              overflowX: "auto",
              scrollbarWidth: "none",
              paddingBottom: 8,
            }}
          >
            {consoles
              .filter((c) =>
                ["NES", "SNES", "GB", "GBA", "N64", "GBC"].includes(c.abbreviation)
              )
              .map((c) => (
                <div key={c.id} style={{ minWidth: 300, flexShrink: 0 }}>
                  <ConsoleCard
                    console={c}
                    onClick={() => navigate(`console/${c.id}`)}
                  />
                </div>
              ))}
          </div>
        </div>

        {/* Other Platforms */}
        <div style={{ marginTop: 40 }}>
          <h3
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: colors.text,
              margin: "0 0 16px 0",
            }}
          >
            More Platforms
          </h3>
          <div
            style={{
              display: "flex",
              gap: 14,
              overflowX: "auto",
              scrollbarWidth: "none",
              paddingBottom: 8,
            }}
          >
            {consoles
              .filter((c) =>
                ["Genesis", "PSX", "NeoGeo", "Arcade"].includes(c.abbreviation)
              )
              .map((c) => (
                <div key={c.id} style={{ minWidth: 300, flexShrink: 0 }}>
                  <ConsoleCard
                    console={c}
                    onClick={() => navigate(`console/${c.id}`)}
                  />
                </div>
              ))}
          </div>
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

  const recentlyPlayed = snesGames
    .filter((g) => g.lastPlayedAt)
    .sort(
      (a, b) =>
        new Date(b.lastPlayedAt!).getTime() - new Date(a.lastPlayedAt!).getTime()
    )
    .slice(0, 4);

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
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
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
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {"\u2190"} All Consoles
        </span>
      </div>

      {/* Title */}
      <div style={{ marginBottom: 32 }}>
        <h1
          style={{
            fontSize: 36,
            fontWeight: 900,
            color: colors.text,
            margin: "0 0 6px 0",
            letterSpacing: "-0.02em",
          }}
        >
          {c.name}
        </h1>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 16,
          }}
        >
          <span style={{ fontSize: 14, color: colors.textSecondary }}>
            {snesGames.length} games
          </span>
          {/* Sort by (visual only) */}
          <div
            style={{
              padding: "6px 16px",
              background: colors.card,
              borderRadius: 8,
              fontSize: 13,
              color: colors.textSecondary,
              display: "flex",
              alignItems: "center",
              gap: 6,
              cursor: "pointer",
            }}
          >
            Sort by: Name {"\u25BE"}
          </div>
        </div>
      </div>

      {/* Recently Played */}
      {recentlyPlayed.length > 0 && (
        <ScrollRow title="Recently Played">
          {recentlyPlayed.map((game) => (
            <div key={game.id} style={{ minWidth: 260, flexShrink: 0 }}>
              <GameCard
                game={game}
                onClick={() => navigate(`../game/${game.id}`)}
                large
              />
            </div>
          ))}
        </ScrollRow>
      )}

      {/* All Games Grid */}
      <h2
        style={{
          fontSize: 18,
          fontWeight: 700,
          color: colors.text,
          margin: "0 0 16px 0",
        }}
      >
        All Games
      </h2>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
          gap: 16,
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

  const ratingPercent = (game.rating / 10) * 100;

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
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
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
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          Home
        </span>
        <span style={{ color: colors.textMuted }}>/</span>
        <span
          style={{ cursor: "pointer", transition: "color 0.2s" }}
          onClick={() => navigate(`../console/${game.consoleId}`)}
          onMouseEnter={(e) => (e.currentTarget.style.color = colors.text)}
          onMouseLeave={(e) => (e.currentTarget.style.color = colors.textSecondary)}
        >
          {game.consoleName}
        </span>
        <span style={{ color: colors.textMuted }}>/</span>
        <span style={{ color: colors.text }}>{game.title}</span>
      </div>

      {/* Main layout */}
      <div style={{ display: "flex", gap: 48 }}>
        {/* Cover art */}
        <div style={{ flexShrink: 0 }}>
          <div
            style={{
              width: 300,
              borderRadius: 12,
              overflow: "hidden",
              boxShadow: "0 12px 40px rgba(0,0,0,0.5)",
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
          <h1
            style={{
              fontSize: 36,
              fontWeight: 900,
              color: colors.text,
              margin: "0 0 16px 0",
              letterSpacing: "-0.02em",
            }}
          >
            {game.title}
          </h1>

          {/* Green Play button */}
          <button
            style={{
              display: "flex",
              alignItems: "center",
              gap: 10,
              padding: "16px 40px",
              background: colors.green,
              color: colors.text,
              border: "none",
              borderRadius: 8,
              fontSize: 18,
              fontWeight: 800,
              cursor: "pointer",
              letterSpacing: "0.02em",
              boxShadow: `0 6px 24px ${colors.greenGlow}`,
              transition: "all 0.2s ease",
              marginBottom: 28,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = colors.greenLight;
              e.currentTarget.style.transform = "scale(1.03)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = colors.green;
              e.currentTarget.style.transform = "scale(1)";
            }}
          >
            {"\u25B6"} Play
          </button>

          {/* Metadata panels */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 12,
              marginBottom: 24,
            }}
          >
            {[
              { label: "Genre", value: game.genre },
              { label: "Players", value: `${game.players} Player${game.players > 1 ? "s" : ""}` },
              { label: "Developer", value: game.developer },
              { label: "Publisher", value: game.publisher },
              { label: "Released", value: game.releaseDate },
              { label: "Size", value: formatFileSize(game.fileSize) },
            ].map((item) => (
              <div
                key={item.label}
                style={{
                  background: colors.card,
                  borderRadius: 8,
                  padding: "12px 16px",
                }}
              >
                <div
                  style={{
                    fontSize: 11,
                    color: colors.green,
                    textTransform: "uppercase",
                    letterSpacing: "0.1em",
                    fontWeight: 700,
                    marginBottom: 4,
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

          {/* Rating as green progress bar */}
          <div
            style={{
              background: colors.card,
              borderRadius: 8,
              padding: "14px 16px",
              marginBottom: 24,
            }}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: 8,
              }}
            >
              <span
                style={{
                  fontSize: 11,
                  color: colors.green,
                  textTransform: "uppercase",
                  letterSpacing: "0.1em",
                  fontWeight: 700,
                }}
              >
                Rating
              </span>
              <span
                style={{
                  fontSize: 16,
                  fontWeight: 800,
                  color: colors.text,
                }}
              >
                {game.rating}/10
              </span>
            </div>
            <div
              style={{
                height: 6,
                borderRadius: 3,
                background: "rgba(255,255,255,0.08)",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${ratingPercent}%`,
                  height: "100%",
                  borderRadius: 3,
                  background: `linear-gradient(90deg, ${colors.green}, ${colors.greenLight})`,
                  boxShadow: `0 0 8px ${colors.greenGlow}`,
                  transition: "width 0.5s ease",
                }}
              />
            </div>
          </div>

          {/* Play stats */}
          {(game.totalPlayTime > 0 || game.lastPlayedAt) && (
            <div
              style={{
                display: "flex",
                gap: 24,
                marginBottom: 24,
              }}
            >
              {game.totalPlayTime > 0 && (
                <div
                  style={{
                    background: colors.card,
                    borderRadius: 8,
                    padding: "12px 16px",
                    flex: 1,
                  }}
                >
                  <div
                    style={{
                      fontSize: 11,
                      color: colors.green,
                      textTransform: "uppercase",
                      letterSpacing: "0.1em",
                      fontWeight: 700,
                      marginBottom: 4,
                    }}
                  >
                    Play Time
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: colors.text }}>
                    {formatPlayTime(game.totalPlayTime)}
                  </div>
                </div>
              )}
              {game.lastPlayedAt && (
                <div
                  style={{
                    background: colors.card,
                    borderRadius: 8,
                    padding: "12px 16px",
                    flex: 1,
                  }}
                >
                  <div
                    style={{
                      fontSize: 11,
                      color: colors.green,
                      textTransform: "uppercase",
                      letterSpacing: "0.1em",
                      fontWeight: 700,
                      marginBottom: 4,
                    }}
                  >
                    Last Played
                  </div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: colors.text }}>
                    {new Date(game.lastPlayedAt).toLocaleDateString()}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Description */}
          <p
            style={{
              fontSize: 15,
              color: colors.textSecondary,
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
                  borderRadius: 10,
                  border: `1px solid ${colors.border}`,
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
export function Proposal2() {
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
