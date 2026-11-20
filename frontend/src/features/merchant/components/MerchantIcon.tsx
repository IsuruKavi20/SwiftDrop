const paths = {
  box: 'M12 3 3.5 7.5v9L12 21l8.5-4.5v-9L12 3ZM3.5 7.5 12 12l8.5-4.5M12 12v9',
  home: 'm3 11 9-8 9 8M5 10v10h5v-6h4v6h5V10',
  key: 'M15 14a5 5 0 1 0-5-5l-7 7v5h5v-3h3v-3l2-2m4-5h.01',
  bolt: 'm13 2-9 12h7l-1 8 10-12h-8l1-8Z',
  chart: 'M3 21V11h5v10m0 0V7h5v14m0 0V3h6v18H3',
  settings: 'M10 3h4l.6 3 2 .9 2.6-1.2 2 3.4-2 2v2l2 2-2 3.4-2.6-1.2-2 .9-.6 3h-4l-.6-3-2-.9-2.6 1.2-2-3.4 2-2v-2l-2-2 2-3.4 2.6 1.2 2-.9.6-3ZM15 12a3 3 0 1 0-6 0 3 3 0 0 0 6 0Z',
  cart: 'M3 4h2l3 10h10l3-7H6m4 12h.01m7 0h.01',
  logout: 'M10 5h9v14h-9M3 12h10m-7-3-3 3 3 3',
  search: 'M15 15l5 5m-3-9a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z',
  plus: 'M12 5v14M5 12h14',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 12a2 2 0 0 0 4 0',
  down: 'm6 9 6 6 6-6',
  left: 'm15 6-6 6 6 6',
  right: 'm9 6 6 6-6 6',
  truck: 'M3 5h12v12H3zm12 4h4l3 4v4h-7M9 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0Zm11 0a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
  check: 'm7 12 3 3 7-7',
  completed: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM7 12h4l2-2 2 2h3',
  payout: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 6v12m3-9h-5a1.5 1.5 0 0 0 0 3h4a1.5 1.5 0 0 1 0 3H9',
  pin: 'M18 10c0 4-6 9-6 9s-6-5-6-9a6 6 0 1 1 12 0Zm-6 0h.01',
  eye: 'M2 12s4-6 10-6 10 6 10 6-4 6-10 6-10-6-10-6Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
};

export type MerchantIconName = keyof typeof paths;

export function MerchantIcon({ name, size = 20 }: { name: MerchantIconName; size?: number }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d={paths[name]} />
    </svg>
  );
}
