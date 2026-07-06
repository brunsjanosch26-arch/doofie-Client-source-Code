import { useEffect, useRef } from "react";
import { useThemeStore } from "../../store/useThemeStore";

export function MouseTracker() {
  const { accentColor } = useThemeStore();
  const glowRef = useRef<HTMLDivElement>(null);
  const trailRef = useRef<HTMLDivElement[]>([]);
  const posRef = useRef({ x: -200, y: -200 });
  const frameRef = useRef<number>(0);

  useEffect(() => {
    const TRAIL_COUNT = 8;
    const container = document.createElement("div");
    container.style.cssText = "position:fixed;inset:0;pointer-events:none;z-index:99998;overflow:hidden;";
    document.body.appendChild(container);

    // Main glow cursor
    const glow = document.createElement("div");
    glow.style.cssText = `
      position:absolute;
      width:40px;height:40px;
      border-radius:50%;
      background:radial-gradient(circle, ${accentColor.value}80 0%, ${accentColor.value}20 50%, transparent 70%);
      transform:translate(-50%,-50%);
      pointer-events:none;
      mix-blend-mode:screen;
      transition:width 0.15s,height 0.15s;
    `;
    container.appendChild(glow);
    glowRef.current = glow;

    // Trail dots
    const trails: HTMLDivElement[] = [];
    for (let i = 0; i < TRAIL_COUNT; i++) {
      const dot = document.createElement("div");
      const size = 6 - (i * 0.5);
      const opacity = 1 - (i / TRAIL_COUNT);
      dot.style.cssText = `
        position:absolute;
        width:${size}px;height:${size}px;
        border-radius:50%;
        background:${accentColor.value};
        opacity:${opacity * 0.6};
        transform:translate(-50%,-50%);
        pointer-events:none;
        mix-blend-mode:screen;
      `;
      container.appendChild(dot);
      trails.push(dot);
    }
    trailRef.current = trails;

    // Outer ring
    const ring = document.createElement("div");
    ring.style.cssText = `
      position:absolute;
      width:24px;height:24px;
      border-radius:50%;
      border:1.5px solid ${accentColor.value}60;
      transform:translate(-50%,-50%);
      pointer-events:none;
      transition:transform 0.08s ease-out, left 0.08s ease-out, top 0.08s ease-out;
    `;
    container.appendChild(ring);

    // Trail positions array
    const trailPositions = Array(TRAIL_COUNT).fill({ x: -200, y: -200 });

    const onMouseMove = (e: MouseEvent) => {
      posRef.current = { x: e.clientX, y: e.clientY };
    };

    const animate = () => {
      const { x, y } = posRef.current;
      glow.style.left = `${x}px`;
      glow.style.top = `${y}px`;
      ring.style.left = `${x}px`;
      ring.style.top = `${y}px`;

      // Shift trail
      trailPositions.unshift({ x, y });
      trailPositions.pop();

      trails.forEach((dot, i) => {
        const pos = trailPositions[i] || { x, y };
        dot.style.left = `${pos.x}px`;
        dot.style.top = `${pos.y}px`;
      });

      frameRef.current = requestAnimationFrame(animate);
    };

    window.addEventListener("mousemove", onMouseMove, { passive: true });
    frameRef.current = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      cancelAnimationFrame(frameRef.current);
      document.body.removeChild(container);
    };
  }, [accentColor.value]);

  return null;
}
