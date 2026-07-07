import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Icon } from "@iconify/react";
import { useThemeStore } from "../../store/useThemeStore";

interface PaletteCommand {
  id: string;
  label: string;
  icon: string;
  keywords?: string;
}

interface DoofieCommandPaletteProps {
  commands: PaletteCommand[];
  onRun: (id: string) => void;
}

/**
 * Doofie-Kommandopalette: Strg+K oeffnet eine Spotlight-artige Suche,
 * mit der man ohne Maus durch den Launcher springt.
 */
export function DoofieCommandPalette({ commands, onRun }: DoofieCommandPaletteProps) {
  const { accentColor } = useThemeStore();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    if (!q) return commands;
    return commands.filter(
      (c) => c.label.toLowerCase().includes(q) || (c.keywords ?? "").toLowerCase().includes(q),
    );
  }, [commands, query]);

  const close = useCallback(() => {
    setOpen(false);
    setQuery("");
    setSelected(0);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
        setQuery("");
        setSelected(0);
      } else if (e.key === "Escape") {
        close();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 50);
  }, [open]);

  useEffect(() => setSelected(0), [query]);

  if (!open) return null;

  const run = (id: string) => {
    onRun(id);
    close();
  };

  return (
    <div
      style={{ position: "fixed", inset: 0, zIndex: 99999, background: "rgba(0,0,0,0.6)", backdropFilter: "blur(4px)", display: "flex", alignItems: "flex-start", justifyContent: "center", paddingTop: "18vh" }}
      onClick={close}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: "480px", maxWidth: "90vw", borderRadius: "16px", overflow: "hidden",
          background: "rgba(10,10,18,0.97)",
          border: `1px solid ${accentColor.value}50`,
          boxShadow: `0 0 60px ${accentColor.value}30, 0 24px 60px rgba(0,0,0,0.7)`,
          animation: "paletteIn 0.15s ease-out",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "10px", padding: "14px 16px", borderBottom: "1px solid rgba(255,255,255,0.07)" }}>
          <Icon icon="solar:magnifer-bold" style={{ width: "18px", height: "18px", color: accentColor.light }} />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { e.preventDefault(); setSelected((s) => Math.min(s + 1, filtered.length - 1)); }
              if (e.key === "ArrowUp") { e.preventDefault(); setSelected((s) => Math.max(s - 1, 0)); }
              if (e.key === "Enter" && filtered[selected]) run(filtered[selected].id);
            }}
            placeholder="Wohin, Doofie?"
            style={{ flex: 1, background: "none", border: "none", outline: "none", color: "#fff", fontSize: "15px" }}
          />
          <span style={{ fontSize: "10px", color: "rgba(255,255,255,0.3)", border: "1px solid rgba(255,255,255,0.15)", borderRadius: "5px", padding: "2px 6px" }}>ESC</span>
        </div>

        <div style={{ maxHeight: "300px", overflowY: "auto", padding: "6px" }}>
          {filtered.length === 0 && (
            <div style={{ padding: "20px", textAlign: "center", color: "rgba(255,255,255,0.3)", fontSize: "13px" }}>
              Nichts gefunden
            </div>
          )}
          {filtered.map((c, i) => (
            <div
              key={c.id}
              onClick={() => run(c.id)}
              onMouseEnter={() => setSelected(i)}
              style={{
                display: "flex", alignItems: "center", gap: "12px",
                padding: "10px 12px", borderRadius: "10px", cursor: "pointer",
                background: i === selected ? `${accentColor.value}25` : "transparent",
                border: i === selected ? `1px solid ${accentColor.value}40` : "1px solid transparent",
              }}
            >
              <Icon icon={c.icon} style={{ width: "18px", height: "18px", color: i === selected ? accentColor.light : "rgba(255,255,255,0.5)" }} />
              <span style={{ fontSize: "14px", color: i === selected ? "#fff" : "rgba(255,255,255,0.7)" }}>{c.label}</span>
            </div>
          ))}
        </div>

        <div style={{ padding: "8px 16px", borderTop: "1px solid rgba(255,255,255,0.05)", fontSize: "10px", color: "rgba(255,255,255,0.25)", display: "flex", gap: "14px" }}>
          <span>&#8593;&#8595; Navigieren</span>
          <span>&#9166; Ausfuehren</span>
          <span>Strg+K Schliessen</span>
        </div>
      </div>
      <style>{`@keyframes paletteIn { from { transform: scale(0.96) translateY(-8px); opacity: 0; } to { transform: scale(1) translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}
