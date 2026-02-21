import { useState } from "react";
import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";
import type { Game } from "@/mock-data";

/* ─── Retro Colorful Palette ─── */
const R = {
  bg: "#faf5eb",
  surface: "#ffffff",
  text: "#2d2015",
  textSecondary: "#8b7355",
  red: "#E52521",
  green: "#43B047",
  blue: "#0066FF",
  gold: "#FFD700",
  pipeGreen: "#00A651",
  skyBlue: "#87CEEB",
  purple: "#9B59B6",
  orange: "#FF8C00",
  pink: "#FF69B4",
  teal: "#00CED1",
  sans: '-apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
  rounded: '"Nunito", -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif',
};

/* ─── Console color assignments ─── */
const consoleColors: Record<string, string> = {
  "1": R.red,       // NES - Mario Red
  "2": R.green,     // SNES - Luigi Green
  "3": R.pipeGreen, // Game Boy - Pipe Green
  "4": R.blue,      // GBA - Sonic Blue
  "5": R.gold,      // N64 - Coin Gold
  "6": R.blue,      // Genesis - Sonic Blue
  "7": R.purple,    // PlayStation - Purple
  "8": R.orange,    // Neo Geo - Orange
  "9": R.teal,      // GBC - Teal
  "10": R.pink,     // Arcade - Pink
};

function getConsoleColor(id: string): string {
  return consoleColors[id] || R.red;
}

/* ─── Genre color map ─── */
const genreColors: Record<string, string> = {
  Platformer: R.red,
  "Action RPG": R.blue,
  "Action-Adventure": R.pipeGreen,
  RPG: R.purple,
  Racing: R.orange,
  Fighting: R.red,
  "Action Platformer": R.teal,
};

function getGenreColor(genre: string): string {
  return genreColors[genre] || R.blue;
}

/* ─── Colorful Letter Component ─── */
function ColorfulText({ text, style: extraStyle }: { text: string; style?: React.CSSProperties }) {
  const colors = [R.red, R.blue, R.green, R.gold, R.purple, R.orange, R.pipeGreen, R.teal];
  return (
    <span style={extraStyle}>
      {text.split("").map((char, i) =>
        char === " " ? (
          <span key={i}>&nbsp;</span>
        ) : (
          <span
            key={i}
            style={{
              color: colors[i % colors.length],
            }}
          >
            {char}
          </span>
        )
      )}
    </span>
  );
}

/* ─── Dot Divider ─── */
function DotDivider() {
  const colors = [R.red, R.blue, R.green, R.gold, R.purple, R.orange];
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        gap: 8,
        padding: "24px 0",
      }}
    >
      {colors.map((color, i) => (
        <span
          key={i}
          style={{
            width: 8,
            height: 8,
            borderRadius: "50%",
            background: color,
            display: "inline-block",
          }}
        />
      ))}
    </div>
  );
}

/* ─── Top Navigation ─── */
function TopNav() {
  const navigate = useNavigate();
  const [hoveredTab, setHoveredTab] = useState<string | null>(null);

  const tabs = [
    { label: "Home", color: R.red },
    { label: "Consoles", color: R.blue },
    { label: "Favorites", color: R.pink },
  ];

  return (
    <div
      style={{
        background: R.surface,
        borderBottom: `2px solid ${R.bg}`,
        padding: "0 32px",
        display: "flex",
        alignItems: "center",
        height: 60,
        fontFamily: R.sans,
      }}
    >
      {/* Brand */}
      <div
        onClick={() => navigate("/")}
        style={{
          fontSize: 24,
          fontWeight: 900,
          color: R.red,
          letterSpacing: "-0.02em",
          cursor: "pointer",
          marginRight: 40,
        }}
      >
        SPELA
      </div>

      {/* Nav tabs */}
      <div style={{ display: "flex", gap: 4 }}>
        {tabs.map((tab) => {
          const isHovered = hoveredTab === tab.label;
          return (
            <div
              key={tab.label}
              onClick={() => navigate("/")}
              onMouseEnter={() => setHoveredTab(tab.label)}
              onMouseLeave={() => setHoveredTab(null)}
              style={{
                padding: "8px 18px",
                borderRadius: 20,
                fontSize: 14,
                fontWeight: 600,
                color: isHovered ? "#ffffff" : tab.color,
                background: isHovered ? tab.color : "transparent",
                cursor: "pointer",
                transition: "all 0.2s ease",
              }}
            >
              {tab.label}
            </div>
          );
        })}
      </div>

      {/* Spacer */}
      <div style={{ flex: 1 }} />

      {/* Back to gallery */}
      <a
        href="/"
        style={{
          color: R.textSecondary,
          textDecoration: "none",
          fontSize: 13,
          display: "inline-flex",
          alignItems: "center",
          gap: 4,
        }}
      >
        ← Back to Gallery
      </a>
    </div>
  );
}

/* ─── Page Shell ─── */
function PageShell({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: R.bg,
        fontFamily: R.sans,
        color: R.text,
      }}
    >
      <TopNav />
      <div
        style={{
          maxWidth: 1100,
          margin: "0 auto",
          padding: "32px 40px 80px",
        }}
      >
        {children}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════
   CONSOLES PAGE
   ═══════════════════════════════════════ */
function ConsolesPage() {
  const navigate = useNavigate();

  return (
    <PageShell>
      {/* Big colorful banner */}
      <div
        style={{
          textAlign: "center",
          padding: "40px 0 16px",
          position: "relative",
        }}
      >
        <ColorfulText
          text="LET'S PLAY!"
          style={{
            fontSize: 52,
            fontWeight: 900,
            letterSpacing: "-0.02em",
            display: "block",
          }}
        />
        <p
          style={{
            fontSize: 16,
            color: R.textSecondary,
            marginTop: 8,
          }}
        >
          Choose a console and start your adventure
        </p>
      </div>

      <DotDivider />

      {/* Console grid */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, 1fr)",
          gap: 20,
        }}
      >
        {consoles.map((c) => (
          <ConsoleCard
            key={c.id}
            console_={c}
            color={getConsoleColor(c.id)}
            onClick={() => navigate(`console/${c.id}`)}
          />
        ))}
      </div>
    </PageShell>
  );
}

/* ─── Console Card ─── */
function ConsoleCard({
  console_,
  color,
  onClick,
}: {
  console_: (typeof consoles)[0];
  color: string;
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: R.surface,
        borderRadius: 16,
        borderTop: `6px solid ${color}`,
        padding: "24px 20px 20px",
        cursor: "pointer",
        transition: "all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275)",
        transform: hovered ? "scale(1.03)" : "scale(1)",
        boxShadow: hovered
          ? `0 12px 32px ${color}33, 0 4px 12px rgba(0,0,0,0.08)`
          : "0 2px 8px rgba(0,0,0,0.06)",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 8,
        }}
      >
        <h3
          style={{
            fontSize: 18,
            fontWeight: 700,
            color: R.text,
            margin: 0,
          }}
        >
          {console_.name}
        </h3>
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: "50%",
            background: color,
            color: "#ffffff",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 13,
            fontWeight: 700,
          }}
        >
          {console_.gameCount}
        </div>
      </div>
      <div
        style={{
          fontSize: 13,
          color: R.textSecondary,
        }}
      >
        {console_.abbreviation}
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════
   GAMES PAGE
   ═══════════════════════════════════════ */
function ConsoleDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const con = consoles.find((c) => c.id === id) || consoles[0];
  const color = getConsoleColor(con.id);
  const [activeGenre, setActiveGenre] = useState<string | null>(null);

  const genres = Array.from(new Set(snesGames.map((g) => g.genre)));
  const filteredGames = activeGenre
    ? snesGames.filter((g) => g.genre === activeGenre)
    : snesGames;

  return (
    <PageShell>
      {/* Back + Title */}
      <div style={{ marginBottom: 4 }}>
        <span
          onClick={() => navigate("/")}
          style={{
            fontSize: 13,
            color: R.textSecondary,
            cursor: "pointer",
          }}
        >
          ← Back to Consoles
        </span>
      </div>

      <h1
        style={{
          fontSize: 40,
          fontWeight: 900,
          color: color,
          margin: "0 0 4px",
          letterSpacing: "-0.02em",
        }}
      >
        {con.name}
      </h1>
      <div style={{ fontSize: 14, color: R.textSecondary, marginBottom: 20 }}>
        {snesGames.length} games available
      </div>

      {/* Genre filters */}
      <div
        style={{
          display: "flex",
          gap: 8,
          marginBottom: 28,
          flexWrap: "wrap",
        }}
      >
        <button
          onClick={() => setActiveGenre(null)}
          style={{
            padding: "6px 16px",
            borderRadius: 999,
            border: "none",
            fontSize: 13,
            fontWeight: 600,
            cursor: "pointer",
            fontFamily: R.sans,
            background: activeGenre === null ? R.text : R.surface,
            color: activeGenre === null ? "#ffffff" : R.text,
            transition: "all 0.15s ease",
          }}
        >
          All
        </button>
        {genres.map((genre) => {
          const gc = getGenreColor(genre);
          const isActive = activeGenre === genre;
          return (
            <button
              key={genre}
              onClick={() => setActiveGenre(genre)}
              style={{
                padding: "6px 16px",
                borderRadius: 999,
                border: "none",
                fontSize: 13,
                fontWeight: 600,
                cursor: "pointer",
                fontFamily: R.sans,
                background: isActive ? gc : R.surface,
                color: isActive ? "#ffffff" : gc,
                transition: "all 0.15s ease",
              }}
            >
              {genre}
            </button>
          );
        })}
      </div>

      {/* Games grid */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, 1fr)",
          gap: 20,
        }}
      >
        {filteredGames.map((game) => (
          <GameCard
            key={game.id}
            game={game}
            consoleColor={color}
            onClick={() => navigate(`/proposal/10/game/${game.id}`)}
          />
        ))}
      </div>

      <DotDivider />
    </PageShell>
  );
}

/* ─── Game Card ─── */
function GameCard({
  game,
  consoleColor,
  onClick,
}: {
  game: Game;
  consoleColor: string;
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  const genreColor = getGenreColor(game.genre);

  return (
    <div
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        background: R.surface,
        borderRadius: 16,
        overflow: "hidden",
        cursor: "pointer",
        transition: "all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275)",
        transform: hovered ? "scale(1.03)" : "scale(1)",
        boxShadow: hovered
          ? `0 12px 32px ${consoleColor}33, 0 4px 12px rgba(0,0,0,0.08)`
          : "0 2px 8px rgba(0,0,0,0.06)",
        position: "relative",
      }}
    >
      {/* Genre badge - top right corner */}
      <div
        style={{
          position: "absolute",
          top: 12,
          right: -20,
          background: genreColor,
          color: "#ffffff",
          fontSize: 10,
          fontWeight: 700,
          padding: "3px 24px",
          transform: "rotate(35deg)",
          zIndex: 2,
          letterSpacing: "0.04em",
          textTransform: "uppercase" as const,
        }}
      >
        {game.genre.split(" ")[0]}
      </div>

      {/* Cover */}
      <div
        style={{
          width: "100%",
          height: 200,
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
            transition: "transform 0.3s ease",
            transform: hovered ? "scale(1.08)" : "scale(1)",
          }}
        />
      </div>

      {/* Content */}
      <div style={{ padding: "14px 16px 16px" }}>
        <h3
          style={{
            fontSize: 16,
            fontWeight: 700,
            color: R.text,
            margin: "0 0 8px",
          }}
        >
          {game.title}
          {game.isFavorite && (
            <span style={{ color: R.red, marginLeft: 6 }}>
              ♥
            </span>
          )}
        </h3>
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span
            style={{
              background: `${genreColor}18`,
              color: genreColor,
              padding: "3px 10px",
              borderRadius: 999,
              fontSize: 11,
              fontWeight: 600,
            }}
          >
            {game.genre}
          </span>
          {game.totalPlayTime > 0 && (
            <span
              style={{
                fontSize: 11,
                color: R.textSecondary,
              }}
            >
              {formatPlayTime(game.totalPlayTime)}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════
   GAME DETAIL PAGE
   ═══════════════════════════════════════ */
function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id) || snesGames[0];
  const con = consoles.find((c) => c.id === game.consoleId) || consoles[0];
  const consoleColor = getConsoleColor(con.id);
  const genreColor = getGenreColor(game.genre);
  const [playHovered, setPlayHovered] = useState(false);

  const metaCards = [
    { label: "Developer", value: game.developer, bg: "#FDE8E8", color: R.red },
    { label: "Publisher", value: game.publisher, bg: "#DBEAFE", color: R.blue },
    { label: "Genre", value: game.genre, bg: "#D1FAE5", color: R.pipeGreen },
    {
      label: "Rating",
      value: `${"★".repeat(Math.round(game.rating / 2))}${"☆".repeat(5 - Math.round(game.rating / 2))}`,
      bg: "#FEF3C7",
      color: "#B45309",
    },
  ];

  return (
    <PageShell>
      {/* Breadcrumb */}
      <div style={{ marginBottom: 4 }}>
        <span
          onClick={() => navigate("/")}
          style={{ fontSize: 13, color: R.textSecondary, cursor: "pointer" }}
        >
          Consoles
        </span>
        <span style={{ fontSize: 13, color: R.textSecondary }}> / </span>
        <span
          onClick={() => navigate(-1)}
          style={{ fontSize: 13, color: consoleColor, cursor: "pointer", fontWeight: 600 }}
        >
          {con.name}
        </span>
        <span style={{ fontSize: 13, color: R.textSecondary }}>
          {" "}
          / {game.title}
        </span>
      </div>

      {/* Main content */}
      <div
        style={{
          display: "flex",
          gap: 36,
          alignItems: "flex-start",
          marginTop: 20,
        }}
      >
        {/* Cover */}
        <div
          style={{
            width: 260,
            flexShrink: 0,
          }}
        >
          <div
            style={{
              background: R.surface,
              borderRadius: 16,
              border: `4px solid ${consoleColor}`,
              overflow: "hidden",
              boxShadow: `0 8px 24px ${consoleColor}33`,
            }}
          >
            <img
              src={game.coverUrl}
              alt={game.title}
              style={{
                width: "100%",
                height: 340,
                objectFit: "cover",
                display: "block",
              }}
            />
          </div>
        </div>

        {/* Details */}
        <div style={{ flex: 1 }}>
          <h1
            style={{
              fontSize: 36,
              fontWeight: 900,
              color: R.text,
              margin: "0 0 4px",
              letterSpacing: "-0.02em",
            }}
          >
            {game.title}
          </h1>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              marginBottom: 20,
            }}
          >
            <span
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: consoleColor,
              }}
            >
              {con.name}
            </span>
            <span
              style={{
                color: genreColor,
                fontWeight: 600,
                fontSize: 14,
              }}
            >
              {game.genre}
            </span>
            <span
              style={{
                fontSize: 14,
                color: R.textSecondary,
              }}
            >
              {game.releaseDate.split("-")[0]}
            </span>
          </div>

          {/* Star rating */}
          <div
            style={{
              fontSize: 22,
              color: R.gold,
              marginBottom: 20,
              letterSpacing: 2,
            }}
          >
            {"★".repeat(Math.round(game.rating / 2))}
            {"☆".repeat(5 - Math.round(game.rating / 2))}
            <span
              style={{
                fontSize: 14,
                color: R.textSecondary,
                marginLeft: 8,
                letterSpacing: 0,
              }}
            >
              {game.rating}/10
            </span>
          </div>

          {/* Colorful meta cards */}
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(4, 1fr)",
              gap: 10,
              marginBottom: 24,
            }}
          >
            {metaCards.map((card) => (
              <div
                key={card.label}
                style={{
                  background: card.bg,
                  borderRadius: 12,
                  padding: "12px 14px",
                }}
              >
                <div
                  style={{
                    fontSize: 10,
                    fontWeight: 600,
                    color: card.color,
                    textTransform: "uppercase" as const,
                    letterSpacing: "0.06em",
                    marginBottom: 4,
                  }}
                >
                  {card.label}
                </div>
                <div
                  style={{
                    fontSize: 13,
                    fontWeight: 700,
                    color: card.color,
                  }}
                >
                  {card.value}
                </div>
              </div>
            ))}
          </div>

          {/* More details */}
          <div
            style={{
              display: "flex",
              gap: 20,
              fontSize: 13,
              color: R.textSecondary,
              marginBottom: 20,
            }}
          >
            <span>
              <strong style={{ color: R.text }}>
                {game.players}
              </strong>{" "}
              player{game.players > 1 ? "s" : ""}
            </span>
            <span>
              <strong style={{ color: R.text }}>
                {formatFileSize(game.fileSize)}
              </strong>
            </span>
            {game.totalPlayTime > 0 && (
              <span>
                <strong style={{ color: R.text }}>
                  {formatPlayTime(game.totalPlayTime)}
                </strong>{" "}
                played
              </span>
            )}
            {game.lastPlayedAt && (
              <span>
                Last played{" "}
                <strong style={{ color: R.text }}>
                  {new Date(game.lastPlayedAt).toLocaleDateString("en-US", {
                    month: "short",
                    day: "numeric",
                  })}
                </strong>
              </span>
            )}
          </div>

          {/* Description */}
          <p
            style={{
              fontSize: 15,
              color: R.text,
              lineHeight: 1.7,
              margin: "0 0 24px",
              maxWidth: 550,
            }}
          >
            {game.description}
          </p>

          {/* Play button */}
          <button
            onMouseEnter={() => setPlayHovered(true)}
            onMouseLeave={() => setPlayHovered(false)}
            style={{
              background: playHovered ? "#CC1F1F" : R.red,
              color: "#ffffff",
              border: "none",
              borderRadius: 999,
              padding: "14px 48px",
              fontSize: 18,
              fontWeight: 800,
              cursor: "pointer",
              fontFamily: R.sans,
              letterSpacing: "0.04em",
              boxShadow: playHovered
                ? "0 8px 24px rgba(229, 37, 33, 0.4)"
                : "0 4px 12px rgba(229, 37, 33, 0.25)",
              transition: "all 0.2s ease",
              transform: playHovered ? "scale(1.05)" : "scale(1)",
            }}
          >
            PLAY!
          </button>
        </div>
      </div>

      <DotDivider />

      {/* Screenshots */}
      {game.screenshotUrls.length > 0 && (
        <div>
          <h3
            style={{
              fontSize: 20,
              fontWeight: 800,
              color: R.text,
              marginBottom: 16,
            }}
          >
            Screenshots
          </h3>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: `repeat(${Math.min(game.screenshotUrls.length, 4)}, 1fr)`,
              gap: 12,
            }}
          >
            {game.screenshotUrls.map((url, i) => {
              const frameColors = [R.red, R.blue, R.green, R.gold, R.purple, R.orange];
              const frameColor = frameColors[i % frameColors.length];
              return (
                <div
                  key={i}
                  style={{
                    border: `3px solid ${frameColor}`,
                    borderRadius: 12,
                    overflow: "hidden",
                    boxShadow: `0 4px 12px ${frameColor}22`,
                  }}
                >
                  <img
                    src={url}
                    alt={`Screenshot ${i + 1}`}
                    style={{
                      width: "100%",
                      height: 120,
                      objectFit: "cover",
                      display: "block",
                    }}
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}
    </PageShell>
  );
}

/* ─── Main Export ─── */
export function Proposal10() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<ConsoleDetailPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
