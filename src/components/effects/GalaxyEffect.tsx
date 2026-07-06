import { useEffect, useRef } from "react";

interface GalaxyEffectProps {
  opacity?: number;
  speed?: number;
}

export function GalaxyEffect({ opacity = 0.5, speed = 1 }: GalaxyEffectProps) {
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
      initStars();
    };

    interface Star {
      angle: number; dist: number; size: number; speed: number;
      color: string; brightness: number; arm: number;
    }
    let stars: Star[] = [];
    const COLORS = ["#ffffff", "#aac8ff", "#ffd6aa", "#c8aaff", "#aaffdd"];
    const ARM_COUNT = 3;

    function initStars() {
      stars = [];
      for (let i = 0; i < 500; i++) {
        const arm = Math.floor(Math.random() * ARM_COUNT);
        const dist = Math.pow(Math.random(), 0.5) * Math.min(canvas.width, canvas.height) * 0.48;
        const armAngle = (arm / ARM_COUNT) * Math.PI * 2;
        const spread = (Math.random() - 0.5) * 0.8;
        const curvature = dist * 0.012;
        stars.push({
          angle: armAngle + curvature + spread,
          dist,
          size: Math.random() * 2.2 + 0.3,
          speed: (0.0004 + Math.random() * 0.0003) * speed / (dist * 0.005 + 1),
          color: COLORS[Math.floor(Math.random() * COLORS.length)],
          brightness: 0.4 + Math.random() * 0.6,
          arm,
        });
      }
    }

    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);

    function drawNebula(cx: number, cy: number) {
      const r = Math.min(canvas.width, canvas.height) * 0.35;
      for (let i = 0; i < ARM_COUNT; i++) {
        const baseAngle = (i / ARM_COUNT) * Math.PI * 2 + t * 0.05 * speed;
        const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
        grad.addColorStop(0, `rgba(120,60,200,${opacity * 0.25})`);
        grad.addColorStop(0.5, `rgba(60,80,180,${opacity * 0.1})`);
        grad.addColorStop(1, "transparent");
        ctx.save();
        ctx.globalAlpha = 1;
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.restore();
        // Arm glow
        ctx.save();
        ctx.globalAlpha = opacity * 0.12;
        ctx.translate(cx, cy);
        ctx.rotate(baseAngle);
        const armGrad = ctx.createRadialGradient(r * 0.3, 0, 0, r * 0.3, 0, r * 0.6);
        armGrad.addColorStop(0, "#8844ff88");
        armGrad.addColorStop(1, "transparent");
        ctx.ellipse(r * 0.3, 0, r * 0.6, r * 0.2, 0, 0, Math.PI * 2);
        ctx.fillStyle = armGrad;
        ctx.fill();
        ctx.restore();
      }
    }

    function drawCore(cx: number, cy: number) {
      // Bright core glow
      const coreR = Math.min(canvas.width, canvas.height) * 0.08;
      const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, coreR * 4);
      grad.addColorStop(0, `rgba(255,220,180,${opacity * 0.9})`);
      grad.addColorStop(0.15, `rgba(180,120,255,${opacity * 0.5})`);
      grad.addColorStop(0.4, `rgba(80,60,180,${opacity * 0.2})`);
      grad.addColorStop(1, "transparent");
      ctx.save();
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.restore();
    }

    function draw() {
      t += 0.016;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const cx = canvas.width / 2;
      const cy = canvas.height / 2;

      drawNebula(cx, cy);
      drawCore(cx, cy);

      // Stars
      for (const s of stars) {
        s.angle += s.speed;
        const x = cx + Math.cos(s.angle) * s.dist;
        const y = cy + Math.sin(s.angle) * s.dist * 0.45;

        const twinkle = 0.7 + 0.3 * Math.sin(t * 4 + s.dist);
        ctx.save();
        ctx.globalAlpha = opacity * s.brightness * twinkle;

        if (s.size > 1.5) {
          // Bigger stars get a glow
          const glow = ctx.createRadialGradient(x, y, 0, x, y, s.size * 3);
          glow.addColorStop(0, s.color);
          glow.addColorStop(1, "transparent");
          ctx.fillStyle = glow;
          ctx.beginPath();
          ctx.arc(x, y, s.size * 3, 0, Math.PI * 2);
          ctx.fill();
        }

        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.arc(x, y, s.size * 0.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }

      // Shooting star occasionally
      if (Math.sin(t * 0.3) > 0.97) {
        const angle = Math.PI * 0.2;
        const sx = Math.random() * canvas.width;
        const sy = Math.random() * canvas.height * 0.5;
        const len = 60 + Math.random() * 80;
        const sg = ctx.createLinearGradient(sx, sy, sx + Math.cos(angle) * len, sy + Math.sin(angle) * len);
        sg.addColorStop(0, `rgba(255,255,255,${opacity * 0.9})`);
        sg.addColorStop(1, "transparent");
        ctx.save();
        ctx.globalAlpha = opacity * 0.8;
        ctx.strokeStyle = sg;
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(sx, sy);
        ctx.lineTo(sx + Math.cos(angle) * len, sy + Math.sin(angle) * len);
        ctx.stroke();
        ctx.restore();
      }

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
