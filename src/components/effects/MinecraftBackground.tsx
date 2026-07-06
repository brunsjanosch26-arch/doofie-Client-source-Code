"use client";
import React from "react";

export function MinecraftBackground() {
  return (
    <div className="absolute inset-0" style={{ pointerEvents: "none", zIndex: 0 }}>
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: "url('/backgrounds/minecraft.jpg')",
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundRepeat: "no-repeat",
        }}
      />
      {/* Dark overlay so UI stays readable */}
      <div
        className="absolute inset-0"
        style={{ background: "linear-gradient(to bottom, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.35) 50%, rgba(0,0,0,0.6) 100%)" }}
      />
    </div>
  );
}
