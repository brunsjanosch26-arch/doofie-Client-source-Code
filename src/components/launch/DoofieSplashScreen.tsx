import { useEffect, useRef, useState } from "react";
import { useThemeStore } from "../../store/useThemeStore";

/**
 * Animierter Doofie-Splashscreen beim Launcher-Start.
 * Logo fliegt rein, Partikel-Ring, Schriftzug baut sich auf, dann Fade-out.
 */
export function DoofieSplashScreen({ onDone }: { onDone: () => void }) {
  const { accentColor } = useThemeStore();
  const [phase, setPhase] = useState<"in" | "hold" | "out">("in");
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const title = "doofieclient";

  useEffect(() => {
    const t1 = setTimeout(() => setPhase("hold"), 700);
    const t2 = setTimeout(() => setPhase("out"), 2100);
    const t3 = setTimeout(onDone, 2700);
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
  }, [onDone]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d")!;
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const color = accentColor.value;
    const cx = canvas.width / 2;
    const cy = canvas.height / 2 - 40;
    let t = 0;
    let frame: number;

    interface Spark { angle: number; dist: number; speed: number; size: number; }
    const sparks: Spark[] = Array.from({ length: 60 }, () => ({
      angle: Math.random() * Math.PI * 2,
      dist: 60 + Math.random() * 120,
      speed: 0.005 + Math.random() * 0.02,
      size: 1 + Math.random() * 2.5,
    }));

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      t += 0.016;

      // Glow im Zentrum
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, 260);
      g.addColorStop(0, `${color}30`);
      g.addColorStop(1, "transparent");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Partikel kreisen ums Logo
      for (const s of sparks) {
        s.angle += s.speed;
        const wobble = Math.sin(t * 2 + s.dist) * 8;
        const x = cx + Math.cos(s.angle) * (s.dist + wobble);
        const y = cy + Math.sin(s.angle) * (s.dist + wobble) * 0.6;
        ctx.globalAlpha = 0.3 + Math.sin(t * 3 + s.angle) * 0.3;
        ctx.fillStyle = color;
        ctx.beginPath();
        ctx.arc(x, y, s.size, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      frame = requestAnimationFrame(draw);
    };
    draw();
    return () => cancelAnimationFrame(frame);
  }, [accentColor.value]);

  return (
    <div style={{
      position: "fixed", inset: 0, zIndex: 999999,
      background: "#050508",
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
      opacity: phase === "out" ? 0 : 1,
      transform: phase === "out" ? "scale(1.06)" : "scale(1)",
      transition: "opacity 0.6s ease, transform 0.6s ease",
      pointerEvents: phase === "out" ? "none" : "auto",
    }}>
      <canvas ref={canvasRef} style={{ position: "absolute", inset: 0 }} />

      <div style={{ position: "relative", zIndex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: "24px", marginTop: "-40px" }}>
        <img
          src="/logo.png"
          alt="Doofie"
          style={{
            width: "120px", height: "120px", objectFit: "contain",
            filter: `drop-shadow(0 0 40px ${accentColor.value})`,
            animation: "splashLogoIn 0.7s cubic-bezier(0.34,1.56,0.64,1), splashFloat 3s 0.7s ease-in-out infinite",
          }}
        />

        {/* Schriftzug Buchstabe fuer Buchstabe */}
        <div className="font-minecraft" style={{ display: "flex", fontSize: "36px", fontWeight: 700, letterSpacing: "3px", color: "#fff", textShadow: `0 0 20px ${accentColor.value}` }}>
          {title.split("").map((ch, i) => (
            <span key={i} style={{
              animation: `splashLetterIn 0.4s ${0.3 + i * 0.05}s cubic-bezier(0.34,1.56,0.64,1) both`,
            }}>{ch}</span>
          ))}
        </div>

        {/* Lade-Linie */}
        <div style={{ width: "220px", height: "3px", background: "rgba(255,255,255,0.08)", borderRadius: "3px", overflow: "hidden" }}>
          <div style={{
            height: "100%", borderRadius: "3px",
            background: `linear-gradient(90deg, ${accentColor.dark}, ${accentColor.value}, ${accentColor.light})`,
            boxShadow: `0 0 10px ${accentColor.value}`,
            animation: "splashBar 1.8s 0.4s ease both",
          }} />
        </div>
      </div>

      <style>{`
        @keyframes splashLogoIn {
          from { transform: scale(0.2) rotate(-12deg); opacity: 0; }
          to { transform: scale(1) rotate(0deg); opacity: 1; }
        }
        @keyframes splashFloat {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-8px); }
        }
        @keyframes splashLetterIn {
          from { transform: translateY(18px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
        @keyframes splashBar {
          from { width: 0%; }
          to { width: 100%; }
        }
      `}</style>
    </div>
  );
}
