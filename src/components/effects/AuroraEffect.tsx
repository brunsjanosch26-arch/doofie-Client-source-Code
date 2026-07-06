import { useEffect, useRef } from "react";

interface AuroraEffectProps {
  opacity?: number;
  speed?: number;
}

export function AuroraEffect({ opacity = 0.5, speed = 1 }: AuroraEffectProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let animFrame: number;
    let t = 0;

    const resize = () => {
      canvas.width = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
    };
    resize();

    const observer = new ResizeObserver(resize);
    observer.observe(canvas);

    const BANDS = [
      { colorA: "#00ffaa", colorB: "#00ddff", phase: 0,   freq: 0.6,  amp: 120, yFrac: 0.15 },
      { colorA: "#00aaff", colorB: "#8800ff", phase: 1.2, freq: 0.4,  amp: 90,  yFrac: 0.28 },
      { colorA: "#22ffcc", colorB: "#00aaff", phase: 2.4, freq: 0.8,  amp: 70,  yFrac: 0.38 },
      { colorA: "#aa00ff", colorB: "#ff0088", phase: 0.8, freq: 0.5,  amp: 100, yFrac: 0.20 },
      { colorA: "#00ffdd", colorB: "#6600ff", phase: 3.1, freq: 0.35, amp: 80,  yFrac: 0.45 },
    ];

    function draw() {
      const w = canvas.width;
      const h = canvas.height;
      ctx.clearRect(0, 0, w, h);
      t += 0.008 * speed;

      for (const band of BANDS) {
        const yBase = h * band.yFrac;
        const alpha = opacity * (0.4 + 0.35 * Math.sin(t * 0.7 + band.phase));

        ctx.save();
        ctx.globalAlpha = alpha;

        const grad = ctx.createLinearGradient(0, 0, w, 0);
        grad.addColorStop(0,   "transparent");
        grad.addColorStop(0.2, band.colorA + "88");
        grad.addColorStop(0.5, band.colorB + "cc");
        grad.addColorStop(0.8, band.colorA + "88");
        grad.addColorStop(1,   "transparent");

        ctx.beginPath();
        ctx.moveTo(0, yBase);
        for (let x = 0; x <= w; x += 6) {
          const y =
            yBase +
            Math.sin((x / w) * Math.PI * 2 * band.freq + t + band.phase) * band.amp +
            Math.sin((x / w) * Math.PI * 4 * band.freq - t * 0.6 + band.phase) * (band.amp * 0.4);
          ctx.lineTo(x, y);
        }
        ctx.lineTo(w, -h * 0.2);
        ctx.lineTo(0, -h * 0.2);
        ctx.closePath();
        ctx.fillStyle = grad;
        ctx.fill();
        ctx.restore();
      }

      // Stars
      ctx.save();
      ctx.globalAlpha = opacity * 0.6;
      for (let i = 0; i < 80; i++) {
        // pseudo-random but stable — use index as seed
        const sx = ((i * 137.508) % 1) * canvas.width;
        const sy = ((i * 97.31) % 0.6) * canvas.height;
        const r = ((i * 53.1) % 1) * 1.5 + 0.3;
        const twinkle = 0.5 + 0.5 * Math.sin(t * 3 + i * 0.7);
        ctx.globalAlpha = opacity * 0.3 * twinkle;
        ctx.beginPath();
        ctx.arc(sx, sy, r, 0, Math.PI * 2);
        ctx.fillStyle = "#ffffff";
        ctx.fill();
      }
      ctx.restore();

      animFrame = requestAnimationFrame(draw);
    }
    draw();

    return () => {
      cancelAnimationFrame(animFrame);
      observer.disconnect();
    };
  }, [opacity, speed]);

  return (
    <canvas
      ref={canvasRef}
      style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none" }}
    />
  );
}
