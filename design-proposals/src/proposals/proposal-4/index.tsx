import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import { consoles, snesGames, formatPlayTime, formatFileSize } from "@/mock-data";

// ─── Netflix Inspired Design ────────────────────────────────────────────────
// Dark, immersive, cinematic. Horizontal scrolling rows, large hero imagery,
// bold red accents. Content fills the screen.

const nx = {
  bg: "#141414",
  surface: "#1a1a1a",
  surfaceHover: "#2a2a2a",
  red: "#e50914",
  redHover: "#f40612",
  text: "#ffffff",
  textSecondary: "#808080",
  textDim: "#5a5a5a",
  divider: "rgba(255, 255, 255, 0.08)",
};

const fontStack = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif";

// ─── Layout ──────────────────────────────────────────────────────────────────

function Layout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();

  return (
    <div style={{
      minHeight: "100vh",
      background: nx.bg,
      fontFamily: fontStack,
      color: nx.text,
    }}>
      {/* Minimal top nav */}
      <header style={{
        padding: "0 48px",
        height: 64,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        zIndex: 100,
        background: "linear-gradient(to bottom, rgba(20, 20, 20, 0.95) 0%, rgba(20, 20, 20, 0) 100%)",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 36 }}>
          <div
            onClick={() => navigate("/")}
            style={{
              fontSize: 26,
              fontWeight: 800,
              color: nx.red,
              cursor: "pointer",
              letterSpacing: "0.02em",
            }}
          >
            SPELA
          </div>
          <nav style={{ display: "flex", gap: 20 }}>
            {["Browse", "Favorites", "Recent"].map((label, i) => (
              <button
                key={label}
                onClick={() => { if (label === "Browse") navigate("/"); }}
                style={{
                  background: "none",
                  border: "none",
                  color: i === 0 ? nx.text : nx.textSecondary,
                  fontSize: 14,
                  fontWeight: i === 0 ? 600 : 400,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  padding: 0,
                  transition: "color 0.2s",
                }}
                onMouseEnter={(e) => { e.currentTarget.style.color = nx.text; }}
                onMouseLeave={(e) => { if (i !== 0) e.currentTarget.style.color = nx.textSecondary; }}
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
              color: nx.textSecondary,
              textDecoration: "none",
              transition: "color 0.2s",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = nx.text; }}
            onMouseLeave={(e) => { e.currentTarget.style.color = nx.textSecondary; }}
          >
            &larr; Back to Gallery
          </a>
          {/* Search icon */}
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={nx.textSecondary} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ cursor: "pointer" }}>
            <circle cx="11" cy="11" r="8" />
            <path d="M21 21l-4.35-4.35" />
          </svg>
          {/* User icon */}
          <div style={{
            width: 30,
            height: 30,
            borderRadius: 4,
            background: `linear-gradient(135deg, ${nx.red}, #b20710)`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 13,
            fontWeight: 600,
          }}>
            U
          </div>
        </div>
      </header>

      <main>{children}</main>
    </div>
  );
}

// ─── Horizontal Scroll Row ───────────────────────────────────────────────────

function ScrollRow({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 40 }}>
      <h3 style={{
        fontSize: 18,
        fontWeight: 600,
        margin: "0 0 14px 0",
        padding: "0 48px",
        letterSpacing: "-0.01em",
      }}>
        {title}
      </h3>
      <div style={{
        display: "flex",
        gap: 10,
        overflowX: "auto",
        padding: "4px 48px 20px",
        scrollbarWidth: "none",
      }}>
        {children}
      </div>
    </div>
  );
}

// ─── Consoles Page ───────────────────────────────────────────────────────────

function ConsolesPage() {
  const navigate = useNavigate();
  const featured = consoles[1]; // SNES as featured

  return (
    <Layout>
      {/* Huge hero - 70% viewport */}
      <div
        onClick={() => navigate(`console/${featured.id}`)}
        style={{
          position: "relative",
          height: "70vh",
          minHeight: 480,
          cursor: "pointer",
          overflow: "hidden",
          background: `linear-gradient(135deg, ${featured.colorTheme}cc 0%, ${featured.colorTheme}44 40%, ${nx.bg} 100%)`,
        }}
      >
        {/* Decorative large text in background */}
        <div style={{
          position: "absolute",
          right: -40,
          top: "50%",
          transform: "translateY(-50%)",
          fontSize: 280,
          fontWeight: 900,
          color: "rgba(255,255,255,0.03)",
          letterSpacing: "-0.04em",
          lineHeight: 1,
          pointerEvents: "none",
          userSelect: "none",
        }}>
          {featured.abbreviation}
        </div>

        {/* Bottom gradient fade */}
        <div style={{
          position: "absolute",
          bottom: 0,
          left: 0,
          right: 0,
          height: "50%",
          background: `linear-gradient(to top, ${nx.bg} 0%, transparent 100%)`,
        }} />

        {/* Content */}
        <div style={{
          position: "absolute",
          bottom: 80,
          left: 48,
          zIndex: 1,
          maxWidth: 600,
        }}>
          <div style={{
            fontSize: 12,
            fontWeight: 600,
            color: nx.red,
            textTransform: "uppercase",
            letterSpacing: "0.15em",
            marginBottom: 16,
          }}>
            Featured
          </div>
          <h1 style={{
            fontSize: 64,
            fontWeight: 800,
            margin: "0 0 12px 0",
            letterSpacing: "-0.03em",
            lineHeight: 1.05,
          }}>
            {featured.name}
          </h1>
          <p style={{
            fontSize: 18,
            color: "rgba(255,255,255,0.6)",
            margin: "0 0 28px 0",
          }}>
            {featured.gameCount} games in your library
          </p>
          <button style={{
            background: nx.red,
            color: nx.text,
            border: "none",
            borderRadius: 4,
            padding: "14px 36px",
            fontSize: 16,
            fontWeight: 700,
            fontFamily: fontStack,
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            gap: 10,
            transition: "all 0.2s ease",
          }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = nx.redHover;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = nx.red;
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" stroke="none">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
            Browse
          </button>
        </div>
      </div>

      {/* Horizontal scrolling row of consoles */}
      <div style={{ marginTop: -20 }}>
        <ScrollRow title="All Platforms">
          {consoles.map((console) => (
            <div
              key={console.id}
              onClick={() => navigate(`console/${console.id}`)}
              style={{
                flexShrink: 0,
                width: 280,
                height: 160,
                borderRadius: 6,
                cursor: "pointer",
                position: "relative",
                overflow: "hidden",
                background: `linear-gradient(135deg, ${console.colorTheme}99, ${console.colorTheme}33, ${nx.surface})`,
                transition: "all 0.3s ease",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "scale(1.05)";
                e.currentTarget.style.boxShadow = "0 8px 32px rgba(0,0,0,0.5)";
                e.currentTarget.style.zIndex = "10";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "scale(1)";
                e.currentTarget.style.boxShadow = "none";
                e.currentTarget.style.zIndex = "1";
              }}
            >
              {/* Large abbreviation watermark */}
              <div style={{
                position: "absolute",
                right: 12,
                top: "50%",
                transform: "translateY(-50%)",
                fontSize: 72,
                fontWeight: 900,
                color: "rgba(255,255,255,0.06)",
                letterSpacing: "-0.02em",
                pointerEvents: "none",
              }}>
                {console.abbreviation}
              </div>
              {/* Bottom gradient */}
              <div style={{
                position: "absolute",
                bottom: 0,
                left: 0,
                right: 0,
                height: "60%",
                background: "linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 100%)",
              }} />
              {/* Info */}
              <div style={{
                position: "absolute",
                bottom: 16,
                left: 18,
              }}>
                <div style={{
                  fontSize: 16,
                  fontWeight: 700,
                  marginBottom: 3,
                }}>
                  {console.name}
                </div>
                <div style={{
                  fontSize: 13,
                  color: "rgba(255,255,255,0.5)",
                }}>
                  {console.gameCount} games
                </div>
              </div>
            </div>
          ))}
        </ScrollRow>
      </div>
    </Layout>
  );
}

// ─── Games Page ──────────────────────────────────────────────────────────────

function GamesPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const currentConsole = consoles.find((c) => c.id === id) || consoles[0];

  // Group games by genre for Netflix-style rows
  const genreMap = new Map<string, typeof snesGames>();
  snesGames.forEach((game) => {
    const list = genreMap.get(game.genre) || [];
    list.push(game);
    genreMap.set(game.genre, list);
  });

  return (
    <Layout>
      {/* Hero with console name */}
      <div style={{
        position: "relative",
        height: 320,
        overflow: "hidden",
        background: `linear-gradient(135deg, ${currentConsole.colorTheme}88 0%, ${currentConsole.colorTheme}22 40%, ${nx.bg} 100%)`,
      }}>
        {/* Big watermark */}
        <div style={{
          position: "absolute",
          right: -20,
          top: "50%",
          transform: "translateY(-50%)",
          fontSize: 200,
          fontWeight: 900,
          color: "rgba(255,255,255,0.03)",
          letterSpacing: "-0.04em",
          pointerEvents: "none",
        }}>
          {currentConsole.abbreviation}
        </div>
        {/* Bottom gradient */}
        <div style={{
          position: "absolute",
          bottom: 0,
          left: 0,
          right: 0,
          height: "60%",
          background: `linear-gradient(to top, ${nx.bg} 0%, transparent 100%)`,
        }} />
        {/* Content */}
        <div style={{
          position: "absolute",
          bottom: 40,
          left: 48,
          zIndex: 1,
        }}>
          <button
            onClick={() => navigate("/")}
            style={{
              background: "none",
              border: "none",
              color: nx.textSecondary,
              fontSize: 13,
              fontFamily: fontStack,
              cursor: "pointer",
              padding: 0,
              marginBottom: 14,
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
            fontSize: 48,
            fontWeight: 800,
            margin: 0,
            letterSpacing: "-0.02em",
          }}>
            {currentConsole.name}
          </h1>
          <p style={{
            fontSize: 15,
            color: nx.textSecondary,
            margin: "8px 0 0",
          }}>
            {snesGames.length} games
          </p>
        </div>
      </div>

      {/* "All Games" row */}
      <ScrollRow title="All Games">
        {snesGames.map((game) => (
          <div
            key={game.id}
            onClick={() => navigate(`../game/${game.id}`)}
            style={{
              flexShrink: 0,
              width: 250,
              cursor: "pointer",
              position: "relative",
              transition: "all 0.3s ease",
              transformOrigin: "center bottom",
              zIndex: 1,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "scale(1.15)";
              e.currentTarget.style.zIndex = "20";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "scale(1)";
              e.currentTarget.style.zIndex = "1";
            }}
          >
            <div style={{
              borderRadius: 6,
              overflow: "hidden",
              position: "relative",
              boxShadow: "0 4px 12px rgba(0,0,0,0.4)",
            }}>
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "16/9",
                  objectFit: "cover",
                  display: "block",
                }}
              />
              {/* Hover overlay with info */}
              <div style={{
                position: "absolute",
                bottom: 0,
                left: 0,
                right: 0,
                background: "linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.4) 60%, transparent 100%)",
                padding: "40px 12px 12px",
              }}>
                <div style={{
                  fontSize: 14,
                  fontWeight: 600,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  marginBottom: 4,
                }}>
                  {game.title}
                </div>
                <div style={{
                  fontSize: 11,
                  color: "rgba(255,255,255,0.5)",
                }}>
                  {game.genre} &middot; {game.rating}/10
                </div>
              </div>
              {/* Favorite indicator */}
              {game.isFavorite && (
                <div style={{
                  position: "absolute",
                  top: 8,
                  right: 8,
                  background: nx.red,
                  borderRadius: 3,
                  padding: "2px 8px",
                  fontSize: 10,
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                }}>
                  Fav
                </div>
              )}
            </div>
          </div>
        ))}
      </ScrollRow>

      {/* Genre rows */}
      {Array.from(genreMap.entries()).map(([genre, games]) => (
        <ScrollRow key={genre} title={genre}>
          {games.map((game) => (
            <div
              key={game.id}
              onClick={() => navigate(`../game/${game.id}`)}
              style={{
                flexShrink: 0,
                width: 250,
                cursor: "pointer",
                position: "relative",
                transition: "all 0.3s ease",
                transformOrigin: "center bottom",
                zIndex: 1,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "scale(1.15)";
                e.currentTarget.style.zIndex = "20";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "scale(1)";
                e.currentTarget.style.zIndex = "1";
              }}
            >
              <div style={{
                borderRadius: 6,
                overflow: "hidden",
                position: "relative",
                boxShadow: "0 4px 12px rgba(0,0,0,0.4)",
              }}>
                <img
                  src={game.coverUrl}
                  alt={game.title}
                  style={{
                    width: "100%",
                    aspectRatio: "16/9",
                    objectFit: "cover",
                    display: "block",
                  }}
                />
                <div style={{
                  position: "absolute",
                  bottom: 0,
                  left: 0,
                  right: 0,
                  background: "linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.4) 60%, transparent 100%)",
                  padding: "40px 12px 12px",
                }}>
                  <div style={{
                    fontSize: 14,
                    fontWeight: 600,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    marginBottom: 4,
                  }}>
                    {game.title}
                  </div>
                  <div style={{
                    fontSize: 11,
                    color: "rgba(255,255,255,0.5)",
                  }}>
                    {game.rating}/10 &middot; {formatPlayTime(game.totalPlayTime)}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </ScrollRow>
      ))}
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
      {/* Hero backdrop - large blurred cover */}
      <div style={{
        position: "relative",
        minHeight: 520,
        overflow: "hidden",
      }}>
        {/* Blurred background */}
        <div style={{
          position: "absolute",
          inset: -60,
          backgroundImage: `url(${game.coverUrl})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          filter: "blur(50px) brightness(0.25) saturate(1.4)",
          transform: "scale(1.3)",
        }} />
        {/* Dark gradient overlay */}
        <div style={{
          position: "absolute",
          inset: 0,
          background: `linear-gradient(to bottom, rgba(20,20,20,0.3) 0%, rgba(20,20,20,0.6) 50%, ${nx.bg} 100%)`,
        }} />

        {/* Content */}
        <div style={{
          position: "relative",
          zIndex: 1,
          padding: "100px 48px 48px",
        }}>
          {/* Back */}
          <button
            onClick={() => navigate(`../console/${game.consoleId}`)}
            style={{
              background: "none",
              border: "none",
              color: nx.textSecondary,
              fontSize: 13,
              fontFamily: fontStack,
              cursor: "pointer",
              padding: 0,
              marginBottom: 28,
              display: "flex",
              alignItems: "center",
              gap: 6,
            }}
            onMouseEnter={(e) => { e.currentTarget.style.color = nx.text; }}
            onMouseLeave={(e) => { e.currentTarget.style.color = nx.textSecondary; }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M19 12H5M12 19l-7-7 7-7" />
            </svg>
            Back to {game.consoleName}
          </button>

          <div style={{
            display: "flex",
            gap: 48,
            alignItems: "flex-start",
          }}>
            {/* Floating cover image */}
            <div style={{ flexShrink: 0, width: 260 }}>
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "3/4",
                  objectFit: "cover",
                  display: "block",
                  borderRadius: 8,
                  boxShadow: "0 16px 60px rgba(0,0,0,0.6)",
                }}
              />
            </div>

            {/* Info */}
            <div style={{ flex: 1, minWidth: 0, paddingTop: 8 }}>
              <h1 style={{
                fontSize: 48,
                fontWeight: 800,
                margin: "0 0 8px 0",
                letterSpacing: "-0.03em",
                lineHeight: 1.1,
              }}>
                {game.title}
              </h1>

              <div style={{
                fontSize: 14,
                color: nx.textSecondary,
                marginBottom: 24,
              }}>
                {game.releaseDate} &middot; {game.developer} &middot; {game.players} player{game.players > 1 ? "s" : ""}
              </div>

              {/* Red Play button */}
              <div style={{
                display: "flex",
                gap: 12,
                marginBottom: 28,
              }}>
                <button style={{
                  background: nx.red,
                  color: nx.text,
                  border: "none",
                  borderRadius: 4,
                  padding: "14px 40px",
                  fontSize: 16,
                  fontWeight: 700,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  transition: "all 0.2s ease",
                }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = nx.redHover; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = nx.red; }}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" stroke="none">
                    <polygon points="5 3 19 12 5 21 5 3" />
                  </svg>
                  Play
                </button>
                <button style={{
                  background: "rgba(255,255,255,0.1)",
                  color: nx.text,
                  border: "none",
                  borderRadius: 4,
                  padding: "14px 24px",
                  fontSize: 14,
                  fontWeight: 500,
                  fontFamily: fontStack,
                  cursor: "pointer",
                  transition: "all 0.2s ease",
                }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.15)"; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = "rgba(255,255,255,0.1)"; }}
                >
                  {game.isFavorite ? "Remove Favorite" : "Add Favorite"}
                </button>
              </div>

              {/* Genre pills */}
              <div style={{
                display: "flex",
                gap: 8,
                marginBottom: 20,
              }}>
                {[game.genre, game.consoleName].map((tag) => (
                  <span key={tag} style={{
                    background: "rgba(255,255,255,0.1)",
                    color: nx.textSecondary,
                    fontSize: 12,
                    fontWeight: 500,
                    padding: "4px 14px",
                    borderRadius: 20,
                  }}>
                    {tag}
                  </span>
                ))}
                <span style={{
                  background: "rgba(255,255,255,0.1)",
                  color: nx.textSecondary,
                  fontSize: 12,
                  fontWeight: 500,
                  padding: "4px 14px",
                  borderRadius: 20,
                }}>
                  {game.rating}/10
                </span>
              </div>

              {/* Description */}
              <p style={{
                fontSize: 15,
                color: "rgba(255,255,255,0.7)",
                lineHeight: 1.7,
                margin: "0 0 28px 0",
                maxWidth: 560,
              }}>
                {game.description}
              </p>

              {/* Metadata row */}
              <div style={{
                display: "flex",
                gap: 32,
              }}>
                {[
                  { label: "Developer", value: game.developer },
                  { label: "Publisher", value: game.publisher },
                  { label: "Play Time", value: formatPlayTime(game.totalPlayTime) },
                  { label: "Size", value: formatFileSize(game.fileSize) },
                ].map((item) => (
                  <div key={item.label}>
                    <div style={{
                      fontSize: 11,
                      color: nx.textDim,
                      textTransform: "uppercase",
                      letterSpacing: "0.08em",
                      marginBottom: 4,
                    }}>
                      {item.label}
                    </div>
                    <div style={{
                      fontSize: 14,
                      fontWeight: 500,
                      color: nx.text,
                    }}>
                      {item.value}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Screenshots as horizontal row */}
      {game.screenshotUrls.length > 0 && (
        <div style={{ padding: "32px 0 48px" }}>
          <ScrollRow title="Screenshots">
            {game.screenshotUrls.map((url, i) => (
              <div key={i} style={{
                flexShrink: 0,
                width: 360,
                borderRadius: 6,
                overflow: "hidden",
                boxShadow: "0 4px 16px rgba(0,0,0,0.4)",
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
          </ScrollRow>
        </div>
      )}

      {/* More details below */}
      <div style={{ padding: "0 48px 60px" }}>
        <h3 style={{
          fontSize: 18,
          fontWeight: 600,
          margin: "0 0 16px 0",
        }}>
          Details
        </h3>
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, 1fr)",
          gap: "16px 40px",
        }}>
          {[
            { label: "Developer", value: game.developer },
            { label: "Publisher", value: game.publisher },
            { label: "Release Date", value: game.releaseDate },
            { label: "Genre", value: game.genre },
            { label: "File Size", value: formatFileSize(game.fileSize) },
            { label: "Last Played", value: game.lastPlayedAt ? new Date(game.lastPlayedAt).toLocaleDateString() : "Never" },
          ].map((item) => (
            <div key={item.label} style={{
              borderBottom: `1px solid ${nx.divider}`,
              paddingBottom: 12,
            }}>
              <div style={{
                fontSize: 12,
                color: nx.textDim,
                textTransform: "uppercase",
                letterSpacing: "0.06em",
                marginBottom: 4,
              }}>
                {item.label}
              </div>
              <div style={{
                fontSize: 14,
                color: nx.text,
                fontWeight: 500,
              }}>
                {item.value}
              </div>
            </div>
          ))}
        </div>
      </div>
    </Layout>
  );
}

// ─── Main Export ──────────────────────────────────────────────────────────────

export function Proposal4() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="/console/:id" element={<GamesPage />} />
      <Route path="/game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
