
export const env = {
  
  authUrl: import.meta.env.VITE_AUTH_URL ?? 'https://auth.getswiftdrop.me',
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  frontendId: import.meta.env.VITE_FRONTEND_ID ?? 'local',
};
