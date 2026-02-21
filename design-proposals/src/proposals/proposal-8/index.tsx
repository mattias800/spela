import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import { consoles, snesGames, formatPlayTime, formatFileSize } from "@/mock-data";
import type { Game } from "@/mock-data";
import { useState } from "react";

// Spotify-inspired Dark Theme
const bg = "#121212";
const surfaceDark = "#181818";
const surfaceElevated = "#282828";
const green = "#1db954";
const textWhite = "#ffffff";
const textGray = "#b3b3b3";
const hoverSurface = "#2a2a2a";
const sidebarBg = "#000000";
const font = "'Inter', system-ui, -apple-system, sans-serif";

const consoleDots: Record<string, string> = {
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

// Sidebar component
function Sidebar({
  activePage,
  onNavigateHome,
}: {
  activePage: string;
  onNavigateHome: () => void;
}) {
  const navigate = useNavigate();

  return (
    <div
      style={{
        width: 240,
        flexShrink: 0,
        background: sidebarBg,
        height: "100vh",
        position: "fixed",
        left: 0,
        top: 0,
        padding: "24px 0",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
      }}
    >
      {/* Logo */}
      <div style={{ padding: "0 24px", marginBottom: 28 }}>
        <span
          style={{
            fontSize: 22,
            fontWeight: 800,
            color: textWhite,
            letterSpacing: "-0.02em",
            cursor: "pointer",
          }}
          onClick={onNavigateHome}
        >
          Spela
        </span>
      </div>

      {/* Nav Links */}
      <div style={{ padding: "0 12px", marginBottom: 28 }}>
        {[
          { label: "Home", icon: "\u2302", active: activePage === "home" },
          { label: "Search", icon: "\u2315", active: false },
        ].map((item) => (
          <div
            key={item.label}
            onClick={() => {
              if (item.label === "Home") onNavigateHome();
            }}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 16,
              padding: "10px 12px",
              borderRadius: 6,
              fontSize: 15,
              fontWeight: item.active ? 700 : 500,
              color: item.active ? textWhite : textGray,
              cursor: "pointer",
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = textWhite)}
            onMouseLeave={(e) => {
              if (!item.active) e.currentTarget.style.color = textGray;
            }}
          >
            <span style={{ fontSize: 22 }}>{item.icon}</span>
            {item.label}
          </div>
        ))}
      </div>

      {/* Your Library */}
      <div style={{ padding: "0 24px", marginBottom: 16 }}>
        <span
          style={{
            fontSize: 11,
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.1em",
            color: textGray,
          }}
        >
          Your Library
        </span>
      </div>

      {/* Consoles as playlist items */}
      <div style={{ padding: "0 8px", flex: 1 }}>
        {consoles.map((c) => (
          <div
            key={c.id}
            onClick={() => navigate(`/proposal/8/console/${c.id}`)}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              padding: "8px 16px",
              borderRadius: 6,
              fontSize: 14,
              color: textGray,
              cursor: "pointer",
              transition: "all 0.15s",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = textWhite;
              e.currentTarget.style.background = "#1a1a1a";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = textGray;
              e.currentTarget.style.background = "transparent";
            }}
          >
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                background: consoleDots[c.id] || "#666",
                flexShrink: 0,
              }}
            />
            <span
              style={{
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {c.name}
            </span>
            <span
              style={{
                marginLeft: "auto",
                fontSize: 12,
                color: "#666",
                flexShrink: 0,
              }}
            >
              {c.gameCount}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Console List Page
function ConsolesPage() {
  const navigate = useNavigate();

  // Greeting based on time of day (or just a fixed one for the proposal)
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  const featured = consoles.slice(0, 6);

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textWhite,
        display: "flex",
      }}
    >
      <Sidebar activePage="home" onNavigateHome={() => navigate("/proposal/8")} />

      {/* Main content */}
      <div
        style={{
          flex: 1,
          marginLeft: 240,
          padding: "32px 40px 60px",
          overflowY: "auto",
        }}
      >
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            fontSize: 13,
            color: textGray,
            textDecoration: "none",
            display: "inline-block",
            marginBottom: 28,
            transition: "color 0.2s",
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = textWhite)}
          onMouseLeave={(e) => (e.currentTarget.style.color = textGray)}
        >
          &larr; Back to Gallery
        </a>

        {/* Greeting */}
        <h1
          style={{
            fontSize: 32,
            fontWeight: 700,
            margin: "0 0 28px 0",
            color: textWhite,
          }}
        >
          {greeting}
        </h1>

        {/* Featured row - compact shortcut cards */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 16,
            marginBottom: 48,
          }}
        >
          {featured.map((c) => (
            <div
              key={c.id}
              onClick={() => navigate(`console/${c.id}`)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 16,
                background: surfaceElevated,
                borderRadius: 6,
                overflow: "hidden",
                cursor: "pointer",
                transition: "background 0.2s",
                height: 64,
              }}
              onMouseEnter={(e) =>
                (e.currentTarget.style.background = "#333")
              }
              onMouseLeave={(e) =>
                (e.currentTarget.style.background = surfaceElevated)
              }
            >
              {/* Color block */}
              <div
                style={{
                  width: 64,
                  height: "100%",
                  background: `linear-gradient(135deg, ${c.colorTheme}cc, ${c.colorTheme}88)`,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 18,
                  fontWeight: 800,
                  color: "#fff",
                  flexShrink: 0,
                }}
              >
                {c.abbreviation}
              </div>
              <span
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: textWhite,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  paddingRight: 16,
                }}
              >
                {c.name}
              </span>
            </div>
          ))}
        </div>

        {/* Browse All */}
        <h2
          style={{
            fontSize: 24,
            fontWeight: 700,
            margin: "0 0 20px 0",
            color: textWhite,
          }}
        >
          Browse All
        </h2>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
            gap: 20,
          }}
        >
          {consoles.map((c) => (
            <div
              key={c.id}
              onClick={() => navigate(`console/${c.id}`)}
              style={{
                borderRadius: 8,
                padding: "20px 20px 24px",
                background: `linear-gradient(135deg, ${c.colorTheme}dd, ${c.colorTheme}66)`,
                cursor: "pointer",
                transition: "all 0.2s",
                position: "relative",
                overflow: "hidden",
                minHeight: 140,
              }}
              onMouseEnter={(e) =>
                (e.currentTarget.style.transform = "scale(1.03)")
              }
              onMouseLeave={(e) =>
                (e.currentTarget.style.transform = "scale(1)")
              }
            >
              <div
                style={{
                  fontSize: 32,
                  fontWeight: 800,
                  color: "#fff",
                  marginBottom: 4,
                }}
              >
                {c.abbreviation}
              </div>
              <div
                style={{
                  fontSize: 15,
                  fontWeight: 700,
                  color: "rgba(255,255,255,0.95)",
                  marginBottom: 4,
                }}
              >
                {c.name}
              </div>
              <div
                style={{
                  fontSize: 13,
                  color: "rgba(255,255,255,0.7)",
                }}
              >
                {c.gameCount} games
              </div>
            </div>
          ))}
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
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textWhite,
        display: "flex",
      }}
    >
      <Sidebar activePage="" onNavigateHome={() => navigate("/proposal/8")} />

      <div
        style={{
          flex: 1,
          marginLeft: 240,
          padding: "0 0 60px",
          overflowY: "auto",
        }}
      >
        {/* Header with gradient */}
        <div
          style={{
            background: `linear-gradient(180deg, ${con.colorTheme}66 0%, ${bg} 100%)`,
            padding: "32px 40px 28px",
          }}
        >
          {/* Back to Gallery */}
          <a
            href="/"
            style={{
              fontSize: 13,
              color: "rgba(255,255,255,0.6)",
              textDecoration: "none",
              display: "inline-block",
              marginBottom: 24,
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = textWhite)}
            onMouseLeave={(e) =>
              (e.currentTarget.style.color = "rgba(255,255,255,0.6)")
            }
          >
            &larr; Back to Gallery
          </a>

          <div style={{ display: "flex", alignItems: "flex-end", gap: 24 }}>
            {/* Console icon circle */}
            <div
              style={{
                width: 192,
                height: 192,
                borderRadius: 8,
                background: `linear-gradient(135deg, ${con.colorTheme}cc, ${con.colorTheme}55)`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 56,
                fontWeight: 800,
                color: "#fff",
                flexShrink: 0,
                boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
              }}
            >
              {con.abbreviation}
            </div>

            <div style={{ paddingBottom: 8 }}>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  textTransform: "uppercase",
                  color: textWhite,
                  marginBottom: 8,
                }}
              >
                Console
              </div>
              <h1
                style={{
                  fontSize: 48,
                  fontWeight: 800,
                  margin: "0 0 8px 0",
                  lineHeight: 1.1,
                  letterSpacing: "-0.02em",
                }}
              >
                {con.name}
              </h1>
              <div
                style={{
                  fontSize: 14,
                  color: textGray,
                }}
              >
                {snesGames.length} games
              </div>
            </div>
          </div>
        </div>

        {/* Controls bar */}
        <div
          style={{
            padding: "20px 40px",
            display: "flex",
            alignItems: "center",
            gap: 20,
          }}
        >
          {/* Shuffle Play button */}
          <button
            style={{
              width: 48,
              height: 48,
              borderRadius: "50%",
              border: "none",
              background: green,
              color: "#000",
              fontSize: 22,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              transition: "all 0.2s",
              boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
            }}
            onMouseEnter={(e) =>
              (e.currentTarget.style.transform = "scale(1.06)")
            }
            onMouseLeave={(e) =>
              (e.currentTarget.style.transform = "scale(1)")
            }
          >
            &#9654;
          </button>

          <span
            style={{
              fontSize: 14,
              fontWeight: 600,
              color: green,
              cursor: "pointer",
            }}
          >
            Shuffle Play
          </span>

          <div style={{ flex: 1 }} />

          {/* View toggles */}
          <div style={{ display: "flex", gap: 8 }}>
            <div
              onClick={() => setViewMode("list")}
              style={{
                padding: "6px 12px",
                borderRadius: 4,
                fontSize: 13,
                fontWeight: 600,
                color: viewMode === "list" ? textWhite : textGray,
                background:
                  viewMode === "list" ? surfaceElevated : "transparent",
                cursor: "pointer",
                transition: "all 0.15s",
              }}
            >
              List
            </div>
            <div
              onClick={() => setViewMode("grid")}
              style={{
                padding: "6px 12px",
                borderRadius: 4,
                fontSize: 13,
                fontWeight: 600,
                color: viewMode === "grid" ? textWhite : textGray,
                background:
                  viewMode === "grid" ? surfaceElevated : "transparent",
                cursor: "pointer",
                transition: "all 0.15s",
              }}
            >
              Grid
            </div>
          </div>
        </div>

        <div style={{ padding: "0 40px" }}>
          {viewMode === "list" ? (
            /* List view - Spotify track list style */
            <div>
              {/* Column Headers */}
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "40px 48px 1fr 140px 80px 60px",
                  gap: 16,
                  padding: "0 16px 8px",
                  borderBottom: "1px solid #333",
                  marginBottom: 8,
                  fontSize: 11,
                  fontWeight: 600,
                  textTransform: "uppercase",
                  letterSpacing: "0.1em",
                  color: textGray,
                }}
              >
                <span>#</span>
                <span></span>
                <span>Title</span>
                <span>Genre</span>
                <span>Size</span>
                <span>Rating</span>
              </div>

              {snesGames.map((game, i) => (
                <div
                  key={game.id}
                  onClick={() => navigate(`../game/${game.id}`)}
                  style={{
                    display: "grid",
                    gridTemplateColumns: "40px 48px 1fr 140px 80px 60px",
                    gap: 16,
                    padding: "8px 16px",
                    borderRadius: 4,
                    alignItems: "center",
                    cursor: "pointer",
                    transition: "background 0.15s",
                    background: i % 2 === 0 ? "transparent" : "#ffffff03",
                  }}
                  onMouseEnter={(e) =>
                    (e.currentTarget.style.background = hoverSurface)
                  }
                  onMouseLeave={(e) =>
                    (e.currentTarget.style.background =
                      i % 2 === 0 ? "transparent" : "#ffffff03")
                  }
                >
                  <span style={{ fontSize: 14, color: textGray }}>{i + 1}</span>
                  <img
                    src={game.coverUrl}
                    alt={game.title}
                    style={{
                      width: 40,
                      height: 40,
                      objectFit: "cover",
                      borderRadius: 4,
                    }}
                  />
                  <div style={{ minWidth: 0 }}>
                    <div
                      style={{
                        fontSize: 15,
                        fontWeight: 500,
                        color: textWhite,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                      }}
                    >
                      {game.title}
                    </div>
                    <div style={{ fontSize: 13, color: textGray }}>
                      {game.developer}
                    </div>
                  </div>
                  <span style={{ fontSize: 13, color: textGray }}>
                    {game.genre}
                  </span>
                  <span style={{ fontSize: 13, color: textGray }}>
                    {formatFileSize(game.fileSize)}
                  </span>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <span style={{ fontSize: 13, color: textGray }}>
                      {game.rating}
                    </span>
                    {game.isFavorite && (
                      <span style={{ fontSize: 12, color: green }}>
                        &#9829;
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            /* Grid view - compact album cards */
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(172px, 1fr))",
                gap: 24,
              }}
            >
              {snesGames.map((game) => (
                <div
                  key={game.id}
                  onClick={() => navigate(`../game/${game.id}`)}
                  style={{
                    padding: 16,
                    borderRadius: 8,
                    background: surfaceDark,
                    cursor: "pointer",
                    transition: "all 0.3s",
                  }}
                  onMouseEnter={(e) =>
                    (e.currentTarget.style.background = surfaceElevated)
                  }
                  onMouseLeave={(e) =>
                    (e.currentTarget.style.background = surfaceDark)
                  }
                >
                  <div
                    style={{
                      position: "relative",
                      marginBottom: 16,
                    }}
                  >
                    <img
                      src={game.coverUrl}
                      alt={game.title}
                      style={{
                        width: "100%",
                        aspectRatio: "1",
                        objectFit: "cover",
                        borderRadius: 6,
                        display: "block",
                        boxShadow: "0 8px 24px rgba(0,0,0,0.5)",
                      }}
                    />
                    {game.isFavorite && (
                      <span
                        style={{
                          position: "absolute",
                          top: 8,
                          right: 8,
                          fontSize: 14,
                          color: green,
                        }}
                      >
                        &#9829;
                      </span>
                    )}
                  </div>
                  <div
                    style={{
                      fontSize: 14,
                      fontWeight: 700,
                      color: textWhite,
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
                      fontSize: 13,
                      color: textGray,
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {game.genre}
                  </div>
                </div>
              ))}
            </div>
          )}
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
  const otherGames = snesGames.filter((g) => g.id !== game.id).slice(0, 6);

  return (
    <div
      style={{
        minHeight: "100vh",
        background: bg,
        fontFamily: font,
        color: textWhite,
        display: "flex",
      }}
    >
      <Sidebar activePage="" onNavigateHome={() => navigate("/proposal/8")} />

      <div
        style={{
          flex: 1,
          marginLeft: 240,
          overflowY: "auto",
        }}
      >
        {/* Hero gradient header */}
        <div
          style={{
            background: `linear-gradient(180deg, ${con.colorTheme}55 0%, ${bg} 100%)`,
            padding: "32px 40px 40px",
          }}
        >
          {/* Back to Gallery */}
          <a
            href="/"
            style={{
              fontSize: 13,
              color: "rgba(255,255,255,0.6)",
              textDecoration: "none",
              display: "inline-block",
              marginBottom: 24,
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = textWhite)}
            onMouseLeave={(e) =>
              (e.currentTarget.style.color = "rgba(255,255,255,0.6)")
            }
          >
            &larr; Back to Gallery
          </a>

          <div style={{ display: "flex", gap: 32 }}>
            {/* Cover Art */}
            <div
              style={{
                width: 230,
                flexShrink: 0,
              }}
            >
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "1",
                  objectFit: "cover",
                  borderRadius: 8,
                  display: "block",
                  boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
                }}
              />
            </div>

            {/* Info */}
            <div
              style={{
                flex: 1,
                display: "flex",
                flexDirection: "column",
                justifyContent: "flex-end",
                paddingBottom: 8,
              }}
            >
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  textTransform: "uppercase",
                  color: textWhite,
                  marginBottom: 8,
                }}
              >
                Game
              </div>
              <h1
                style={{
                  fontSize: 48,
                  fontWeight: 800,
                  margin: "0 0 16px 0",
                  lineHeight: 1.1,
                  letterSpacing: "-0.02em",
                }}
              >
                {game.title}
              </h1>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  fontSize: 14,
                  color: textGray,
                  flexWrap: "wrap",
                }}
              >
                <span style={{ fontWeight: 700, color: textWhite }}>
                  {game.developer}
                </span>
                <span style={{ color: "#666" }}>&bull;</span>
                <span
                  onClick={() => navigate(`/proposal/8/console/${con.id}`)}
                  style={{ cursor: "pointer", transition: "color 0.2s" }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = textWhite)}
                  onMouseLeave={(e) => (e.currentTarget.style.color = textGray)}
                >
                  {con.name}
                </span>
                <span style={{ color: "#666" }}>&bull;</span>
                <span>{game.rating}/10</span>
                {game.totalPlayTime > 0 && (
                  <>
                    <span style={{ color: "#666" }}>&bull;</span>
                    <span>{formatPlayTime(game.totalPlayTime)}</span>
                  </>
                )}
                <span style={{ color: "#666" }}>&bull;</span>
                <span>{formatFileSize(game.fileSize)}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Action Bar */}
        <div
          style={{
            padding: "20px 40px",
            display: "flex",
            alignItems: "center",
            gap: 24,
          }}
        >
          <button
            style={{
              width: 52,
              height: 52,
              borderRadius: "50%",
              border: "none",
              background: green,
              color: "#000",
              fontSize: 24,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              transition: "all 0.2s",
              boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
            }}
            onMouseEnter={(e) =>
              (e.currentTarget.style.transform = "scale(1.06)")
            }
            onMouseLeave={(e) =>
              (e.currentTarget.style.transform = "scale(1)")
            }
          >
            &#9654;
          </button>
          <span
            style={{
              fontSize: 28,
              color: game.isFavorite ? green : textGray,
              cursor: "pointer",
              transition: "all 0.2s",
            }}
            onMouseEnter={(e) => {
              if (!game.isFavorite) e.currentTarget.style.color = textWhite;
            }}
            onMouseLeave={(e) => {
              if (!game.isFavorite) e.currentTarget.style.color = textGray;
            }}
          >
            {game.isFavorite ? "\u2665" : "\u2661"}
          </span>
        </div>

        {/* Description */}
        <div style={{ padding: "0 40px 32px" }}>
          <p
            style={{
              fontSize: 15,
              color: textGray,
              lineHeight: 1.7,
              margin: "0 0 28px 0",
              maxWidth: 700,
            }}
          >
            {game.description}
          </p>

          {/* Details Row */}
          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: 28,
              marginBottom: 40,
            }}
          >
            {[
              { label: "Publisher", value: game.publisher },
              { label: "Released", value: game.releaseDate },
              { label: "Genre", value: game.genre },
              {
                label: "Players",
                value: `${game.players} player${game.players > 1 ? "s" : ""}`,
              },
            ].map((item) => (
              <div key={item.label}>
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    textTransform: "uppercase",
                    letterSpacing: "0.1em",
                    color: textGray,
                    marginBottom: 4,
                  }}
                >
                  {item.label}
                </div>
                <div
                  style={{
                    fontSize: 15,
                    fontWeight: 500,
                    color: textWhite,
                  }}
                >
                  {item.value}
                </div>
              </div>
            ))}
          </div>

          {/* Screenshots */}
          {game.screenshotUrls.length > 0 && (
            <div style={{ marginBottom: 48 }}>
              <h2
                style={{
                  fontSize: 20,
                  fontWeight: 700,
                  margin: "0 0 16px 0",
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
                  <img
                    key={i}
                    src={url}
                    alt={`Screenshot ${i + 1}`}
                    style={{
                      width: 280,
                      aspectRatio: "16/9",
                      objectFit: "cover",
                      borderRadius: 8,
                      flexShrink: 0,
                    }}
                  />
                ))}
              </div>
            </div>
          )}

          {/* More from this console */}
          <div>
            <h2
              style={{
                fontSize: 20,
                fontWeight: 700,
                margin: "0 0 16px 0",
              }}
            >
              More from {con.name}
            </h2>

            {/* Compact track rows */}
            {otherGames.map((g, i) => (
              <div
                key={g.id}
                onClick={() => navigate(`/proposal/8/game/${g.id}`)}
                style={{
                  display: "grid",
                  gridTemplateColumns: "40px 48px 1fr 100px 60px",
                  gap: 16,
                  padding: "8px 16px",
                  borderRadius: 4,
                  alignItems: "center",
                  cursor: "pointer",
                  transition: "background 0.15s",
                }}
                onMouseEnter={(e) =>
                  (e.currentTarget.style.background = hoverSurface)
                }
                onMouseLeave={(e) =>
                  (e.currentTarget.style.background = "transparent")
                }
              >
                <span style={{ fontSize: 14, color: textGray }}>{i + 1}</span>
                <img
                  src={g.coverUrl}
                  alt={g.title}
                  style={{
                    width: 40,
                    height: 40,
                    objectFit: "cover",
                    borderRadius: 4,
                  }}
                />
                <div style={{ minWidth: 0 }}>
                  <div
                    style={{
                      fontSize: 15,
                      fontWeight: 500,
                      color: textWhite,
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {g.title}
                  </div>
                  <div style={{ fontSize: 13, color: textGray }}>
                    {g.developer}
                  </div>
                </div>
                <span style={{ fontSize: 13, color: textGray }}>
                  {g.genre}
                </span>
                <span style={{ fontSize: 13, color: textGray }}>
                  {g.rating}/10
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// Main Export
export function Proposal8() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<GamesPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
