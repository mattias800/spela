import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import { consoles, snesGames, formatPlayTime, formatFileSize } from "@/mock-data";

// ─── PlayStation Store Inspired Design ──────────────────────────────────────
// Dark, polished, professional gaming storefront. Deep navy surfaces,
// vivid blue accents, clean typography.

const ps = {
  bg: "#000000",
  surface: "#0f1923",
  card: "#15202b",
  cardHover: "#1c2d3d",
  blue: "#003791",
  blueLight: "#00d4ff",
  blueGlow: "rgba(0, 212, 255, 0.15)",
  blueGlowStrong: "rgba(0, 212, 255, 0.3)",
  text: "#ffffff",
  textSecondary: "#8e8e8e",
  textDim: "#5a5a5a",
  divider: "rgba(255, 255, 255, 0.06)",
};

const fontStack = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";

// ─── Layout ──────────────────────────────────────────────────────────────────

function Layout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();

  return (
    <div style={{
      minHeight: "100vh",
      background: ps.bg,
      fontFamily: fontStack,
      color: ps.text,
    }}>
      {/* Top nav bar */}
      <header style={{
        background: ps.surface,
        padding: "0 40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        height: 60,
        borderBottom: `1px solid ${ps.divider}`,
        position: "sticky",
        top: 0,
        zIndex: 100,
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 40 }}>
          <div
            onClick={() => navigate("/")}
            style={{
              fontSize: 22,
              fontWeight: 700,
              color: ps.text,
              cursor: "pointer",
              letterSpacing: "0.04em",
            }}
          >
            Spela
          </div>
          <nav style={{ display: "flex", gap: 4 }}>
            {["Games", "Consoles", "Favorites"].map((label, i) => (
              <button
                key={label}
                onClick={() => { if (label === "Consoles" || label === "Games") navigate("/"); }}
                style={{
                  background: i === 1 ? "rgba(0, 55, 145, 0.4)" : "transparent",
                  color: i === 1 ? ps.blueLight : ps.textSecondary,
                  border: "none",
                  borderRadius: 6,
                  padding: "8px 18px",
                  fontSize: 14,
                  fontWeight: 500,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  transition: "all 0.2s ease",
                  letterSpacing: "0.01em",
                }}
                onMouseEnter={(e) => {
                  if (i !== 1) {
                    e.currentTarget.style.color = ps.text;
                    e.currentTarget.style.background = "rgba(255, 255, 255, 0.05)";
                  }
                }}
                onMouseLeave={(e) => {
                  if (i !== 1) {
                    e.currentTarget.style.color = ps.textSecondary;
                    e.currentTarget.style.background = "transparent";
                  }
                }}
              >
                {label}
              </button>
            ))}
          </nav>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <a
            href="/"
            onClick={(e) => { e.preventDefault(); navigate("/"); }}
            style={{
              fontSize: 13,
              color: ps.textSecondary,
              textDecoration: "none",
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = ps.blueLight; }}
            onMouseLeave={(e) => { e.currentTarget.style.color = ps.textSecondary; }}
          >
            &larr; Back to Gallery
          </a>
          {/* User avatar */}
          <div style={{
            width: 34,
            height: 34,
            borderRadius: "50%",
            background: `linear-gradient(135deg, ${ps.blue}, ${ps.blueLight})`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 14,
            fontWeight: 600,
            color: ps.text,
          }}>
            U
          </div>
        </div>
      </header>

      <main>{children}</main>
    </div>
  );
}

// ─── Consoles Page ───────────────────────────────────────────────────────────

function ConsolesPage() {
  const navigate = useNavigate();
  const featured = consoles[0];

  return (
    <Layout>
      {/* Hero banner */}
      <div
        onClick={() => navigate(`console/${featured.id}`)}
        style={{
          position: "relative",
          height: 360,
          background: `linear-gradient(135deg, ${ps.blue} 0%, ${ps.surface} 60%, ${ps.bg} 100%)`,
          display: "flex",
          alignItems: "center",
          padding: "0 60px",
          cursor: "pointer",
          overflow: "hidden",
        }}
      >
        {/* Decorative circles */}
        <div style={{
          position: "absolute",
          right: 80,
          top: "50%",
          transform: "translateY(-50%)",
          width: 300,
          height: 300,
          borderRadius: "50%",
          border: `1px solid rgba(0, 212, 255, 0.08)`,
        }} />
        <div style={{
          position: "absolute",
          right: 130,
          top: "50%",
          transform: "translateY(-50%)",
          width: 200,
          height: 200,
          borderRadius: "50%",
          border: `1px solid rgba(0, 212, 255, 0.05)`,
        }} />

        <div style={{ position: "relative", zIndex: 1, maxWidth: 600 }}>
          <div style={{
            fontSize: 12,
            fontWeight: 600,
            color: ps.blueLight,
            textTransform: "uppercase",
            letterSpacing: "0.12em",
            marginBottom: 12,
          }}>
            Featured Platform
          </div>
          <h1 style={{
            fontSize: 52,
            fontWeight: 700,
            margin: "0 0 8px 0",
            letterSpacing: "-0.02em",
            lineHeight: 1.1,
          }}>
            {featured.name}
          </h1>
          <p style={{
            fontSize: 16,
            color: ps.textSecondary,
            margin: "0 0 28px 0",
            lineHeight: 1.5,
          }}>
            {featured.gameCount} games ready to play
          </p>
          <button style={{
            background: ps.blue,
            color: ps.text,
            border: "none",
            borderRadius: 8,
            padding: "14px 36px",
            fontSize: 16,
            fontWeight: 600,
            fontFamily: fontStack,
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            gap: 8,
            transition: "all 0.2s ease",
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "#004ab5";
              e.currentTarget.style.transform = "translateY(-1px)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = ps.blue;
              e.currentTarget.style.transform = "translateY(0)";
            }}
          >
            Browse
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      {/* All Platforms */}
      <div style={{ padding: "48px 60px" }}>
        <div style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 28,
        }}>
          <h2 style={{
            fontSize: 22,
            fontWeight: 600,
            margin: 0,
            letterSpacing: "-0.01em",
          }}>
            All Platforms
          </h2>
          <span style={{
            fontSize: 13,
            color: ps.textSecondary,
          }}>
            {consoles.length} platforms
          </span>
        </div>

        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
          gap: 20,
        }}>
          {consoles.map((console) => (
            <div
              key={console.id}
              onClick={() => navigate(`console/${console.id}`)}
              style={{
                background: ps.card,
                borderRadius: 12,
                overflow: "hidden",
                cursor: "pointer",
                transition: "all 0.25s ease",
                border: "2px solid transparent",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = ps.blue;
                e.currentTarget.style.boxShadow = `0 0 20px ${ps.blueGlow}, 0 8px 32px rgba(0,0,0,0.3)`;
                e.currentTarget.style.transform = "translateY(-4px)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = "transparent";
                e.currentTarget.style.boxShadow = "none";
                e.currentTarget.style.transform = "translateY(0)";
              }}
            >
              {/* Color gradient top half */}
              <div style={{
                height: 110,
                background: `linear-gradient(135deg, ${console.colorTheme}99, ${console.colorTheme}33, ${ps.card})`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                position: "relative",
              }}>
                <span style={{
                  fontSize: 36,
                  fontWeight: 700,
                  color: "rgba(255, 255, 255, 0.25)",
                  letterSpacing: "0.06em",
                }}>
                  {console.abbreviation}
                </span>
              </div>

              {/* Info bottom */}
              <div style={{ padding: "16px 20px 20px" }}>
                <h3 style={{
                  fontSize: 16,
                  fontWeight: 600,
                  margin: "0 0 6px 0",
                  color: ps.text,
                }}>
                  {console.name}
                </h3>
                <div style={{
                  fontSize: 13,
                  color: ps.textSecondary,
                }}>
                  {console.gameCount} games
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </Layout>
  );
}

// ─── Games Page ──────────────────────────────────────────────────────────────

function GamesPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const currentConsole = consoles.find((c) => c.id === id) || consoles[0];
  const featured = snesGames[0];

  return (
    <Layout>
      {/* Blue header banner */}
      <div style={{
        background: `linear-gradient(135deg, ${ps.blue}, ${ps.surface})`,
        padding: "36px 60px",
        position: "relative",
        overflow: "hidden",
      }}>
        <div style={{
          position: "absolute",
          right: 60,
          top: "50%",
          transform: "translateY(-50%)",
          width: 180,
          height: 180,
          borderRadius: "50%",
          border: `1px solid rgba(0, 212, 255, 0.08)`,
        }} />
        <button
          onClick={() => navigate("/")}
          style={{
            background: "none",
            border: "none",
            color: "rgba(255,255,255,0.6)",
            fontSize: 13,
            fontFamily: fontStack,
            cursor: "pointer",
            padding: 0,
            marginBottom: 12,
            display: "flex",
            alignItems: "center",
            gap: 6,
          }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          All Platforms
        </button>
        <h1 style={{
          fontSize: 36,
          fontWeight: 700,
          margin: 0,
          letterSpacing: "-0.01em",
        }}>
          {currentConsole.name}
        </h1>
        <p style={{
          fontSize: 14,
          color: "rgba(255,255,255,0.5)",
          margin: "6px 0 0",
        }}>
          {snesGames.length} games available
        </p>
      </div>

      {/* Filter/sort row */}
      <div style={{
        background: ps.surface,
        padding: "12px 60px",
        display: "flex",
        alignItems: "center",
        gap: 12,
        borderBottom: `1px solid ${ps.divider}`,
      }}>
        {["All", "Favorites", "RPG", "Platformer", "Action"].map((filter, i) => (
          <button
            key={filter}
            style={{
              background: i === 0 ? "rgba(0, 55, 145, 0.5)" : "rgba(255,255,255,0.05)",
              color: i === 0 ? ps.blueLight : ps.textSecondary,
              border: "none",
              borderRadius: 20,
              padding: "6px 16px",
              fontSize: 13,
              fontWeight: 500,
              fontFamily: fontStack,
              cursor: "pointer",
              transition: "all 0.2s ease",
            }}
          >
            {filter}
          </button>
        ))}
      </div>

      <div style={{ padding: "40px 60px" }}>
        {/* Featured game - large card */}
        <div
          onClick={() => navigate(`../game/${featured.id}`)}
          style={{
            display: "flex",
            background: ps.card,
            borderRadius: 14,
            overflow: "hidden",
            marginBottom: 40,
            cursor: "pointer",
            transition: "all 0.25s ease",
            border: "2px solid transparent",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.borderColor = ps.blue;
            e.currentTarget.style.boxShadow = `0 0 24px ${ps.blueGlow}`;
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.borderColor = "transparent";
            e.currentTarget.style.boxShadow = "none";
          }}
        >
          <img
            src={featured.coverUrl}
            alt={featured.title}
            style={{
              width: 200,
              aspectRatio: "3/4",
              objectFit: "cover",
              display: "block",
            }}
          />
          <div style={{ padding: "28px 32px", flex: 1 }}>
            <div style={{
              fontSize: 11,
              fontWeight: 600,
              color: ps.blueLight,
              textTransform: "uppercase",
              letterSpacing: "0.12em",
              marginBottom: 10,
            }}>
              Featured
            </div>
            <h2 style={{
              fontSize: 28,
              fontWeight: 700,
              margin: "0 0 10px 0",
            }}>
              {featured.title}
            </h2>
            <p style={{
              fontSize: 14,
              color: ps.textSecondary,
              margin: "0 0 16px 0",
              lineHeight: 1.6,
              maxWidth: 500,
            }}>
              {featured.description}
            </p>
            <div style={{ display: "flex", gap: 8 }}>
              <span style={{
                background: "rgba(0, 55, 145, 0.4)",
                color: ps.blueLight,
                fontSize: 12,
                fontWeight: 500,
                padding: "4px 12px",
                borderRadius: 4,
              }}>
                {featured.genre}
              </span>
              <span style={{
                background: "rgba(255,255,255,0.06)",
                color: ps.textSecondary,
                fontSize: 12,
                fontWeight: 500,
                padding: "4px 12px",
                borderRadius: 4,
              }}>
                {featured.rating}/10
              </span>
            </div>
          </div>
        </div>

        {/* Games grid */}
        <h3 style={{
          fontSize: 18,
          fontWeight: 600,
          margin: "0 0 20px 0",
        }}>
          All Games
        </h3>

        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(190px, 1fr))",
          gap: 20,
        }}>
          {snesGames.map((game) => (
            <div
              key={game.id}
              onClick={() => navigate(`../game/${game.id}`)}
              style={{
                borderRadius: 10,
                overflow: "hidden",
                cursor: "pointer",
                transition: "all 0.25s ease",
                position: "relative",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "translateY(-6px)";
                e.currentTarget.style.boxShadow = `0 12px 40px rgba(0,0,0,0.4)`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "translateY(0)";
                e.currentTarget.style.boxShadow = "none";
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
                {/* Dark gradient scrim at bottom */}
                <div style={{
                  position: "absolute",
                  bottom: 0,
                  left: 0,
                  right: 0,
                  height: "60%",
                  background: "linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 100%)",
                }} />
                {/* Title overlaid */}
                <div style={{
                  position: "absolute",
                  bottom: 0,
                  left: 0,
                  right: 0,
                  padding: "12px 14px",
                }}>
                  <h4 style={{
                    fontSize: 14,
                    fontWeight: 600,
                    margin: "0 0 4px 0",
                    color: ps.text,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}>
                    {game.title}
                  </h4>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <span style={{
                      fontSize: 11,
                      color: ps.blueLight,
                      fontWeight: 500,
                    }}>
                      {game.genre}
                    </span>
                    {game.isFavorite && (
                      <svg width="12" height="12" viewBox="0 0 24 24" fill={ps.blueLight} stroke="none">
                        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
                      </svg>
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </Layout>
  );
}

// ─── Game Detail Page ────────────────────────────────────────────────────────

function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id) || snesGames[0];

  return (
    <Layout>
      {/* Full-width blurred background hero */}
      <div style={{
        position: "relative",
        minHeight: 500,
        overflow: "hidden",
      }}>
        {/* Blurred background image */}
        <div style={{
          position: "absolute",
          inset: -40,
          backgroundImage: `url(${game.coverUrl})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          filter: "blur(40px) brightness(0.3)",
          transform: "scale(1.2)",
        }} />
        {/* Dark overlay */}
        <div style={{
          position: "absolute",
          inset: 0,
          background: "linear-gradient(to bottom, rgba(0,0,0,0.4) 0%, rgba(0,0,0,0.8) 100%)",
        }} />

        {/* Content */}
        <div style={{
          position: "relative",
          zIndex: 1,
          padding: "40px 60px 50px",
        }}>
          {/* Back nav */}
          <button
            onClick={() => navigate(`../console/${game.consoleId}`)}
            style={{
              background: "none",
              border: "none",
              color: "rgba(255,255,255,0.5)",
              fontSize: 13,
              fontFamily: fontStack,
              cursor: "pointer",
              padding: 0,
              marginBottom: 32,
              display: "flex",
              alignItems: "center",
              gap: 6,
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = ps.blueLight; }}
            onMouseLeave={(e) => { e.currentTarget.style.color = "rgba(255,255,255,0.5)"; }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7" />
            </svg>
            Back to {game.consoleName}
          </button>

          {/* Two column: cover + info */}
          <div style={{
            display: "flex",
            gap: 48,
            alignItems: "flex-start",
          }}>
            {/* Large cover */}
            <div style={{ flexShrink: 0, width: 280 }}>
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "3/4",
                  objectFit: "cover",
                  display: "block",
                  borderRadius: 12,
                  boxShadow: "0 16px 48px rgba(0,0,0,0.5)",
                }}
              />
            </div>

            {/* Info */}
            <div style={{ flex: 1, minWidth: 0, paddingTop: 8 }}>
              <div style={{
                display: "flex",
                gap: 8,
                marginBottom: 16,
              }}>
                <span style={{
                  background: "rgba(0, 55, 145, 0.5)",
                  color: ps.blueLight,
                  fontSize: 12,
                  fontWeight: 500,
                  padding: "4px 14px",
                  borderRadius: 4,
                }}>
                  {game.genre}
                </span>
                <span style={{
                  background: "rgba(255,255,255,0.08)",
                  color: ps.textSecondary,
                  fontSize: 12,
                  fontWeight: 500,
                  padding: "4px 14px",
                  borderRadius: 4,
                }}>
                  {game.consoleName}
                </span>
              </div>

              <h1 style={{
                fontSize: 42,
                fontWeight: 700,
                margin: "0 0 8px 0",
                letterSpacing: "-0.02em",
                lineHeight: 1.15,
              }}>
                {game.title}
              </h1>

              <div style={{
                fontSize: 14,
                color: ps.textSecondary,
                marginBottom: 28,
              }}>
                {game.developer} &middot; {game.releaseDate}
              </div>

              {/* Action buttons */}
              <div style={{
                display: "flex",
                gap: 14,
                marginBottom: 32,
              }}>
                <button style={{
                  background: ps.blue,
                  color: ps.text,
                  border: "none",
                  borderRadius: 8,
                  padding: "16px 44px",
                  fontSize: 16,
                  fontWeight: 600,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  transition: "all 0.2s ease",
                }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "#004ab5";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = ps.blue;
                  }}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <polygon points="5 3 19 12 5 21 5 3" />
                  </svg>
                  Play
                </button>
                <button style={{
                  background: "rgba(255,255,255,0.08)",
                  color: ps.text,
                  border: "1px solid rgba(255,255,255,0.15)",
                  borderRadius: 8,
                  padding: "16px 28px",
                  fontSize: 14,
                  fontWeight: 500,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  transition: "all 0.2s ease",
                }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.12)";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "rgba(255,255,255,0.08)";
                  }}
                >
                  {game.isFavorite ? "Remove from Favorites" : "Add to Favorites"}
                </button>
              </div>

              {/* Description */}
              <p style={{
                fontSize: 15,
                color: "rgba(255,255,255,0.7)",
                lineHeight: 1.7,
                margin: "0 0 32px 0",
                maxWidth: 550,
              }}>
                {game.description}
              </p>

              {/* Metadata panels */}
              <div style={{
                display: "grid",
                gridTemplateColumns: "repeat(4, 1fr)",
                gap: 12,
              }}>
                {[
                  { label: "Rating", value: `${game.rating}/10` },
                  { label: "Players", value: game.players.toString() },
                  { label: "Play Time", value: formatPlayTime(game.totalPlayTime) },
                  { label: "Size", value: formatFileSize(game.fileSize) },
                ].map((stat) => (
                  <div key={stat.label} style={{
                    background: "rgba(15, 25, 35, 0.8)",
                    borderRadius: 8,
                    padding: "14px 16px",
                    border: `1px solid ${ps.divider}`,
                  }}>
                    <div style={{
                      fontSize: 20,
                      fontWeight: 700,
                      color: ps.text,
                      marginBottom: 2,
                    }}>
                      {stat.value}
                    </div>
                    <div style={{
                      fontSize: 11,
                      color: ps.textSecondary,
                      textTransform: "uppercase",
                      letterSpacing: "0.08em",
                    }}>
                      {stat.label}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Game info section below */}
      <div style={{ padding: "40px 60px" }}>
        {/* Details grid */}
        <h3 style={{
          fontSize: 18,
          fontWeight: 600,
          margin: "0 0 20px 0",
        }}>
          Details
        </h3>
        <div style={{
          background: ps.card,
          borderRadius: 12,
          padding: "4px 0",
          marginBottom: 40,
        }}>
          {[
            { label: "Developer", value: game.developer },
            { label: "Publisher", value: game.publisher },
            { label: "Release Date", value: game.releaseDate },
            { label: "Genre", value: game.genre },
            { label: "File Size", value: formatFileSize(game.fileSize) },
            { label: "Last Played", value: game.lastPlayedAt ? new Date(game.lastPlayedAt).toLocaleDateString() : "Never" },
          ].map((item, i, arr) => (
            <div key={item.label} style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              padding: "14px 24px",
              borderBottom: i < arr.length - 1 ? `1px solid ${ps.divider}` : "none",
            }}>
              <span style={{
                fontSize: 14,
                color: ps.textSecondary,
              }}>
                {item.label}
              </span>
              <span style={{
                fontSize: 14,
                fontWeight: 500,
                color: ps.text,
              }}>
                {item.value}
              </span>
            </div>
          ))}
        </div>

        {/* Screenshots */}
        {game.screenshotUrls.length > 0 && (
          <div>
            <h3 style={{
              fontSize: 18,
              fontWeight: 600,
              margin: "0 0 20px 0",
            }}>
              Screenshots
            </h3>
            <div style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))",
              gap: 16,
            }}>
              {game.screenshotUrls.map((url, i) => (
                <div key={i} style={{
                  borderRadius: 10,
                  overflow: "hidden",
                  boxShadow: "0 4px 16px rgba(0,0,0,0.3)",
                }}>
                  <img
                    src={url}
                    alt={`${game.title} screenshot ${i + 1}`}
                    style={{
                      width: "100%",
                      display: "block",
                    }}
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </Layout>
  );
}

// ─── Main Export ──────────────────────────────────────────────────────────────

export function Proposal3() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<GamesPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
