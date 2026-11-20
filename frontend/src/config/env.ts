export const env = {
  /** The SwiftDrop auth service (login, signup, Google login, tokens). */
  authUrl: import.meta.env?.VITE_AUTH_URL ?? 'https://auth.getswiftdrop.me',
  /** The SwiftDrop backend API (checks the tokens issued by the auth service). */
  apiUrl: import.meta.env?.VITE_API_URL ?? 'http://localhost:8080',
  frontendId: import.meta.env?.VITE_FRONTEND_ID ?? 'local',
  /**
   * Explicit mock mode selector for courier portal.
   * Forced to false in production builds (import.meta.env.PROD).
   * In development, enabled ONLY when VITE_COURIER_MOCK_MODE === 'true'.
   */
  isCourierMockMode: Boolean(import.meta.env?.DEV && import.meta.env?.VITE_COURIER_MOCK_MODE === 'true'),
};
