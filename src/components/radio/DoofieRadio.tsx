import { useEffect, useRef, useState } from "react";
import { Icon } from "@iconify/react";
import { STATIONS, useRadioStore } from "../../store/radio-store";
import { useThemeStore } from "../../store/useThemeStore";
import { useAchievementStore } from "../../store/achievement-store";

export function DoofieRadio() {
  const { isOpen, isPlaying, currentStationId, volume, setOpen, setPlaying, setStation, setVolume, addListened, totalListened } = useRadioStore();
  const { accentColor } = useThemeStore();
  const { updateStats } = useAchievementStore();
  const audioRef = useRef<HTMLAudioElement>(null);
  const [loading, setLoading] = useState(false);
  const [visualizer, setVisualizer] = useState<number[]>(() => Array(16).fill(0));
  const listenedRef = useRef(0);

  const station = STATIONS.find(s => s.id === currentStationId) ?? STATIONS[0];

  // Fake visualizer animation
  useEffect(() => {
    if (!isPlaying) {
      setVisualizer(Array(16).fill(0));
      return;
    }
    const interval = setInterval(() => {
      setVisualizer(v => v.map(() => isPlaying ? 10 + Math.random() * 90 : 0));
    }, 80);
    return () => clearInterval(interval);
  }, [isPlaying]);

  // Track listen time
  useEffect(() => {
    if (!isPlaying) return;
    const interval = setInterval(() => {
      listenedRef.current += 5;
      addListened(5);
      updateStats({ radioListened: totalListened + listenedRef.current });
    }, 5000);
    return () => clearInterval(interval);
  }, [isPlaying, addListened, updateStats, totalListened]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = volume;
    if (isPlaying) {
      setLoading(true);
      audio.play().then(() => setLoading(false)).catch(() => setLoading(false));
    } else {
      audio.pause();
    }
  }, [isPlaying, currentStationId, volume]);

  const togglePlay = () => setPlaying(!isPlaying);
  const changeStation = (id: string) => {
    setStation(id);
    if (isPlaying && audioRef.current) {
      audioRef.current.load();
      audioRef.current.play().catch(() => {});
    }
  };

  return (
    <>
      <audio ref={audioRef} src={station.url} preload="none" />

      {/* Mini player button in nav */}
      <button
        onClick={() => setOpen(!isOpen)}
        title="Doofie Radio"
        style={{
          width: "40px", height: "40px", borderRadius: "10px",
          background: isPlaying ? `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})` : "rgba(255,255,255,0.06)",
          border: isPlaying ? "none" : "1px solid rgba(255,255,255,0.1)",
          display: "flex", alignItems: "center", justifyContent: "center",
          cursor: "pointer", color: "#fff", fontSize: "16px",
          boxShadow: isPlaying ? `0 0 20px ${accentColor.value}60` : undefined,
          animation: isPlaying ? "pulse-radio 2s ease-in-out infinite" : undefined,
        }}
      >
        <Icon icon="solar:music-note-2-bold" style={{ width: "20px", height: "20px" }} />
      </button>

      {/* Expanded player */}
      {isOpen && (
        <div style={{
          position: "fixed", bottom: "20px", left: "72px", zIndex: 10000,
          background: "rgba(8,8,18,0.97)",
          border: `1px solid ${accentColor.value}40`,
          borderRadius: "20px", padding: "20px", width: "300px",
          boxShadow: `0 0 60px ${accentColor.value}30, 0 20px 60px rgba(0,0,0,0.8)`,
          backdropFilter: "blur(20px)",
        }}>
          {/* Header */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
            <div style={{ fontSize: "13px", fontWeight: 800, color: "#fff", display: "flex", alignItems: "center", gap: "6px" }}><Icon icon="solar:music-note-2-bold" style={{ width: "14px", height: "14px" }} /> Doofie Radio</div>
            <button onClick={() => setOpen(false)} style={{ background: "none", border: "none", color: "rgba(255,255,255,0.4)", cursor: "pointer", fontSize: "16px", display: "flex" }}><Icon icon="solar:close-circle-bold" style={{ width: "16px", height: "16px" }} /></button>
          </div>

          {/* Current station */}
          <div style={{
            display: "flex", alignItems: "center", gap: "12px",
            padding: "14px", borderRadius: "12px", marginBottom: "14px",
            background: `linear-gradient(135deg, ${station.color}22, ${station.color}11)`,
            border: `1px solid ${station.color}40`,
          }}>
            <div style={{ width: "44px", height: "44px", display: "flex", alignItems: "center", justifyContent: "center", background: `${station.color}30`, borderRadius: "10px" }}>
              <Icon icon={station.icon} style={{ width: "24px", height: "24px", color: station.color }} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: "14px", fontWeight: 700, color: "#fff" }}>{station.name}</div>
              <div style={{ fontSize: "11px", color: "rgba(255,255,255,0.4)" }}>{station.genre}</div>
            </div>
            {loading && <div style={{ fontSize: "12px", color: "rgba(255,255,255,0.3)" }}>laden...</div>}
          </div>

          {/* Visualizer */}
          <div style={{ display: "flex", alignItems: "flex-end", gap: "3px", height: "40px", marginBottom: "14px", padding: "0 4px" }}>
            {visualizer.map((h, i) => (
              <div key={i} style={{
                flex: 1, borderRadius: "3px",
                background: `linear-gradient(to top, ${station.color}, ${accentColor.light})`,
                height: `${h}%`,
                transition: "height 0.08s ease",
                opacity: isPlaying ? 0.8 : 0.1,
              }} />
            ))}
          </div>

          {/* Controls */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "16px", marginBottom: "16px" }}>
            <button onClick={togglePlay} style={{
              width: "48px", height: "48px", borderRadius: "50%",
              background: `linear-gradient(135deg, ${accentColor.dark}, ${accentColor.value})`,
              border: "none", cursor: "pointer", fontSize: "20px", color: "#fff",
              display: "flex", alignItems: "center", justifyContent: "center",
              boxShadow: `0 0 20px ${accentColor.value}60`,
            }}>
              <Icon icon={isPlaying ? "solar:pause-bold" : "solar:play-bold"} style={{ width: "20px", height: "20px" }} />
            </button>
          </div>

          {/* Volume */}
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "16px" }}>
            <Icon icon="solar:volume-small-bold" style={{ width: "16px", height: "16px", color: "rgba(255,255,255,0.6)" }} />
            <input type="range" min="0" max="1" step="0.05" value={volume}
              onChange={e => setVolume(parseFloat(e.target.value))}
              style={{ flex: 1, accentColor: accentColor.value }} />
            <Icon icon="solar:volume-loud-bold" style={{ width: "16px", height: "16px", color: "rgba(255,255,255,0.6)" }} />
          </div>

          {/* Station list */}
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            {STATIONS.map(s => (
              <button key={s.id} onClick={() => changeStation(s.id)} style={{
                display: "flex", alignItems: "center", gap: "10px",
                padding: "8px 10px", borderRadius: "10px",
                background: currentStationId === s.id ? `${s.color}25` : "rgba(255,255,255,0.03)",
                border: currentStationId === s.id ? `1px solid ${s.color}50` : "1px solid transparent",
                cursor: "pointer", textAlign: "left",
              }}>
                <Icon icon={s.icon} style={{ width: "16px", height: "16px", color: s.color }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: "12px", fontWeight: 600, color: currentStationId === s.id ? "#fff" : "rgba(255,255,255,0.6)" }}>{s.name}</div>
                  <div style={{ fontSize: "10px", color: "rgba(255,255,255,0.3)" }}>{s.genre}</div>
                </div>
                {currentStationId === s.id && isPlaying && <div style={{ width: "6px", height: "6px", borderRadius: "50%", background: s.color, animation: "blink 1s ease infinite" }} />}
              </button>
            ))}
          </div>
        </div>
      )}

      <style>{`
        @keyframes pulse-radio { 0%,100%{box-shadow:0 0 15px ${accentColor.value}40} 50%{box-shadow:0 0 30px ${accentColor.value}80} }
        @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0.2} }
      `}</style>
    </>
  );
}
