import { Fragment } from 'react';

/**
 * An email address that may wrap only at natural points: after the "@" and before each dot
 * ("ishara@ | ceylonteabox | .example | .lk"), never in the middle of a word.
 */
export function Email({ address }: Readonly<{ address: string }>) {
  const parts = address.split(/(?=\.)|(?<=@)/);
  return (
    <>
      {parts.map((part, index) => (
        <Fragment key={`${index}${part}`}>{index > 0 && <wbr />}{part}</Fragment>
      ))}
    </>
  );
}
