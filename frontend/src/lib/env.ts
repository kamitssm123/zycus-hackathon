/**
 * Every environment value is read here and nowhere else, so a missing variable fails loudly at
 * startup instead of as an undefined deep inside a fetch call.
 */
export const env = {
  // Empty default = same-origin "/api", which the Vite proxy (dev) and nginx (prod) both forward.
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
  appName: import.meta.env.VITE_APP_NAME ?? 'SlotMind',
} as const;
