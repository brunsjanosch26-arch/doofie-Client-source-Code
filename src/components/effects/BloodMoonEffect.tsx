import { useEffect, useRef } from "react";

interface BloodMoonEffectProps {
  opacity?: number;
  speed?: number;
}

export function BloodMoonEffect({ opacity = 0.5, speed = 1 }: BloodMoonEffectProps) {
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
    };
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);

    interface Ember {
      x: number; y: number; vy: number; vx: number;
      size: number; alpha: number; color: string; life: number;
    }
    const COLORS = ["#ff2200", "#ff6600", "#ff4400", "#cc0000", "#ff8800"];
    const embers: Ember[] = [];
    const spawnEmber = (): Ember => ({
      x: Math.random() * canvas.width,
      y: canvas.height + 10,
      vy: -(0.5 + Math.random() * 2.5) * speed,
      vx: (Math.random() - 0.5) * 1.5,
      size: 1 + Math.random() * 3,
      alpha: 0.6 + Math.random() * 0.4,
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
      life: 1,
    });
    for (let i = 0; i < 120; i++) {
      const e = spawnEmber();
      e.y = Math.random() * canvas.height;
      e.life = Math.random();
      embers.push(e);
    }

    function drawMoon(cx: number) {
      const my = canvas.height * 0.3;
      const r = Math.min(canvas.width, canvas.height) * 0.14;

      // Outer blood glow
      const outerGlow = ctx.createRadialGradient(cx, my, 0, cx, my, r * 4);
      outerGlow.addColorStop(0,   `rgba(200,0,0,${opacity * 0.4})`);
      outerGlow.addColorStop(0.3, `rgba(150,0,0,${opacity * 0.2})`);
      outerGlow.addColorStop(0.6, `rgba(80,0,0,${opacity * 0.1})`);
      outerGlow.addColorStop(1,   "transparent");
      ctx.save();
      ctx.fillStyle = outerGlow;
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.restore();

      // Moon body
      const moonGrad = ctx.createRadialGradient(cx - r * 0.25, my - r * 0.25, 0, cx, my, r);
      moonGrad.addColorStop(0,   `rgba(255,80,30,${opacity})`);
      moonGrad.addColorStop(0.5, `rgba(200,20,10,${opacity})`);
      moonGrad.addColorStop(1,   `rgba(100,0,0,${opacity})`);
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, my, r, 0, Math.PI * 2);
      ctx.fillStyle = moonGrad;
      ctx.fill();

      // Moon craters (dark spots)
      ctx.globalAlpha = opacity * 0.3;
      ctx.fillStyle = "#330000";
      const craters = [[cx - r*0.25, my - r*0.1, r*0.12], [cx + r*0.2, my + r*0.2, r*0.08], [cx - r*0.05, my + r*0.25, r*0.05]];
      for (const [cx2, cy2, cr] of craters) {
        ctx.beginPath();
        ctx.arc(cx2, cy2, cr, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();

      // Atmospheric halo pulse
      const pulse = 0.8 + 0.2 * Math.sin(t * 1.5);
      const halo = ctx.createRadialGradient(cx, my, r * 0.9, cx, my, r * (1.4 + 0.1 * pulse));
      halo.addColorStop(0, `rgba(255,50,0,${opacity * 0.3})`);
      halo.addColorStop(1, "transparent");
      ctx.save();
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(cx, my, r * (1.4 + 0.1 * pulse), 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }

    function drawGround() {
      const h = canvas.height;
      const w = canvas.width;
      const grad = ctx.createLinearGradient(0, h * 0.7, 0, h);
      grad.addColorStop(0, "transparent");
      grad.addColorStop(1, `rgba(60,0,0,${opacity * 0.4})`);
      ctx.save();
      ctx.fillStyle = grad;
      ctx.fillRect(0, h * 0.7, w, h * 0.3);
      ctx.restore();
    }

    function drawEmbers() {
      for (const e of embers) {
        e.y += e.vy;
        e.x += e.vx + Math.sin(t * 2 + e.life * 10) * 0.3;
        e.life -= 0.004 * speed;
        if (e.life <= 0 || e.y < -10) {
          Object.assign(e, spawnEmber());
        }
        ctx.save();
        ctx.globalAlpha = e.alpha * e.life * opacity;

        // Glow
        const glow = ctx.createRadialGradient(e.x, e.y, 0, e.x, e.y, e.size * 3);
        glow.addColorStop(0, e.color);
        glow.addColorStop(1, "transparent");
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(e.x, e.y, e.size * 3, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = "#ffcc88";
        ctx.beginPath();
        ctx.arc(e.x, e.y, e.size * 0.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }
    }

    function drawFog() {
      const w = canvas.width;
      const h = canvas.height;
      for (let i = 0; i < 3; i++) {
        const x = ((t * 15 * speed * (0.5 + i * 0.3) + i * w * 0.4) % (w * 1.5)) - w * 0.25;
        const y = h * (0.5 + i * 0.15);
        const grad = ctx.createRadialGradient(x, y, 0, x, y, w * 0.4);
        grad.addColorStop(0, `rgba(80,0,0,${opacity * 0.08})`);
        grad.addColorStop(1, "transparent");
        ctx.save();
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, w, h);
        ctx.restore();
      }
    }

    function draw() {
      t += 0.016;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const cx = canvas.width * 0.65;

      drawGround();
      drawMoon(cx);
      drawFog();
      drawEmbers();

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
