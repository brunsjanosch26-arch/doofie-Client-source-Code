import { useEffect, useRef, useState } from "react";
import { Icon } from "@iconify/react";
import { useAchievementStore } from "../../store/achievement-store";
import { useThemeStore } from "../../store/useThemeStore";

export function AchievementUnlock() {
  const { pendingUnlock, clearPending } = useAchievementStore();
  const { accentColor } = useThemeStore();
  const [visible, setVisible] = useState(false);
  const [current, setCurrent] = useState(pendingUnlock);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!pendingUnlock) return;

    // Clear any running timer first
    if (timerRef.current) clearTimeout(timerRef.current);

    setCurrent(pendingUnlock);
    setVisible(true);

    // After 5s start slide-out, after 5.5s clear the store
    timerRef.current = setTimeout(() => {
      setVisible(false);
      setTimeout(() => clearPending(), 500);
    }, 5000);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [pendingUnlock]); // only pendingUnlock — do NOT add visible here

  if (!current) return null;

  return (
    <div
      style={{
        position: "fixed",
        bottom: "24px",
        right: "24px",
        zIndex: 99999,
        transform: visible ? "translateX(0)" : "translateX(120%)",
        transition: "transform 0.5s cubic-bezier(0.34,1.56,0.64,1), opacity 0.5s",
        opacity: visible ? 1 : 0,
        pointerEvents: "none",
      }}
    >
      <div
        style={{
          background: "linear-gradient(135deg, rgba(10,10,20,0.98), rgba(20,20,40,0.95))",
          border: `2px solid ${accentColor.value}60`,
          borderRadius: "16px",
          padding: "16px 20px",
          minWidth: "300px",
          boxShadow: `0 0 40px ${accentColor.value}40, 0 20px 60px rgba(0,0,0,0.7)`,
          backdropFilter: "blur(20px)",
          display: "flex",
          alignItems: "center",
          gap: "14px",
          overflow: "hidden",
          position: "relative",
        }}
      >
        {/* Shimmer sweep */}
        <div style={{
          position: "absolute", inset: 0, pointerEvents: "none",
          background: `linear-gradient(105deg, transparent 30%, ${accentColor.value}22 50%, transparent 70%)`,
          animation: "sweep 1.5s ease-out",
        }} />

        {/* Icon */}
        <div style={{
          width: "52px", height: "52px", borderRadius: "12px", flexShrink: 0,
          background: `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})`,
          display: "flex", alignItems: "center", justifyContent: "center",
          boxShadow: `0 0 20px ${accentColor.value}60`,
        }}>
          <Icon icon={current.icon} style={{ width: "26px", height: "26px", color: "#fff" }} />
        </div>

        {/* Text */}
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: "10px", fontWeight: 700, color: accentColor.light, textTransform: "uppercase", letterSpacing: "2px", marginBottom: "2px" }}>
            Achievement freigeschaltet · {current.points} Punkte
          </div>
          <div style={{ fontSize: "15px", fontWeight: 800, color: "#fff", lineHeight: 1.2 }}>
            {current.name}
          </div>
          <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.55)", marginTop: "2px" }}>
            {current.description}
          </div>
        </div>

        {/* Trophy */}
        <Icon icon="solar:cup-star-bold" style={{ width: "28px", height: "28px", color: accentColor.value, opacity: 0.5 }} />

        <style>{`
          @keyframes sweep {
            from { transform: translateX(-100%); }
            to { transform: translateX(200%); }
          }
        `}</style>
      </div>
    </div>
  );
}
