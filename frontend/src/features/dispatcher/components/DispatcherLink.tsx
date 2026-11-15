import type { ComponentProps } from 'react';
import { navigate } from '../../../app/router';

/** A normal link that changes page without reloading (ctrl/cmd-click still opens a new tab). */
export function DispatcherLink({ href, ...props }: Omit<ComponentProps<'a'>, 'href' | 'onClick'> & { href: string }) {
  return (
    <a {...props} href={href} onClick={(event) => {
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      if (window.location.pathname !== href) navigate(href);
      window.scrollTo({ top: 0 });
    }} />
  );
}
