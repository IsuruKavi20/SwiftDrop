import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '../../styles/theme.css';
import { DispatcherPreviewApp } from './DispatcherPreviewApp';
import { dispatcherPath, matchDispatcherRoute } from './dispatcherRoutes';

/**
 * Standalone entry for /dispatcher.html, so the dispatcher screens can be opened before they are wired into
 * app/App.tsx. It starts on the development preview; the sidebar links then work as in the real app.
 */
if (!matchDispatcherRoute(window.location.pathname)) {
  window.history.replaceState(null, '', dispatcherPath('admin', import.meta.env.DEV));
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DispatcherPreviewApp />
  </StrictMode>,
);
