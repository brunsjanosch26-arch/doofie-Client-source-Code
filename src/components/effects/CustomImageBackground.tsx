"use client";
import React, { useEffect, useState } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";

interface Props {
  imagePath: string;
}

export function CustomImageBackground({ imagePath }: Props) {
  const [src, setSrc] = useState<string>("");

  useEffect(() => {
    if (!imagePath) return;
    try {
      setSrc(convertFileSrc(imagePath));
    } catch {
      setSrc(imagePath);
    }
  }, [imagePath]);

  if (!src) return null;

  return (
    <div className="absolute inset-0" style={{ pointerEvents: "none", zIndex: 0 }}>
      <div
        className="absolute inset-0"
        style={{
          backgroundImage: `url('${src}')`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundRepeat: "no-repeat",
        }}
      />
      <div
        className="absolute inset-0"
        style={{ background: "linear-gradient(to bottom, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.35) 50%, rgba(0,0,0,0.6) 100%)" }}
      />
    </div>
  );
}
