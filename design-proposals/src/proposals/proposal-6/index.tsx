import { Routes, Route, useNavigate, useParams } from "react-router-dom";
import {
  consoles,
  snesGames,
  formatPlayTime,
  formatFileSize,
} from "@/mock-data";

// ─── Apple TV+ / Apple Arcade Theme (Light) ─────────────────────────
const A = {
  bg: "#ffffff",
  sectionBg: "#f5f5f7",
  textPrimary: "#1d1d1f",
  textSecondary: "#6e6e73",
  blue: "#0071e3",
  blueHover: "#0077ed",
  border: "#d2d2d7",
  borderLight: "#e8e8ed",
  surface: "#ffffff",
  font: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', system-ui, sans-serif",
};

// ─── Apple Layout ────────────────────────────────────────────────────
function AppleLayout({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();

  return (
    <div
      style={{
        minHeight: "100vh",
        background: A.bg,
        fontFamily: A.font,
        color: A.textPrimary,
      }}
    >
      {/* Ultra-minimal top nav */}
      <header
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          padding: "0 48px",
          height: 52,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          borderBottom: `1px solid ${A.borderLight}`,
        }}
      >
        {/* Left: brand + links */}
        <div style={{ display: "flex", alignItems: "center", gap: 40 }}>
          <span
            onClick={() => navigate("/")}
            style={{
              fontSize: 21,
              fontWeight: 600,
              color: A.textPrimary,
              cursor: "pointer",
              letterSpacing: "-0.02em",
            }}
          >
            Spela
          </span>

          <nav style={{ display: "flex", gap: 28 }}>
            {["Games", "Consoles", "Favorites"].map((link) => (
              <span
                key={link}
                onClick={() => navigate("/")}
                style={{
                  fontSize: 14,
                  color: A.textSecondary,
                  cursor: "pointer",
                  fontWeight: 400,
                  transition: "color 0.15s",
                }}
                onMouseEnter={(e) => (e.currentTarget.style.color = A.textPrimary)}
                onMouseLeave={(e) => (e.currentTarget.style.color = A.textSecondary)}
              >
                {link}
              </span>
            ))}
          </nav>
        </div>

        {/* Right: icons */}
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <span
            style={{
              fontSize: 16,
              color: A.textSecondary,
              cursor: "pointer",
            }}
          >
            &#128269;
          </span>
          <div
            style={{
              width: 30,
              height: 30,
              borderRadius: "50%",
              background: A.sectionBg,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 13,
              color: A.textSecondary,
              cursor: "pointer",
              fontWeight: 500,
            }}
          >
            U
          </div>
        </div>
      </header>

      {/* Content */}
      <main>{children}</main>
    </div>
  );
}

// ─── Console List Page ──────────────────────────────────────────────
function ConsolesPage() {
  const navigate = useNavigate();
  const featured = consoles[1]; // SNES as featured

  return (
    <AppleLayout>
      <div
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          padding: "32px 48px 80px",
        }}
      >
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            fontSize: 14,
            color: A.blue,
            textDecoration: "none",
            marginBottom: 32,
            fontWeight: 400,
          }}
        >
          ← Back to Gallery
        </a>

        {/* HUGE HERO - Featured Console */}
        <div
          onClick={() => navigate(`console/${featured.id}`)}
          style={{
            width: "100%",
            height: 400,
            borderRadius: 24,
            background: `linear-gradient(135deg, ${featured.colorTheme}dd, ${featured.colorTheme}88, ${featured.colorTheme}44)`,
            position: "relative",
            overflow: "hidden",
            cursor: "pointer",
            marginBottom: 56,
            display: "flex",
            flexDirection: "column",
            justifyContent: "flex-end",
            padding: 48,
            transition: "transform 0.3s ease",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.transform = "scale(1.005)";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = "scale(1)";
          }}
        >
          {/* Background abstract shapes */}
          <div
            style={{
              position: "absolute",
              top: -60,
              right: -40,
              width: 400,
              height: 400,
              borderRadius: "50%",
              background: "rgba(255,255,255,0.08)",
              pointerEvents: "none",
            }}
          />
          <div
            style={{
              position: "absolute",
              top: 80,
              right: 120,
              width: 200,
              height: 200,
              borderRadius: "50%",
              background: "rgba(255,255,255,0.06)",
              pointerEvents: "none",
            }}
          />

          <div style={{ position: "relative", zIndex: 1 }}>
            <div
              style={{
                fontSize: 14,
                fontWeight: 500,
                color: "rgba(255,255,255,0.7)",
                marginBottom: 8,
                textTransform: "uppercase",
                letterSpacing: "0.08em",
              }}
            >
              Featured Platform
            </div>
            <h2
              style={{
                fontSize: 48,
                fontWeight: 700,
                color: "#ffffff",
                margin: "0 0 12px 0",
                lineHeight: 1.1,
                letterSpacing: "-0.02em",
              }}
            >
              {featured.name}
            </h2>
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                fontSize: 17,
                fontWeight: 500,
                color: "#ffffff",
                opacity: 0.9,
              }}
            >
              Browse {featured.gameCount} Games →
            </span>
          </div>
        </div>

        {/* All Platforms heading */}
        <h2
          style={{
            fontSize: 28,
            fontWeight: 600,
            margin: "0 0 24px 0",
            color: A.textPrimary,
            letterSpacing: "-0.02em",
          }}
        >
          All Platforms
        </h2>

        {/* Console cards grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
            gap: 20,
          }}
        >
          {consoles.map((c) => (
            <div
              key={c.id}
              onClick={() => navigate(`console/${c.id}`)}
              style={{
                cursor: "pointer",
                background: A.surface,
                borderRadius: 20,
                border: `1px solid ${A.border}`,
                overflow: "hidden",
                transition: "all 0.2s ease",
                boxShadow: "0 0 0 rgba(0,0,0,0)",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = "0 4px 20px rgba(0,0,0,0.08)";
                e.currentTarget.style.transform = "translateY(-2px)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = "0 0 0 rgba(0,0,0,0)";
                e.currentTarget.style.transform = "translateY(0)";
              }}
            >
              {/* Thin gradient accent at top */}
              <div
                style={{
                  height: 4,
                  background: `linear-gradient(90deg, ${c.colorTheme}, ${c.colorTheme}66)`,
                }}
              />

              <div style={{ padding: "24px 24px 28px" }}>
                <h3
                  style={{
                    fontSize: 18,
                    fontWeight: 600,
                    margin: "0 0 6px 0",
                    color: A.textPrimary,
                    letterSpacing: "-0.01em",
                  }}
                >
                  {c.name}
                </h3>
                <div
                  style={{
                    fontSize: 14,
                    color: A.textSecondary,
                    fontWeight: 400,
                  }}
                >
                  {c.gameCount} games
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </AppleLayout>
  );
}

// ─── Games Page ─────────────────────────────────────────────────────
function GamesPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const con = consoles.find((c) => c.id === id) ?? consoles[0];

  return (
    <AppleLayout>
      <div
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          padding: "32px 48px 80px",
        }}
      >
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            fontSize: 14,
            color: A.blue,
            textDecoration: "none",
            marginBottom: 16,
            fontWeight: 400,
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
            color: A.textSecondary,
            marginBottom: 8,
          }}
        >
          <span
            onClick={() => navigate("/")}
            style={{ cursor: "pointer", color: A.blue }}
          >
            Platforms
          </span>
          <span>›</span>
          <span style={{ color: A.textPrimary, fontWeight: 500 }}>
            {con.name}
          </span>
        </div>

        {/* Header */}
        <h1
          style={{
            fontSize: 36,
            fontWeight: 700,
            margin: "0 0 4px 0",
            color: A.textPrimary,
            letterSpacing: "-0.02em",
          }}
        >
          {con.name}
        </h1>
        <p
          style={{
            fontSize: 17,
            color: A.textSecondary,
            margin: "0 0 36px 0",
            fontWeight: 400,
          }}
        >
          {snesGames.length} games
        </p>

        {/* Games grid - 5 columns on large, content-forward */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
            gap: 24,
          }}
        >
          {snesGames.map((game) => (
            <div
              key={game.id}
              onClick={() => navigate(`../game/${game.id}`)}
              style={{
                cursor: "pointer",
                transition: "transform 0.2s ease",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "scale(1.03)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "scale(1)";
              }}
            >
              {/* Cover image - the star */}
              <img
                src={game.coverUrl}
                alt={game.title}
                style={{
                  width: "100%",
                  aspectRatio: "3 / 4",
                  objectFit: "cover",
                  display: "block",
                  borderRadius: 16,
                  boxShadow: "0 2px 12px rgba(0,0,0,0.08)",
                }}
              />

              {/* Title below - minimal */}
              <h4
                style={{
                  fontSize: 14,
                  fontWeight: 500,
                  margin: "10px 0 2px 0",
                  color: A.textPrimary,
                  lineHeight: 1.3,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {game.title}
              </h4>
              <div
                style={{
                  fontSize: 13,
                  color: A.textSecondary,
                  fontWeight: 400,
                }}
              >
                {game.genre}
              </div>
            </div>
          ))}
        </div>
      </div>
    </AppleLayout>
  );
}

// ─── Game Detail Page ───────────────────────────────────────────────
function GameDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const game = snesGames.find((g) => g.id === id) ?? snesGames[0];

  return (
    <AppleLayout>
      <div
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          padding: "32px 48px 80px",
        }}
      >
        {/* Back to Gallery */}
        <a
          href="/"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            fontSize: 14,
            color: A.blue,
            textDecoration: "none",
            marginBottom: 16,
            fontWeight: 400,
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
            color: A.textSecondary,
            marginBottom: 32,
          }}
        >
          <span
            onClick={() => navigate("/")}
            style={{ cursor: "pointer", color: A.blue }}
          >
            Platforms
          </span>
          <span>›</span>
          <span
            onClick={() => navigate(`../console/${game.consoleId}`)}
            style={{ cursor: "pointer", color: A.blue }}
          >
            {game.consoleName}
          </span>
          <span>›</span>
          <span style={{ color: A.textPrimary, fontWeight: 500 }}>
            {game.title}
          </span>
        </div>

        {/* Hero: cover + info */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "280px 1fr",
            gap: 48,
            marginBottom: 56,
          }}
        >
          {/* Large cover - centered, rounded, subtle shadow */}
          <div style={{ alignSelf: "start" }}>
            <img
              src={game.coverUrl}
              alt={game.title}
              style={{
                width: "100%",
                aspectRatio: "3 / 4",
                objectFit: "cover",
                display: "block",
                borderRadius: 20,
                boxShadow: "0 8px 30px rgba(0,0,0,0.12)",
              }}
            />
          </div>

          {/* Info */}
          <div>
            {/* Title - 42px, semi-bold */}
            <h1
              style={{
                fontSize: 42,
                fontWeight: 600,
                margin: "0 0 12px 0",
                color: A.textPrimary,
                lineHeight: 1.15,
                letterSpacing: "-0.025em",
              }}
            >
              {game.title}
            </h1>

            {/* Metadata row - clean horizontal with dot separators */}
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 0,
                fontSize: 15,
                color: A.textSecondary,
                marginBottom: 28,
                flexWrap: "wrap",
              }}
            >
              {[
                { label: "Developer", value: game.developer },
                { label: "Genre", value: game.genre },
                { label: "Released", value: game.releaseDate.slice(0, 4) },
                { label: "Rating", value: `${game.rating}/10` },
                { label: "Players", value: `${game.players}` },
              ].map((item, i) => (
                <span key={item.label} style={{ display: "inline-flex", alignItems: "center" }}>
                  {i > 0 && (
                    <span
                      style={{
                        width: 3,
                        height: 3,
                        borderRadius: "50%",
                        background: A.textSecondary,
                        display: "inline-block",
                        margin: "0 12px",
                        opacity: 0.5,
                      }}
                    />
                  )}
                  <span style={{ color: A.textSecondary, fontWeight: 400 }}>
                    {item.value}
                  </span>
                </span>
              ))}
            </div>

            {/* Action buttons */}
            <div
              style={{
                display: "flex",
                gap: 12,
                marginBottom: 32,
              }}
            >
              {/* Apple blue Play button */}
              <button
                style={{
                  padding: "12px 36px",
                  borderRadius: 980,
                  border: "none",
                  background: A.blue,
                  color: "#ffffff",
                  fontSize: 15,
                  fontWeight: 500,
                  fontFamily: A.font,
                  cursor: "pointer",
                  transition: "background 0.15s",
                  letterSpacing: "-0.01em",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = A.blueHover;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = A.blue;
                }}
              >
                Play
              </button>

              {/* Outline Favorite button */}
              <button
                style={{
                  padding: "12px 28px",
                  borderRadius: 980,
                  border: `1px solid ${A.border}`,
                  background: "transparent",
                  color: game.isFavorite ? A.blue : A.textSecondary,
                  fontSize: 15,
                  fontWeight: 500,
                  fontFamily: A.font,
                  cursor: "pointer",
                  transition: "all 0.15s",
                  letterSpacing: "-0.01em",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.borderColor = A.blue;
                  e.currentTarget.style.color = A.blue;
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.borderColor = A.border;
                  e.currentTarget.style.color = game.isFavorite ? A.blue : A.textSecondary;
                }}
              >
                {game.isFavorite ? "\u2665 Favorited" : "\u2661 Favorite"}
              </button>
            </div>

            {/* Additional metadata in clean rows */}
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr",
                gap: "12px 32px",
                paddingTop: 20,
                borderTop: `1px solid ${A.borderLight}`,
              }}
            >
              {[
                { label: "Play Time", value: formatPlayTime(game.totalPlayTime) },
                { label: "File Size", value: formatFileSize(game.fileSize) },
                { label: "Publisher", value: game.publisher },
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
                      fontSize: 13,
                      color: A.textSecondary,
                      fontWeight: 400,
                      marginBottom: 2,
                    }}
                  >
                    {item.label}
                  </div>
                  <div
                    style={{
                      fontSize: 15,
                      color: A.textPrimary,
                      fontWeight: 500,
                    }}
                  >
                    {item.value}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Description - readable column */}
        <div style={{ marginBottom: 56 }}>
          <h2
            style={{
              fontSize: 24,
              fontWeight: 600,
              margin: "0 0 16px 0",
              color: A.textPrimary,
              letterSpacing: "-0.02em",
            }}
          >
            About
          </h2>
          <p
            style={{
              fontSize: 17,
              lineHeight: 1.65,
              color: A.textSecondary,
              margin: 0,
              maxWidth: 640,
              fontWeight: 400,
            }}
          >
            {game.description}
          </p>
        </div>

        {/* Screenshots - full-width row */}
        {game.screenshotUrls.length > 0 && (
          <div style={{ marginBottom: 64 }}>
            <h2
              style={{
                fontSize: 24,
                fontWeight: 600,
                margin: "0 0 20px 0",
                color: A.textPrimary,
                letterSpacing: "-0.02em",
              }}
            >
              Screenshots
            </h2>
            <div
              style={{
                display: "flex",
                gap: 16,
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
                    flexShrink: 0,
                    width: 360,
                    aspectRatio: "16 / 9",
                    objectFit: "cover",
                    display: "block",
                    borderRadius: 16,
                    boxShadow: "0 2px 12px rgba(0,0,0,0.06)",
                  }}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </AppleLayout>
  );
}

// ─── Proposal 6 ─────────────────────────────────────────────────────
export function Proposal6() {
  return (
    <Routes>
      <Route path="/" element={<ConsolesPage />} />
      <Route path="console/:id" element={<GamesPage />} />
      <Route path="game/:id" element={<GameDetailPage />} />
    </Routes>
  );
}
