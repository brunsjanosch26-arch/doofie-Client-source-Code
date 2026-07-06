import { useEffect, useRef } from "react";

interface CyberpunkEffectProps {
  opacity?: number;
  speed?: number;
  color?: string;
}

export function CyberpunkEffect({ opacity = 0.4, speed = 1, color = "#ff00aa" }: CyberpunkEffectProps) {
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

    // Falling neon particles
    interface Particle {
      x: number; y: number; vy: number; len: number; alpha: number; width: number;
    }
    const particles: Particle[] = [];
    const spawnParticle = () => ({
      x: Math.random() * canvas.width,
      y: -20,
      vy: (1 + Math.random() * 3) * speed,
      len: 20 + Math.random() * 60,
      alpha: 0.3 + Math.random() * 0.7,
      width: 1 + Math.random() * 2,
    });
    for (let i = 0; i < 40; i++) {
      const p = spawnParticle();
      p.y = Math.random() * canvas.height;
      particles.push(p);
    }

    // Glitch flicker state
    let glitchTimer = 0;
    let glitchActive = false;

    function drawGrid() {
      const w = canvas.width;
      const h = canvas.height;
      const vanishY = h * 0.45;
      const horizonX = w * 0.5;
      const numLines = 20;

      ctx.save();
      ctx.globalAlpha = opacity * 0.35;
      ctx.strokeStyle = color;
      ctx.lineWidth = 0.8;

      // Vertical perspective lines
      for (let i = -numLines; i <= numLines; i++) {
        const x = horizonX + (i / numLines) * w * 0.8;
        const grad = ctx.createLinearGradient(horizonX, vanishY, x, h);
        grad.addColorStop(0, color + "00");
        grad.addColorStop(1, color + "88");
        ctx.strokeStyle = grad;
        ctx.beginPath();
        ctx.moveTo(horizonX, vanishY);
        ctx.lineTo(x, h);
        ctx.stroke();
      }

      // Horizontal lines converging to horizon
      for (let i = 0; i < 14; i++) {
        const progress = i / 14;
        const easedProgress = Math.pow(progress, 2);
        const y = vanishY + easedProgress * (h - vanishY);
        const xLeft  = horizonX - easedProgress * w * 0.8;
        const xRight = horizonX + easedProgress * w * 0.8;
        const alpha = (0.1 + 0.5 * progress) * opacity * 0.4;
        ctx.globalAlpha = alpha;
        ctx.strokeStyle = color;
        ctx.lineWidth = 0.5 + progress;
        ctx.beginPath();
        ctx.moveTo(xLeft, y);
        ctx.lineTo(xRight, y);
        ctx.stroke();
      }
      ctx.restore();
    }

    function drawScanlines() {
      const w = canvas.width;
      const h = canvas.height;
      ctx.save();
      ctx.globalAlpha = opacity * 0.06;
      ctx.fillStyle = "#000000";
      for (let y = 0; y < h; y += 4) {
        ctx.fillRect(0, y, w, 2);
      }
      ctx.restore();

      // Moving scan line
      const scanY = ((t * 60 * speed) % (h + 40)) - 20;
      ctx.save();
      ctx.globalAlpha = opacity * 0.15;
      const scanGrad = ctx.createLinearGradient(0, scanY - 10, 0, scanY + 10);
      scanGrad.addColorStop(0, "transparent");
      scanGrad.addColorStop(0.5, color + "55");
      scanGrad.addColorStop(1, "transparent");
      ctx.fillStyle = scanGrad;
      ctx.fillRect(0, scanY - 10, w, 20);
      ctx.restore();
    }

    function drawParticles() {
      for (const p of particles) {
        p.y += p.vy;
        if (p.y > canvas.height + 40) {
          Object.assign(p, spawnParticle());
          p.x = Math.random() * canvas.width;
        }
        ctx.save();
        ctx.globalAlpha = p.alpha * opacity;
        const grad = ctx.createLinearGradient(p.x, p.y - p.len, p.x, p.y);
        grad.addColorStop(0, "transparent");
        grad.addColorStop(1, color);
        ctx.strokeStyle = grad;
        ctx.lineWidth = p.width;
        ctx.beginPath();
        ctx.moveTo(p.x, p.y - p.len);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();
        ctx.restore();
      }
    }

    function drawGlitch() {
      if (!glitchActive) return;
      const w = canvas.width;
      const h = canvas.height;
      ctx.save();
      const sliceH = 2 + Math.random() * 8;
      const sliceY = Math.random() * h;
      const offset = (Math.random() - 0.5) * 30;
      ctx.globalAlpha = opacity * 0.5;
      const imgData = ctx.getImageData(0, sliceY, w, sliceH);
      ctx.putImageData(imgData, offset, sliceY);
      ctx.restore();
    }

    function draw() {
      t += 0.016;
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      glitchTimer -= 0.016;
      if (glitchTimer <= 0) {
        glitchActive = Math.random() < 0.1;
        glitchTimer = 0.1 + Math.random() * 2;
      }

      drawGrid();
      drawParticles();
      drawScanlines();
      drawGlitch();

      animFrame = requestAnimationFrame(draw);
    }
    draw();

    return () => {
      cancelAnimationFrame(animFrame);
      observer.disconnect();
    };
  }, [opacity, speed, color]);

  return (
    <canvas
      ref={canvasRef}
      style={{ position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none" }}
    />
  );
}
