export async function extractDominantSkinColor(imageUrl: string): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        const canvas = document.createElement("canvas");
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext("2d");
        if (!ctx) { resolve(null); return; }

        ctx.drawImage(img, 0, 0);

        // Sample the head area (top 16x16 of a 64x64 skin)
        const scale = img.width / 64;
        const headData = ctx.getImageData(8 * scale, 8 * scale, 8 * scale, 8 * scale);
        const pixels = headData.data;

        let rSum = 0, gSum = 0, bSum = 0, count = 0;

        for (let i = 0; i < pixels.length; i += 4) {
          const r = pixels[i], g = pixels[i + 1], b = pixels[i + 2], a = pixels[i + 3];
          if (a < 128) continue;

          // Skip near-white and near-black pixels
          const brightness = (r + g + b) / 3;
          if (brightness > 230 || brightness < 20) continue;

          // Saturation filter - skip grayscale-ish pixels
          const max = Math.max(r, g, b), min = Math.min(r, g, b);
          if (max - min < 30) continue;

          rSum += r; gSum += g; bSum += b; count++;
        }

        if (count === 0) { resolve(null); return; }

        const r = Math.round(rSum / count);
        const g = Math.round(gSum / count);
        const b = Math.round(bSum / count);

        const hex = "#" + [r, g, b].map(v => v.toString(16).padStart(2, "0")).join("");
        resolve(hex);
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => resolve(null);
    img.src = imageUrl;
  });
}
