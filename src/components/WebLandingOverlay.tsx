"use client";

import { useState, useEffect } from "react";
import { Icon } from "@iconify/react";

const isBrowser = () => !(window as any).__TAURI_INTERNALS__;

const SCREENSHOTS = [
  { src: "/screenshots/189.png", caption: "Startbildschirm — 3D Skin-Viewer" },
  { src: "/screenshots/195.png", caption: "Launch-Screen — Custom Theme & Hintergrund" },
  { src: "/screenshots/190.png", caption: "Profil-Verwaltung" },
  { src: "/screenshots/191.png", caption: "Mod-Browser — Modrinth & CurseForge" },
  { src: "/screenshots/192.png", caption: "Einstellungen — Farben, Sprache & mehr" },
  { src: "/screenshots/193.png", caption: "Hintergrund-Animationen" },
  { src: "/screenshots/194.png", caption: "Skin-Posen — 10 Posen, 4 animiert" },
  { src: "/screenshots/196.png", caption: "Freunde-System" },
];

export function WebLandingOverlay() {
  const [visible, setVisible] = useState(false);
  const [lightbox, setLightbox] = useState<string | null>(null);
  const [activeScreenshot, setActiveScreenshot] = useState(0);

  useEffect(() => {
    if (!isBrowser()) return;
    const dismissed = sessionStorage.getItem("doofie-landing-dismissed");
    if (!dismissed) setVisible(true);
  }, []);

  useEffect(() => {
    if (!visible) return;
    const interval = setInterval(() => {
      setActiveScreenshot(i => (i + 1) % SCREENSHOTS.length);
    }, 3000);
    return () => clearInterval(interval);
  }, [visible]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setLightbox(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!visible) return null;

  const dismiss = () => {
    sessionStorage.setItem("doofie-landing-dismissed", "1");
    setVisible(false);
  };

  return (
    <>
      {/* Lightbox */}
      {lightbox && (
        <div
          onClick={() => setLightbox(null)}
          style={{
            position: "fixed", inset: 0, zIndex: 10000,
            background: "rgba(0,0,0,0.92)", backdropFilter: "blur(8px)",
            display: "flex", alignItems: "center", justifyContent: "center",
          }}
        >
          <img
            src={lightbox}
            onClick={e => e.stopPropagation()}
            style={{ maxWidth: "90vw", maxHeight: "90vh", borderRadius: 12, boxShadow: "0 0 80px rgba(0,0,0,0.8)" }}
          />
          <button
            onClick={() => setLightbox(null)}
            style={{
              position: "absolute", top: 20, right: 24,
              background: "rgba(255,255,255,0.1)", border: "1px solid rgba(255,255,255,0.2)",
              color: "#fff", width: 40, height: 40, borderRadius: 8,
              fontSize: 18, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center",
            }}
          ><Icon icon="solar:close-circle-bold" style={{ width: '20px', height: '20px' }} /></button>
        </div>
      )}

      {/* Overlay */}
      <div style={{
        position: "fixed", inset: 0, zIndex: 9999,
        background: "linear-gradient(135deg, #04070f 0%, #0a0e1f 50%, #04070f 100%)",
        overflowY: "auto",
        fontFamily: "'Inter', 'Segoe UI', sans-serif",
        color: "#e0e8ff",
      }}>

        {/* Grid background */}
        <div style={{
          position: "fixed", inset: 0, pointerEvents: "none",
          background: `
            radial-gradient(ellipse 70% 50% at 50% 30%, rgba(61,127,240,0.08) 0%, transparent 70%),
            linear-gradient(rgba(61,127,240,0.03) 1px, transparent 1px),
            linear-gradient(90deg, rgba(61,127,240,0.03) 1px, transparent 1px)
          `,
          backgroundSize: "100% 100%, 48px 48px, 48px 48px",
        }} />

        <div style={{ position: "relative", maxWidth: 900, margin: "0 auto", padding: "60px 24px 80px" }}>

          {/* Notice banner */}
          <div style={{
            display: "inline-flex", alignItems: "center", gap: 8,
            background: "rgba(251,191,36,0.1)", border: "1px solid rgba(251,191,36,0.3)",
            color: "#fbbf24", fontSize: 11, fontWeight: 700, letterSpacing: "1.5px",
            padding: "5px 14px", borderRadius: 20, marginBottom: 32,
            textTransform: "uppercase",
          }}>
            100% Doofie — dein Launcher, dein Style
          </div>

          {/* Hero */}
          <div style={{ textAlign: "center", marginBottom: 56 }}>
            <h1 style={{
              fontSize: "clamp(36px, 7vw, 72px)", fontWeight: 900,
              lineHeight: 1.05, letterSpacing: -2, color: "#fff",
              margin: "0 0 16px",
              textShadow: "0 0 80px rgba(61,127,240,0.4)",
            }}>
              Doofie <span style={{ color: "#3d7ff0" }}>Client</span>
            </h1>
            <p style={{ fontSize: "clamp(14px, 2vw, 18px)", color: "#5a6a8a", maxWidth: 520, margin: "0 auto 12px", fontWeight: 500 }}>
              Der Minecraft Launcher mit eigenem Style — Posen, Hintergründe, Mod-Manager, Freunde-System und mehr.
            </p>
            <p style={{ fontSize: 13, color: "#4a5a7a", marginBottom: 36 }}>
              Dein Launcher. Dein Design. Dein Doofie.
            </p>

            <div style={{ display: "flex", gap: 12, flexWrap: "wrap", justifyContent: "center" }}>
              <a
                href="https://github.com/brunsjanosch26-arch/doofie-client-code/releases/download/v2.0.0/Doofieclient_2.0.0_x64-setup.exe"
                download="Doofieclient_2.0.0_x64-setup.exe"
                style={{
                  display: "inline-flex", alignItems: "center", gap: 10,
                  background: "linear-gradient(135deg, rgba(61,127,240,0.35), rgba(61,127,240,0.2))",
                  border: "1px solid rgba(61,127,240,0.5)",
                  color: "#fff", fontSize: 15, fontWeight: 700, letterSpacing: 0.5,
                  padding: "14px 32px", borderRadius: 10, textDecoration: "none",
                  boxShadow: "0 0 30px rgba(61,127,240,0.2)",
                  transition: "all 0.2s",
                }}
              >
                ↓ Kostenlos herunterladen (.exe)
              </a>
              <button
                onClick={dismiss}
                style={{
                  display: "inline-flex", alignItems: "center", gap: 8,
                  background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.12)",
                  color: "#8a9abf", fontSize: 14, fontWeight: 600,
                  padding: "13px 26px", borderRadius: 10, cursor: "pointer",
                }}
              >
                Deinen Launcher erkunden →
              </button>
            </div>
            <p style={{ marginTop: 12, fontSize: 11, color: "#3a4a6a" }}>
              Windows 10/11 · 64-Bit · Kostenlos & Open Source
            </p>
          </div>

          {/* Features */}
          <div style={{ marginBottom: 56 }}>
            <p style={{ fontSize: 10, fontWeight: 800, letterSpacing: 3, color: "#3d7ff0", textTransform: "uppercase", marginBottom: 14 }}>Was neu ist</p>
            <h2 style={{ fontSize: "clamp(22px, 4vw, 36px)", fontWeight: 900, color: "#fff", letterSpacing: -1, marginBottom: 32 }}>
              Alles was ein Launcher haben muss
            </h2>
            <div style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
              gap: 12,
            }}>
              {[
                { icon: "solar:accessibility-bold", title: "10 Skin-Posen", desc: "Stehend, kniend, fliegend, winkend — 4 davon animiert" },
                { icon: "solar:palette-bold", title: "Custom Hintergründe", desc: "Eigenes Bild, Minecraft-Foto oder 9 Animationen" },
                { icon: "solar:box-bold", title: "Mod-Manager", desc: "Mods direkt aus Modrinth & CurseForge installieren" },
                { icon: "solar:folder-bold", title: "Profil-System", desc: "Jede Instanz komplett isoliert mit eigenen Mods & Saves" },
                { icon: "solar:pallete-2-bold", title: "Akzentfarben", desc: "12 Farben für den gesamten Launcher wählbar" },
                { icon: "solar:users-group-rounded-bold", title: "Freunde-System", desc: "Online-Status & Server deiner Freunde live sehen" },
              ].map(f => (
                <div key={f.title} style={{
                  background: "rgba(13,19,40,0.8)", border: "1px solid rgba(61,127,240,0.15)",
                  borderRadius: 12, padding: "20px 22px",
                }}>
                  <div style={{ marginBottom: 8 }}><Icon icon={f.icon} style={{ width: "24px", height: "24px" }} /></div>
                  <div style={{ fontSize: 14, fontWeight: 700, color: "#fff", marginBottom: 4 }}>{f.title}</div>
                  <div style={{ fontSize: 12, color: "#5a6a8a", lineHeight: 1.5 }}>{f.desc}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Screenshot slider */}
          <div style={{ marginBottom: 56 }}>
            <p style={{ fontSize: 10, fontWeight: 800, letterSpacing: 3, color: "#3d7ff0", textTransform: "uppercase", marginBottom: 14 }}>Einblicke</p>
            <h2 style={{ fontSize: "clamp(22px, 4vw, 36px)", fontWeight: 900, color: "#fff", letterSpacing: -1, marginBottom: 24 }}>
              Screenshots
            </h2>

            {/* Big preview */}
            <div
              onClick={() => setLightbox(SCREENSHOTS[activeScreenshot].src)}
              style={{
                position: "relative", borderRadius: 12, overflow: "hidden",
                border: "1px solid rgba(61,127,240,0.3)", cursor: "pointer",
                marginBottom: 10,
              }}
            >
              <img
                src={SCREENSHOTS[activeScreenshot].src}
                style={{ width: "100%", display: "block", aspectRatio: "16/9", objectFit: "cover" }}
              />
              <div style={{
                position: "absolute", bottom: 0, left: 0, right: 0,
                padding: "10px 14px",
                background: "linear-gradient(transparent, rgba(4,7,15,0.88))",
                fontSize: 12, fontWeight: 600, color: "#c0d0ff",
              }}>
                {SCREENSHOTS[activeScreenshot].caption}
              </div>
            </div>

            {/* Thumbnails */}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {SCREENSHOTS.map((s, i) => (
                <div
                  key={i}
                  onClick={() => setActiveScreenshot(i)}
                  style={{
                    flex: "1 1 80px", borderRadius: 6, overflow: "hidden",
                    border: `1px solid ${i === activeScreenshot ? "rgba(61,127,240,0.7)" : "rgba(61,127,240,0.15)"}`,
                    cursor: "pointer", opacity: i === activeScreenshot ? 1 : 0.5,
                    transition: "all 0.2s",
                  }}
                >
                  <img src={s.src} style={{ width: "100%", display: "block", aspectRatio: "16/9", objectFit: "cover" }} />
                </div>
              ))}
            </div>
          </div>

          {/* Bottom CTA */}
          <div style={{
            textAlign: "center",
            background: "rgba(61,127,240,0.06)", border: "1px solid rgba(61,127,240,0.15)",
            borderRadius: 16, padding: "40px 32px",
          }}>
            <h2 style={{ fontSize: 24, fontWeight: 900, color: "#fff", marginBottom: 12 }}>
              Jetzt die Web-Vorschau erkunden
            </h2>
            <p style={{ fontSize: 14, color: "#5a6a8a", marginBottom: 24 }}>
              Schau dir den Launcher direkt im Browser an — interaktiv, aber ohne echter Account oder Spielstart.
            </p>
            <button
              onClick={dismiss}
              style={{
                display: "inline-flex", alignItems: "center", gap: 10,
                background: "linear-gradient(135deg, rgba(61,127,240,0.35), rgba(61,127,240,0.2))",
                border: "1px solid rgba(61,127,240,0.5)",
                color: "#fff", fontSize: 15, fontWeight: 700,
                padding: "14px 40px", borderRadius: 10, cursor: "pointer",
                boxShadow: "0 0 30px rgba(61,127,240,0.2)",
              }}
            >
              Doofie Client erkunden →
            </button>
            <p style={{ marginTop: 12, fontSize: 11, color: "#3a4a6a" }}>
              Oder lade den Installer herunter um ihn wirklich zu spielen.
            </p>
          </div>

        </div>
      </div>
    </>
  );
}
