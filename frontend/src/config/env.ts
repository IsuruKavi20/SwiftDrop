/**
 * Where the services live. Set them in frontend/.env (see .env.example); the defaults fit local development
 * against the deployed SwiftDrop auth service.
 */
export const env = {
  /** The SwiftDrop auth service (login, signup, Google login, tokens). */
  authUrl: import.meta.env.VITE_AUTH_URL ?? 'https://auth.getswiftdrop.me',
  /** The SwiftDrop backend API (checks the tokens issued by the auth service). */
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
};
