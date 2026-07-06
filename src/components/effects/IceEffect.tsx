import { useEffect, useRef } from "react";

interface IceEffectProps {
  opacity?: number;
  speed?: number;
}

export function IceEffect({ opacity = 0.45, speed = 1 }: IceEffectProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let t = 0;
    let animFrame: number;

    const resize = () => {
      canvas.width = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
      initCrystals();
    };

    interface Crystal {
      x: number; y: number; angle: number; len: number;
      depth: number; alpha: number;
    }
    interface Snowflake {
      x: number; y: number; vx: number; vy: number; size: number; alpha: number;
    }
    let crystals: Crystal[] = [];
    let snowflakes: Snowflake[] = [];

    function initCrystals() {
      crystals = [];
      for (let i = 0; i < 18; i++) {
        crystals.push({
          x: (i / 18) * canvas.width + (Math.random() - 0.5) * 80,
          y: Math.random() * canvas.height,
          angle: Math.random() * Math.PI * 2,
          len: 30 + Math.random() * 80,
          depth: 2 + Math.floor(Math.random() * 3),
          alpha: 0.15 + Math.random() * 0.3,
        });
      }
      snowflakes = [];
      for (let i = 0; i < 80; i++) {
        snowflakes.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          vx: (Math.random() - 0.5) * 0.5,
          vy: 0.3 + Math.random() * 0.8,
          size: 0.5 + Math.random() * 2.5,
          alpha: 0.4 + Math.random() * 0.6,
        });
      }
    }

    resize();
    const observer = new ResizeObserver(() => {
      canvas.width = canvas.offsetWidth;
      canvas.height = canvas.offsetHeight;
      initCrystals();
    });
    observer.observe(canvas);

    function drawCrystalBranch(cx: number, cy: number, angle: number, len: number, depth: number, baseAlpha: number) {
      if (depth === 0 || len < 4) return;
      const ex = cx + Math.cos(angle) * len;
      const ey = cy + Math.sin(angle) * len;

      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(ex, ey);
      ctx.globalAlpha = baseAlpha * opacity;
      ctx.stroke();

      // Side branches
      const branchLen = len * 0.5;
      const branchAngle = Math.PI / 3;
      for (const dir of [-1, 1]) {
        drawCrystalBranch(
          cx + Math.cos(angle) * len * 0.5,
          cy + Math.sin(angle) * len * 0.5,
          angle + dir * branchAngle,
          branchLen,
          depth - 1,
          baseAlpha * 0.7
        );
      }
    }

    function drawCrystals() {
      ctx.save();
      ctx.lineWidth = 1;
      ctx.strokeStyle = "#aaddff";
      for (const c of crystals) {
        const shimmer = 0.7 + 0.3 * Math.sin(t * 1.5 + c.x * 0.01);
        for (let arm = 0; arm < 6; arm++) {
          drawCrystalBranch(c.x, c.y, c.angle + (arm / 6) * Math.PI * 2, c.len, c.depth, c.alpha * shimmer);
        }
      }
      ctx.restore();
    }

    function drawFrost() {
      const w = canvas.width;
      const h = canvas.height;
      // Corners
      const positions = [[0, 0], [w, 0], [0, h], [w, h]];
      for (const [px, py] of positions) {
        const r = Math.min(w, h) * 0.35;
        const grad = ctx.createRadialGradient(px, py, 0, px, py, r);
        grad.addColorStop(0, `rgba(180,220,255,${opacity * 0.25})`);
        grad.addColorStop(0.4, `rgba(120,190,255,${opacity * 0.1})`);
        grad.addColorStop(1, "transparent");
        ctx.save();
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, w, h);
        ctx.restore();
      }
    }

    function drawSnowflakes() {
      for (const s of snowflakes) {
        s.x += s.vx + Math.sin(t + s.y * 0.02) * 0.3;
        s.y += s.vy * speed;
        if (s.y > canvas.height + 10) {
          s.y = -10;
          s.x = Math.random() * canvas.width;
        }
        ctx.save();
        ctx.globalAlpha = s.alpha * opacity;
        ctx.fillStyle = "#ddeeff";
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }
    }

    function drawAmbient() {
      const w = canvas.width;
      const h = canvas.height;
      const grad = ctx.createLinearGradient(0, 0, w, h);
      grad.addColorStop(0, `rgba(100,180,255,${opacity * 0.08})`);
      grad.addColorStop(0.5, `rgba(180,220,255,${opacity * 0.04})`);
      grad.addColorStop(1, `rgba(80,140,220,${opacity * 0.08})`);
      ctx.save();
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
      ctx.restore();
    }

    function draw() {
      t += 0.016;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      drawAmbient();
      drawFrost();
      drawCrystals();
      drawSnowflakes();
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
