// Replaces @tauri-apps/api/event via Vite alias when not running inside Tauri.

const internals = () => (window as any).__TAURI_INTERNALS__;

export interface TauriEvent<T> {
  event: string;
  id: number;
  payload: T;
}

export type UnlistenFn = () => void;

export function listen<T>(
  event: string,
  handler: (event: TauriEvent<T>) => void,
): Promise<UnlistenFn> {
  const t = internals();
  if (t?.listen) return t.listen(event, handler);
  return Promise.resolve(() => {});
}

export function once<T>(
  event: string,
  handler: (event: TauriEvent<T>) => void,
): Promise<UnlistenFn> {
  return listen(event, handler);
}

export function emit(event: string, payload?: unknown): Promise<void> {
  const t = internals();
  if (t?.emit) return t.emit(event, payload);
  return Promise.resolve();
}

export function emitTo(
  _target: unknown,
  event: string,
  payload?: unknown,
): Promise<void> {
  return emit(event, payload);
}
