import { useEffect, useRef, useState } from "react";
import { useThemeStore } from "../../store/useThemeStore";

interface CinematicLaunchScreenProps {
  profileName: string;
  onDone: () => void;
}

const LOADING_PHRASES = [
  "Doofie poliert die Blöcke...",
  "Doofie füttert die Creeper...",
  "Doofie sortiert die Chunks...",
  "Doofie zähmt die Endermen...",
  "Doofie schärft die Spitzhacke...",
  "Doofie backt den Kuchen (keine Lüge)...",
  "Doofie verlegt Redstone...",
  "Minecraft wird gestartet...",
];

export function CinematicLaunchScreen({ profileName, onDone }: CinematicLaunchScreenProps) {
  const { accentColor } = useThemeStore();
  const [phase, setPhase] = useState<"intro" | "loading" | "outro">("intro");
  const [progress, setProgress] = useState(0);
  const [loadingText, setLoadingText] = useState(LOADING_PHRASES[0]);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const interval = setInterval(() => {
      setLoadingText(LOADING_PHRASES[Math.floor(Math.random() * LOADING_PHRASES.length)]);
    }, 900);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d")!;
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const color = accentColor.value;
    let t = 0;
    let frame: number;

    interface Particle { x: number; y: number; vx: number; vy: number; size: number; alpha: number; }
    const particles: Particle[] = [];
    for (let i = 0; i < 120; i++) {
      particles.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        vx: (Math.random() - 0.5) * 2,
        vy: (Math.random() - 0.5) * 2,
        size: 1 + Math.random() * 3,
        alpha: Math.random(),
      });
    }

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      t += 0.02;

      // Background pulse
      const bg = ctx.createRadialGradient(canvas.width / 2, canvas.height / 2, 0, canvas.width / 2, canvas.height / 2, canvas.width * 0.7);
      bg.addColorStop(0, `${color}18`);
      bg.addColorStop(1, "transparent");
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Rotating rings
      for (let r = 0; r < 3; r++) {
        const radius = 80 + r * 60 + Math.sin(t + r) * 10;
        ctx.save();
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate(t * (r % 2 === 0 ? 1 : -1) * 0.5);
        ctx.strokeStyle = color;
        ctx.globalAlpha = 0.3 - r * 0.07;
        ctx.lineWidth = 2 - r * 0.4;
        ctx.setLineDash([20, 15]);
        ctx.beginPath();
        ctx.arc(0, 0, radius, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }

      // Particles
      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0) p.x = canvas.width;
        if (p.x > canvas.width) p.x = 0;
        if (p.y < 0) p.y = canvas.height;
        if (p.y > canvas.height) p.y = 0;
        const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 3);
        g.addColorStop(0, color);
        g.addColorStop(1, "transparent");
        ctx.globalAlpha = p.alpha * 0.6;
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size * 3, 0, Math.PI * 2);
        ctx.fill();
      }

      frame = requestAnimationFrame(draw);
    };
    draw();
    return () => cancelAnimationFrame(frame);
  }, [accentColor.value]);

  // Sequence
  useEffect(() => {
    const t1 = setTimeout(() => setPhase("loading"), 800);
    const t2 = setTimeout(() => setPhase("outro"), 3500);
    const t3 = setTimeout(onDone, 4200);

    let prog = 0;
    const interval = setInterval(() => {
      prog += Math.random() * 8;
      if (prog > 100) prog = 100;
      setProgress(prog);
    }, 100);

    return () => {
      clearTimeout(t1); clearTimeout(t2); clearTimeout(t3);
      clearInterval(interval);
    };
  }, [onDone]);

  return (
    <div style={{
      position: "fixed", inset: 0, zIndex: 99998,
      background: "rgba(0,0,0,0.95)",
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
      transform: phase === "outro" ? "scale(1.05)" : "scale(1)",
      opacity: phase === "outro" ? 0 : 1,
      transition: "opacity 0.7s ease, transform 0.7s ease",
    }}>
      <canvas ref={canvasRef} style={{ position: "absolute", inset: 0 }} />

      <div style={{ position: "relative", zIndex: 1, textAlign: "center", display: "flex", flexDirection: "column", alignItems: "center", gap: "28px" }}>
        {/* Logo */}
        <div style={{
          animation: phase === "intro" ? "popIn 0.6s cubic-bezier(0.34,1.56,0.64,1)" : "logoFloat 3s ease-in-out infinite",
          filter: `drop-shadow(0 0 30px ${accentColor.value})`,
        }}>
          <img src="/logo.png" alt="Doofie" style={{ width: "96px", height: "96px", objectFit: "contain" }} />
        </div>

        <div>
          <div style={{
            fontSize: "13px", fontWeight: 700, letterSpacing: "4px", textTransform: "uppercase",
            color: accentColor.light, marginBottom: "8px",
            opacity: phase === "loading" || phase === "outro" ? 1 : 0,
            transition: "opacity 0.5s",
          }}>
            Starte Profil
          </div>
          <div style={{
            fontSize: "28px", fontWeight: 900, color: "#fff",
            opacity: phase === "loading" || phase === "outro" ? 1 : 0,
            transform: phase === "loading" ? "translateY(0)" : "translateY(10px)",
            transition: "opacity 0.5s, transform 0.5s",
          }}>
            {profileName}
          </div>
        </div>

        {/* Progress bar */}
        <div style={{
          width: "320px", height: "4px", background: "rgba(255,255,255,0.1)", borderRadius: "4px",
          overflow: "hidden",
          opacity: phase === "loading" ? 1 : 0, transition: "opacity 0.5s",
        }}>
          <div style={{
            height: "100%", borderRadius: "4px",
            background: `linear-gradient(90deg, ${accentColor.dark}, ${accentColor.value}, ${accentColor.light})`,
            width: `${progress}%`,
            transition: "width 0.15s ease",
            boxShadow: `0 0 12px ${accentColor.value}`,
          }} />
        </div>

        <div style={{
          fontSize: "12px", color: "rgba(255,255,255,0.3)", letterSpacing: "1px",
          opacity: phase === "loading" ? 1 : 0, transition: "opacity 0.5s",
          minHeight: "18px",
        }}>
          {loadingText}
        </div>
      </div>

      <style>{`
        @keyframes popIn {
          from { transform: scale(0.3); opacity: 0; }
          to { transform: scale(1); opacity: 1; }
        }
        @keyframes logoFloat {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-8px); }
        }
      `}</style>
    </div>
  );
}
