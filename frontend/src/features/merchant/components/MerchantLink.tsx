import type { ComponentProps } from 'react';
import { navigate } from '../../../app/router';

export function MerchantLink({ href, ...props }: Omit<ComponentProps<'a'>, 'href' | 'onClick'> & { href: string }) {
  return (
    <a {...props} href={href} onClick={(event) => {
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      if (window.location.pathname !== href || window.location.hash) navigate(href);
      window.scrollTo({ top: 0 });
    }} />
  );
}
