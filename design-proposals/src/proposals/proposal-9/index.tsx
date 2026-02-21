import { useState } from "react";
import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";
import type { Game } from "@/mock-data";

/* ─── Notion / Linear palette ─── */
const N = {
  bg: "#ffffff",
  surface: "#f7f6f3",
  sidebar: "#fbfbfa",
  text: "#37352f",
  textSecondary: "#787774",
  textMuted: "#9b9a97",
  accent: "#eb5757",
  accentLight: "rgba(235, 87, 87, 0.08)",
  accentMedium: "rgba(235, 87, 87, 0.12)",
  border: "#e9e9e7",
  borderLight: "#f0efed",
  hover: "#f0f0ee",
  activeNav: "rgba(235, 87, 87, 0.06)",
  sans: '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
};

/* ─── Sidebar nav items ─── */
const navItems = [
  { emoji: "\u{1F3E0}", label: "Home" },
  { emoji: "\u{1F3AE}", label: "Consoles" },
  { emoji: "\u{1F4DA}", label: "All Games" },
  { emoji: "\u2764\uFE0F", label: "Favorites" },
];

/* ─── Sidebar Component ─── */
function Sidebar({
  activePage,
  onNavClick,
}: {
  activePage: string;
  onNavClick?: (page: string) => void;
}) {
  const navigate = useNavigate();
  const [hoveredNav, setHoveredNav] = useState<string | null>(null);
  const [hoveredConsole, setHoveredConsole] = useState<string | null>(null);

  return (
    <div
      style={{
        width: 260,
        minHeight: "100vh",
        background: N.sidebar,
        borderRight: `1px solid ${N.border}`,
        padding: "16px 12px",
        display: "flex",
        flexDirection: "column",
        flexShrink: 0,
        fontFamily: N.sans,
      }}
    >
      {/* Brand */}
      <div
        style={{
          padding: "4px 8px 20px",
          fontSize: 15,
          fontWeight: 600,
          color: N.text,
          letterSpacing: "-0.01em",
        }}
      >
        Spela
      </div>

      {/* Nav items */}
      <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
        {navItems.map((item) => {
          const isActive = activePage === item.label;
          const isHovered = hoveredNav === item.label;
          return (
            <div
              key={item.label}
              onClick={() => {
                if (item.label === "Consoles" || item.label === "Home") {
                  navigate("/");
                }
                onNavClick?.(item.label);
              }}
              onMouseEnter={() => setHoveredNav(item.label)}
              onMouseLeave={() => setHoveredNav(null)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                padding: "5px 8px",
                borderRadius: 4,
                cursor: "pointer",
                fontSize: 14,
                color: isActive ? N.text : N.textSecondary,
                fontWeight: isActive ? 500 : 400,
                background: isActive
                  ? N.activeNav
                  : isHovered
                  ? N.hover
                  : "transparent",
                transition: "background 0.1s ease",
              }}
            >
              <span style={{ fontSize: 15, width: 20, textAlign: "center" }}>
                {item.emoji}
              </span>
              <span>{item.label}</span>
            </div>
          );
        })}
      </div>

      {/* Separator */}
      <div
        style={{
          height: 1,
          background: N.border,
          margin: "16px 8px",
        }}
      />

      {/* Console section header */}
      <div
        style={{
          padding: "4px 8px 8px",
          fontSize: 11,
          fontWeight: 500,
          color: N.textMuted,
          textTransform: "uppercase" as const,
          letterSpacing: "0.06em",
        }}
      >
        Consoles
      </div>

      {/* Console list */}
      <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
        {consoles.map((c) => {
          const isHovered = hoveredConsole === c.id;
          return (
            <div
              key={c.id}
              onClick={() => navigate(`console/${c.id}`)}
              onMouseEnter={() => setHoveredConsole(c.id)}
              onMouseLeave={() => setHoveredConsole(null)}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 8,
                padding: "4px 8px",
                borderRadius: 4,
                cursor: "pointer",
                fontSize: 13,
                color: N.textSecondary,
                background: isHovered ? N.hover : "transparent",
                transition: "background 0.1s ease",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  overflow: "hidden",
                }}
              >
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: "50%",
                    background: c.colorTheme,
                    flexShrink: 0,
                  }}
                />
                <span
                  style={{
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {c.name}
                </span>
              </div>
              <span
                style={{
                  fontSize: 12,
                  color: N.textMuted,
                  flexShrink: 0,
                }}
              >
                {c.gameCount}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ─── Page Layout with Sidebar ─── */
function PageLayout({
  activePage,
  children,
}: {
  activePage: string;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        display: "flex",
        minHeight: "100vh",
        background: N.bg,
        fontFamily: N.sans,
        color: N.text,
      }}
    >
      <Sidebar activePage={activePage} />
      <div
        style={{
          flex: 1,
          padding: "32px 48px",
          maxWidth: 960,
          overflow: "auto",
        }}
      >
        {/* Back to Gallery */}
        <div style={{ marginBottom: 8 }}>
          <a
            href="/"
            style={{
              color: N.textMuted,
              textDecoration: "none",
              fontSize: 12,
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
            }}
          >
            ← Back to Gallery
          </a>
        </div>
        {children}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════
   CONSOLES PAGE - Table/Database View
   ═══════════════════════════════════════ */
function ConsolesPage() {
  const navigate = useNavigate();
  const [hoveredRow, setHoveredRow] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"table" | "gallery">("table");

  return (
    <PageLayout activePage="Consoles">
      {/* Breadcrumb */}
      <div
        style={{
          fontSize: 12,
          color: N.textMuted,
          marginBottom: 8,
        }}
      >
        Home / Consoles
      </div>

      {/* Title row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 24,
        }}
      >
        <h1
          style={{
            fontSize: 32,
            fontWeight: 700,
            color: N.text,
            margin: 0,
            letterSpacing: "-0.02em",
          }}
        >
          Consoles
        </h1>

        {/* View toggle */}
        <div
          style={{
            display: "flex",
            gap: 2,
            background: N.surface,
            borderRadius: 4,
            padding: 2,
          }}
        >
          {(["table", "gallery"] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => setViewMode(mode)}
              style={{
                background: viewMode === mode ? N.bg : "transparent",
                border: viewMode === mode ? `1px solid ${N.border}` : "1px solid transparent",
                borderRadius: 3,
                padding: "4px 8px",
                fontSize: 12,
                color: viewMode === mode ? N.text : N.textMuted,
                cursor: "pointer",
                fontFamily: N.sans,
              }}
            >
              {mode === "table" ? "\u2630" : "\u2637"} {mode.charAt(0).toUpperCase() + mode.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {viewMode === "table" ? (
        /* ─── Table View ─── */
        <div
          style={{
            border: `1px solid ${N.border}`,
            borderRadius: 4,
            overflow: "hidden",
          }}
        >
          {/* Table header */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 100px 120px",
              background: N.surface,
              borderBottom: `1px solid ${N.border}`,
            }}
          >
            {["Platform", "Games", ""].map((header, i) => (
              <div
                key={i}
                style={{
                  padding: "8px 12px",
                  fontSize: 12,
                  fontWeight: 500,
                  color: N.textMuted,
                  textTransform: "uppercase" as const,
                  letterSpacing: "0.04em",
                }}
              >
                {header}
              </div>
            ))}
          </div>

          {/* Table rows */}
          {consoles.map((c) => {
            const isHovered = hoveredRow === c.id;
            return (
              <div
                key={c.id}
                onClick={() => navigate(`console/${c.id}`)}
                onMouseEnter={() => setHoveredRow(c.id)}
                onMouseLeave={() => setHoveredRow(null)}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 100px 120px",
                  borderBottom: `1px solid ${N.borderLight}`,
                  cursor: "pointer",
                  background: isHovered ? N.surface : N.bg,
                  transition: "background 0.1s ease",
                }}
              >
                <div
                  style={{
                    padding: "10px 12px",
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                  }}
                >
                  <span
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      background: c.colorTheme,
                      flexShrink: 0,
                    }}
                  />
                  <span
                    style={{
                      fontSize: 14,
                      color: N.text,
                      fontWeight: 400,
                    }}
                  >
                    {c.name}
                  </span>
                </div>
                <div
                  style={{
                    padding: "10px 12px",
                    fontSize: 14,
                    color: N.textSecondary,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {c.gameCount}
                </div>
                <div
                  style={{
                    padding: "10px 12px",
                    fontSize: 13,
                    color: isHovered ? N.accent : N.textMuted,
                    display: "flex",
                    alignItems: "center",
                    transition: "color 0.1s ease",
                  }}
                >
                  Browse →
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* ─── Gallery View ─── */
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 12,
          }}
        >
          {consoles.map((c) => {
            const isHovered = hoveredRow === c.id;
            return (
              <div
                key={c.id}
                onClick={() => navigate(`console/${c.id}`)}
                onMouseEnter={() => setHoveredRow(c.id)}
                onMouseLeave={() => setHoveredRow(null)}
                style={{
                  border: `1px solid ${isHovered ? N.border : N.borderLight}`,
                  borderRadius: 4,
                  padding: 16,
                  cursor: "pointer",
                  background: isHovered ? N.surface : N.bg,
                  transition: "all 0.15s ease",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 8,
                    marginBottom: 8,
                  }}
                >
                  <span
                    style={{
                      width: 10,
                      height: 10,
                      borderRadius: "50%",
                      background: c.colorTheme,
                    }}
                  />
                  <span
                    style={{
                      fontSize: 14,
                      fontWeight: 500,
                      color: N.text,
                    }}
                  >
                    {c.name}
                  </span>
                </div>
                <div
                  style={{
                    fontSize: 13,
                    color: N.textMuted,
                  }}
                >
                  {c.gameCount} games
                </div>
              </div>
            );
          })}
        </div>
      )}
    </PageLayout>
  );
}

/* ═══════════════════════════════════════
   GAMES PAGE - Database Table View
   ═══════════════════════════════════════ */
function ConsoleDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const con = consoles.find((c) => c.id === id) || consoles[0];
  const [hoveredRow, setHoveredRow] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"table" | "gallery">("table");
  const [sortCol, setSortCol] = useState<string>("Title");

  const columns = [
    { key: "cover", label: "", width: "48px" },
    { key: "title", label: "Title", width: "1fr" },
    { key: "genre", label: "Genre", width: "130px" },
    { key: "developer", label: "Developer", width: "140px" },
    { key: "rating", label: "Rating", width: "70px" },
    { key: "size", label: "Size", width: "80px" },
  ];

  const gridTemplate = columns.map((c) => c.width).join(" ");

  return (
    <PageLayout activePage="Consoles">
      {/* Breadcrumb */}
      <div style={{ marginBottom: 8 }}>
        <span
          onClick={() => navigate("/")}
          style={{
            fontSize: 12,
            color: N.textMuted,
            cursor: "pointer",
          }}
        >
          ← Consoles
        </span>
        <span style={{ fontSize: 12, color: N.textMuted }}> / {con.name}</span>
      </div>

      {/* Title row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 4,
        }}
      >
        <h1
          style={{
            fontSize: 32,
            fontWeight: 700,
            color: N.text,
            margin: 0,
            letterSpacing: "-0.02em",
          }}
        >
          {con.name}
        </h1>

        {/* View toggle */}
        <div
          style={{
            display: "flex",
            gap: 2,
            background: N.surface,
            borderRadius: 4,
            padding: 2,
          }}
        >
          {(["table", "gallery"] as const).map((mode) => (
            <button
              key={mode}
              onClick={() => setViewMode(mode)}
              style={{
                background: viewMode === mode ? N.bg : "transparent",
                border:
                  viewMode === mode
                    ? `1px solid ${N.border}`
                    : "1px solid transparent",
                borderRadius: 3,
                padding: "4px 8px",
                fontSize: 12,
                color: viewMode === mode ? N.text : N.textMuted,
                cursor: "pointer",
                fontFamily: N.sans,
              }}
            >
              {mode === "table" ? "\u2630" : "\u2637"}{" "}
              {mode.charAt(0).toUpperCase() + mode.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {/* Game count */}
      <div
        style={{
          fontSize: 13,
          color: N.textMuted,
          marginBottom: 20,
        }}
      >
        {snesGames.length} games
      </div>

      {viewMode === "table" ? (
        /* ─── Table View ─── */
        <div
          style={{
            border: `1px solid ${N.border}`,
            borderRadius: 4,
            overflow: "hidden",
          }}
        >
          {/* Header */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: gridTemplate,
              background: N.surface,
              borderBottom: `1px solid ${N.border}`,
            }}
          >
            {columns.map((col) => (
              <div
                key={col.key}
                onClick={() => col.label && setSortCol(col.label)}
                style={{
                  padding: "8px 10px",
                  fontSize: 12,
                  fontWeight: 500,
                  color: sortCol === col.label ? N.text : N.textMuted,
                  textTransform: "uppercase" as const,
                  letterSpacing: "0.04em",
                  cursor: col.label ? "pointer" : "default",
                  userSelect: "none",
                  display: "flex",
                  alignItems: "center",
                  gap: 4,
                }}
              >
                {col.label}
                {sortCol === col.label && (
                  <span style={{ fontSize: 10 }}>▼</span>
                )}
              </div>
            ))}
          </div>

          {/* Rows */}
          {snesGames.map((game) => {
            const isHovered = hoveredRow === game.id;
            return (
              <div
                key={game.id}
                onClick={() => navigate(`/proposal/9/game/${game.id}`)}
                onMouseEnter={() => setHoveredRow(game.id)}
                onMouseLeave={() => setHoveredRow(null)}
                style={{
                  display: "grid",
                  gridTemplateColumns: gridTemplate,
                  borderBottom: `1px solid ${N.borderLight}`,
                  cursor: "pointer",
                  background: isHovered ? N.surface : N.bg,
                  transition: "background 0.1s ease",
                }}
              >
                {/* Cover thumbnail */}
                <div
                  style={{
                    padding: "6px 10px",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <img
                    src={game.coverUrl}
                    alt=""
                    style={{
                      width: 32,
                      height: 40,
                      objectFit: "cover",
                      borderRadius: 2,
                    }}
                  />
                </div>

                {/* Title */}
                <div
                  style={{
                    padding: "6px 10px",
                    fontSize: 14,
                    color: N.text,
                    display: "flex",
                    alignItems: "center",
                    gap: 6,
                  }}
                >
                  {game.title}
                  {game.isFavorite && (
                    <span style={{ color: N.accent, fontSize: 12 }}>
                      ♥
                    </span>
                  )}
                </div>

                {/* Genre */}
                <div
                  style={{
                    padding: "6px 10px",
                    fontSize: 13,
                    color: N.textSecondary,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <span
                    style={{
                      background: N.surface,
                      padding: "2px 8px",
                      borderRadius: 3,
                      fontSize: 12,
                    }}
                  >
                    {game.genre}
                  </span>
                </div>

                {/* Developer */}
                <div
                  style={{
                    padding: "6px 10px",
                    fontSize: 13,
                    color: N.textSecondary,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {game.developer}
                </div>

                {/* Rating */}
                <div
                  style={{
                    padding: "6px 10px",
                    fontSize: 13,
                    color: N.textSecondary,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {game.rating}/10
                </div>

                {/* Size */}
                <div
                  style={{
                    padding: "6px 10px",
                    fontSize: 13,
                    color: N.textMuted,
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  {formatFileSize(game.fileSize)}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        /* ─── Gallery View ─── */
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(3, 1fr)",
            gap: 12,
          }}
        >
          {snesGames.map((game) => (
            <GameCard
              key={game.id}
              game={game}
              onClick={() => navigate(`/proposal/9/game/${game.id}`)}
            />
          ))}
        </div>
      )}
    </PageLayout>
  );
}

/* ─── Game Card for Gallery View ─── */
function GameCard({
  game,
  onClick,
}: {
  game: Game;
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        border: `1px solid ${hovered ? N.border : N.borderLight}`,
        borderRadius: 4,
        overflow: "hidden",
        cursor: "pointer",
        background: hovered ? N.surface : N.bg,
        transition: "all 0.15s ease",
      }}
    >
      {/* Cover */}
      <div style={{ width: "100%", height: 160, overflow: "hidden" }}>
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
      </div>
      <div style={{ padding: 12 }}>
        <div
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: N.text,
            marginBottom: 6,
          }}
        >
          {game.title}
          {game.isFavorite && (
            <span style={{ color: N.accent, marginLeft: 4, fontSize: 12 }}>
              ♥
            </span>
          )}
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <span
            style={{
              background: N.surface,
              padding: "2px 8px",
              borderRadius: 3,
              fontSize: 11,
              color: N.textSecondary,
            }}
          >
            {game.genre}
          </span>
          <span
            style={{
              background: N.surface,
              padding: "2px 8px",
              borderRadius: 3,
              fontSize: 11,
              color: N.textSecondary,
            }}
          >
            {game.rating}/10
          </span>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════
   GAME DETAIL PAGE - Property Table
   ═══════════════════════════════════════ */
function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id) || snesGames[0];
  const [playHovered, setPlayHovered] = useState(false);
  const [favHovered, setFavHovered] = useState(false);

  const properties = [
    { label: "Developer", value: game.developer },
    { label: "Publisher", value: game.publisher },
    { label: "Genre", value: game.genre },
    { label: "Release Date", value: game.releaseDate },
    {
      label: "Players",
      value: `${game.players} player${game.players > 1 ? "s" : ""}`,
    },
    { label: "Rating", value: `${game.rating}/10` },
    { label: "File Size", value: formatFileSize(game.fileSize) },
    ...(game.totalPlayTime > 0
      ? [{ label: "Play Time", value: formatPlayTime(game.totalPlayTime) }]
      : []),
    ...(game.lastPlayedAt
      ? [
          {
            label: "Last Played",
            value: new Date(game.lastPlayedAt).toLocaleDateString("en-US", {
              year: "numeric",
              month: "short",
              day: "numeric",
            }),
          },
        ]
      : []),
  ];

  return (
    <PageLayout activePage="Consoles">
      {/* Breadcrumb */}
      <div style={{ marginBottom: 8 }}>
        <span
          onClick={() => navigate("/")}
          style={{
            fontSize: 12,
            color: N.textMuted,
            cursor: "pointer",
          }}
        >
          Consoles
        </span>
        <span style={{ fontSize: 12, color: N.textMuted }}> / </span>
        <span
          onClick={() => navigate(-1)}
          style={{
            fontSize: 12,
            color: N.textMuted,
            cursor: "pointer",
          }}
        >
          {game.consoleName}
        </span>
        <span style={{ fontSize: 12, color: N.textMuted }}>
          {" "}
          / {game.title}
        </span>
      </div>

      {/* Content */}
      <div style={{ maxWidth: 700 }}>
        {/* Cover + Title row */}
        <div
          style={{
            display: "flex",
            gap: 28,
            alignItems: "flex-start",
            marginBottom: 32,
          }}
        >
          {/* Cover */}
          <div
            style={{
              width: 180,
              height: 230,
              flexShrink: 0,
              border: `1px solid ${N.border}`,
              borderRadius: 4,
              overflow: "hidden",
            }}
          >
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
          </div>

          {/* Title + meta */}
          <div style={{ flex: 1, paddingTop: 4 }}>
            <h1
              style={{
                fontSize: 32,
                fontWeight: 700,
                color: N.text,
                margin: "0 0 8px",
                letterSpacing: "-0.02em",
                lineHeight: 1.2,
              }}
            >
              {game.title}
            </h1>
            <div
              style={{
                fontSize: 14,
                color: N.textSecondary,
                marginBottom: 4,
              }}
            >
              {game.consoleName}
            </div>
            <div
              style={{
                display: "flex",
                gap: 6,
                marginBottom: 20,
                flexWrap: "wrap",
              }}
            >
              <span
                style={{
                  background: N.surface,
                  padding: "3px 10px",
                  borderRadius: 3,
                  fontSize: 12,
                  color: N.textSecondary,
                }}
              >
                {game.genre}
              </span>
              <span
                style={{
                  background: N.surface,
                  padding: "3px 10px",
                  borderRadius: 3,
                  fontSize: 12,
                  color: N.textSecondary,
                }}
              >
                {game.rating}/10
              </span>
              {game.isFavorite && (
                <span
                  style={{
                    background: N.accentLight,
                    padding: "3px 10px",
                    borderRadius: 3,
                    fontSize: 12,
                    color: N.accent,
                  }}
                >
                  ♥ Favorite
                </span>
              )}
            </div>

            {/* Action buttons */}
            <div style={{ display: "flex", gap: 8 }}>
              <button
                onMouseEnter={() => setPlayHovered(true)}
                onMouseLeave={() => setPlayHovered(false)}
                style={{
                  background: playHovered ? "#d94444" : N.accent,
                  color: "#ffffff",
                  border: "none",
                  borderRadius: 4,
                  padding: "8px 24px",
                  fontSize: 14,
                  fontWeight: 500,
                  cursor: "pointer",
                  fontFamily: N.sans,
                  transition: "background 0.15s ease",
                }}
              >
                Play
              </button>
              <button
                onMouseEnter={() => setFavHovered(true)}
                onMouseLeave={() => setFavHovered(false)}
                style={{
                  background: favHovered ? N.surface : N.bg,
                  color: N.textSecondary,
                  border: `1px solid ${N.border}`,
                  borderRadius: 4,
                  padding: "8px 16px",
                  fontSize: 14,
                  cursor: "pointer",
                  fontFamily: N.sans,
                  transition: "background 0.15s ease",
                }}
              >
                {game.isFavorite ? "♥ Favorited" : "♡ Favorite"}
              </button>
            </div>
          </div>
        </div>

        {/* Property table */}
        <div
          style={{
            borderTop: `1px solid ${N.border}`,
            marginBottom: 32,
          }}
        >
          {properties.map((prop) => (
            <div
              key={prop.label}
              style={{
                display: "flex",
                alignItems: "center",
                padding: "10px 0",
                borderBottom: `1px solid ${N.borderLight}`,
              }}
            >
              <span
                style={{
                  width: 140,
                  fontSize: 13,
                  color: N.textMuted,
                  flexShrink: 0,
                }}
              >
                {prop.label}
              </span>
              <span
                style={{
                  fontSize: 14,
                  color: N.text,
                }}
              >
                {prop.value}
              </span>
            </div>
          ))}
        </div>

        {/* Description */}
        <div style={{ marginBottom: 32 }}>
          <div
            style={{
              fontSize: 13,
              color: N.textMuted,
              textTransform: "uppercase" as const,
              letterSpacing: "0.04em",
              fontWeight: 500,
              marginBottom: 8,
            }}
          >
            Description
          </div>
          <p
            style={{
              fontSize: 15,
              color: N.text,
              lineHeight: 1.7,
              margin: 0,
              maxWidth: 600,
            }}
          >
            {game.description}
          </p>
        </div>

        {/* Screenshots */}
        {game.screenshotUrls.length > 0 && (
          <div>
            <div
              style={{
                fontSize: 13,
                color: N.textMuted,
                textTransform: "uppercase" as const,
                letterSpacing: "0.04em",
                fontWeight: 500,
                marginBottom: 12,
              }}
            >
              Screenshots
            </div>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${Math.min(game.screenshotUrls.length, 4)}, 1fr)`,
                gap: 8,
              }}
            >
              {game.screenshotUrls.map((url, i) => (
                <div
                  key={i}
                  style={{
                    border: `1px solid ${N.border}`,
                    borderRadius: 4,
                    overflow: "hidden",
                  }}
                >
                  <img
                    src={url}
                    alt={`Screenshot ${i + 1}`}
                    style={{
                      width: "100%",
                      height: 100,
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
    </PageLayout>
  );
}

/* ─── Main Export ─── */
export function Proposal9() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<ConsoleDetailPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
