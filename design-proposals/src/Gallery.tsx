import { Link } from "react-router-dom";

const proposals = [
  {
    id: 1,
    name: "Nintendo Switch",
    description: "Light, playful, colorful. Inspired by the eShop and Nintendo.com. Joy-Con neon pops, bright red accent, friendly rounded UI.",
    colors: ["#ffffff", "#e60012", "#00c3e3", "#ff3c28"],
    tag: "LIGHT",
  },
  {
    id: 2,
    name: "Xbox Game Pass",
    description: "Dark and confident with Xbox green. Horizontal scroll categories, bold typography, green glow on interactives.",
    colors: ["#0e0e0e", "#107c10", "#ffffff", "#1a1a1a"],
    tag: "DARK",
  },
  {
    id: 3,
    name: "PlayStation Store",
    description: "Dark navy with PS blue. Featured hero banners, clean horizontal rows, professional gaming aesthetic.",
    colors: ["#000000", "#003791", "#ffffff", "#0f1923"],
    tag: "DARK",
  },
  {
    id: 4,
    name: "Netflix",
    description: "Black with bold red. Full-bleed hero, horizontal scroll rows, hover zoom reveals. Content is king.",
    colors: ["#141414", "#e50914", "#ffffff", "#000000"],
    tag: "DARK",
  },
  {
    id: 5,
    name: "Google / Material You",
    description: "Light, cheerful, accessible. Material 3 design with rounded containers, tonal surfaces, warm coral seed color.",
    colors: ["#ffffff", "#FF6F61", "#e8def8", "#f5f0ff"],
    tag: "LIGHT",
  },
  {
    id: 6,
    name: "Apple TV+",
    description: "Light, premium, content-forward. Clean white, huge hero images, Apple-style typography, subtle gray sections.",
    colors: ["#ffffff", "#0071e3", "#f5f5f7", "#1d1d1f"],
    tag: "LIGHT",
  },
  {
    id: 7,
    name: "Disney+ / Cartoon",
    description: "Rich saturated colors meet warm charm. Disney blue, magic purple, gold, enchanted teal. Pixar palette meets streaming.",
    colors: ["#1a1d29", "#113CCF", "#7B61FF", "#F5C518"],
    tag: "COLORFUL",
  },
  {
    id: 8,
    name: "Spotify",
    description: "Dark with vibrant green. Dense content, compact rows, sidebar navigation, album-art-driven dynamic feel.",
    colors: ["#121212", "#1db954", "#ffffff", "#282828"],
    tag: "DARK",
  },
  {
    id: 9,
    name: "Notion / Linear",
    description: "Light productivity aesthetic. Clean white, subtle warm gray, database-like views, minimal accent color. Tool for game lovers.",
    colors: ["#ffffff", "#5e5ce6", "#f7f6f3", "#37352f"],
    tag: "LIGHT",
  },
  {
    id: 10,
    name: "Retro Nintendo Colorful",
    description: "Bright, joyful, nostalgic. Mario red, Luigi green, Sonic blue, coin gold. Chunky rounded elements, bold colors, pure fun.",
    colors: ["#faf5eb", "#E52521", "#43B047", "#0066FF"],
    tag: "LIGHT",
  },
];

export function Gallery() {
  return (
    <div style={{
      minHeight: "100vh",
      background: "linear-gradient(135deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%)",
      color: "#e2e8f0",
      fontFamily: "'Inter', system-ui, sans-serif",
    }}>
      {/* Header */}
      <header style={{
        padding: "48px 48px 32px",
        maxWidth: 1400,
        margin: "0 auto",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 16, marginBottom: 12 }}>
          <div style={{
            width: 48, height: 48, borderRadius: 14,
            background: "linear-gradient(135deg, #6366f1, #8b5cf6)",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 24,
          }}>
            🎮
          </div>
          <h1 style={{ fontSize: 36, fontWeight: 800, margin: 0, letterSpacing: "-0.02em" }}>
            Spela Design Proposals
          </h1>
        </div>
        <p style={{ fontSize: 18, color: "#94a3b8", margin: 0, maxWidth: 700 }}>
          10 unique design directions for the Spela game library interface.
          Each proposal shows the console list, game grid, and game detail page.
        </p>
      </header>

      {/* Grid */}
      <main style={{
        padding: "0 48px 64px",
        maxWidth: 1400,
        margin: "0 auto",
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(380px, 1fr))",
        gap: 24,
      }}>
        {proposals.map((p) => (
          <Link
            key={p.id}
            to={`/proposal/${p.id}`}
            style={{ textDecoration: "none", color: "inherit" }}
          >
            <div
              style={{
                background: "rgba(30, 30, 50, 0.6)",
                backdropFilter: "blur(20px)",
                border: "1px solid rgba(255,255,255,0.08)",
                borderRadius: 20,
                padding: 28,
                transition: "all 0.3s ease",
                cursor: "pointer",
                position: "relative",
                overflow: "hidden",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = "translateY(-4px)";
                e.currentTarget.style.borderColor = "rgba(255,255,255,0.15)";
                e.currentTarget.style.boxShadow = "0 20px 60px rgba(0,0,0,0.4)";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = "translateY(0)";
                e.currentTarget.style.borderColor = "rgba(255,255,255,0.08)";
                e.currentTarget.style.boxShadow = "none";
              }}
            >
              {/* Color preview strip */}
              <div style={{ display: "flex", gap: 8, marginBottom: 20 }}>
                {p.colors.map((color, i) => (
                  <div
                    key={i}
                    style={{
                      width: i === 0 ? 48 : 32,
                      height: 32,
                      borderRadius: 8,
                      background: color,
                      border: "1px solid rgba(255,255,255,0.1)",
                    }}
                  />
                ))}
              </div>

              {/* Proposal number + tag */}
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
                <div style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: "#6366f1",
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                }}>
                  Proposal #{p.id}
                </div>
                {"tag" in p && (
                  <span style={{
                    fontSize: 10,
                    fontWeight: 700,
                    padding: "2px 8px",
                    borderRadius: 6,
                    background: p.tag === "LIGHT" ? "rgba(255,255,255,0.15)" : p.tag === "COLORFUL" ? "rgba(251,191,36,0.2)" : "rgba(100,100,140,0.2)",
                    color: p.tag === "LIGHT" ? "#e2e8f0" : p.tag === "COLORFUL" ? "#fbbf24" : "#94a3b8",
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}>
                    {p.tag}
                  </span>
                )}
              </div>

              <h2 style={{
                fontSize: 22,
                fontWeight: 700,
                margin: "0 0 10px 0",
                letterSpacing: "-0.01em",
              }}>
                {p.name}
              </h2>

              <p style={{
                fontSize: 14,
                color: "#94a3b8",
                margin: 0,
                lineHeight: 1.6,
              }}>
                {p.description}
              </p>

              {/* Arrow indicator */}
              <div style={{
                position: "absolute",
                bottom: 24,
                right: 24,
                width: 36,
                height: 36,
                borderRadius: 10,
                background: "rgba(99, 102, 241, 0.15)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 18,
                color: "#6366f1",
              }}>
                →
              </div>
            </div>
          </Link>
        ))}
      </main>
    </div>
  );
}
