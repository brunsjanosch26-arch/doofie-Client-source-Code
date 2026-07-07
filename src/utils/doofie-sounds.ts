/**
 * Doofie-Sound-System: synthetisierte UI-Sounds via WebAudio.
 * Keine Audiodateien noetig — alles wird live erzeugt.
 * Abschaltbar via localStorage "doofie-sounds" = "off".
 */

let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  if (typeof window === "undefined") return null;
  if (localStorage.getItem("doofie-sounds") === "off") return null;
  if (!ctx) {
    try {
      ctx = new AudioContext();
    } catch {
      return null;
    }
  }
  return ctx;
}

function tone(freq: number, start: number, duration: number, volume: number, type: OscillatorType = "square") {
  const ac = audio();
  if (!ac) return;
  const osc = ac.createOscillator();
  const gain = ac.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  gain.gain.setValueAtTime(0, ac.currentTime + start);
  gain.gain.linearRampToValueAtTime(volume, ac.currentTime + start + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.001, ac.currentTime + start + duration);
  osc.connect(gain).connect(ac.destination);
  osc.start(ac.currentTime + start);
  osc.stop(ac.currentTime + start + duration + 0.05);
}

/** Kurzer Klick fuer Buttons/Navigation. */
export function playClick() {
  tone(880, 0, 0.05, 0.04);
}

/** Zweiton-Jingle bei Erfolg (Launch fertig, Download fertig). */
export function playSuccess() {
  tone(523, 0, 0.1, 0.06);
  tone(784, 0.09, 0.16, 0.06);
}

/** Doofie-Start-Jingle fuer den Splashscreen. */
export function playStartup() {
  tone(392, 0, 0.12, 0.05, "triangle");
  tone(523, 0.11, 0.12, 0.05, "triangle");
  tone(659, 0.22, 0.12, 0.05, "triangle");
  tone(784, 0.33, 0.28, 0.06, "triangle");
}

/** Fehlerton. */
export function playError() {
  tone(220, 0, 0.15, 0.05, "sawtooth");
  tone(180, 0.13, 0.2, 0.05, "sawtooth");
}

export function soundsEnabled(): boolean {
  return localStorage.getItem("doofie-sounds") !== "off";
}

export function setSoundsEnabled(on: boolean) {
  localStorage.setItem("doofie-sounds", on ? "on" : "off");
}
