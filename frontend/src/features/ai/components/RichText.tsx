import type { ReactNode } from 'react';

/**
 * Shows the assistant's replies with simple formatting: **bold**, *italic*, `code`, bullet and numbered lists,
 * paragraphs. Builds React elements only (never HTML strings), so nothing in a reply can run as code.
 */
export function RichText({ text, className }: { text: string; className?: string }) {
  const blocks = text.replace(/\r\n/g, '\n').trim().split(/\n\s*\n/);
  return <div className={className}>{blocks.map((block, i) => renderBlock(block, i))}</div>;
}

const BULLET = /^\s*[-*•]\s+/;
const NUMBERED = /^\s*\d+[.)]\s+/;

// A block can mix text and a list ("Cost breakdown:" then "* Base fee …"): each run of lines becomes its own part
function renderBlock(block: string, key: number): ReactNode {
  const lines = block.split('\n').filter((line) => line.trim() !== '');
  const runs: { kind: 'ul' | 'ol' | 'p'; lines: string[] }[] = [];
  for (const line of lines) {
    const kind = BULLET.test(line) ? 'ul' : NUMBERED.test(line) ? 'ol' : 'p';
    const last = runs.at(-1);
    if (last && last.kind === kind) last.lines.push(line);
    else runs.push({ kind, lines: [line] });
  }
  return runs.map((run, r) => {
    const runKey = `${key}-${r}`;
    if (run.kind === 'ul') {
      return <ul key={runKey}>{run.lines.map((line, i) => <li key={i}>{inline(line.replace(BULLET, ''))}</li>)}</ul>;
    }
    if (run.kind === 'ol') {
      return <ol key={runKey}>{run.lines.map((line, i) => <li key={i}>{inline(line.replace(NUMBERED, ''))}</li>)}</ol>;
    }
    return (
      <p key={runKey}>
        {run.lines.map((line, i) => <span key={i}>{i > 0 && <br />}{inline(line)}</span>)}
      </p>
    );
  });
}

// **bold**, *italic* / _italic_, `code`
function inline(line: string): ReactNode[] {
  const parts: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*\s][^*]*\*|_[^_\s][^_]*_)/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(line)) !== null) {
    if (match.index > last) parts.push(line.slice(last, match.index));
    const token = match[0];
    const key = `${match.index}-${token.length}`;
    if (token.startsWith('**')) parts.push(<strong key={key}>{token.slice(2, -2)}</strong>);
    else if (token.startsWith('`')) parts.push(<code key={key}>{token.slice(1, -1)}</code>);
    else parts.push(<em key={key}>{token.slice(1, -1)}</em>);
    last = match.index + token.length;
  }
  if (last < line.length) parts.push(line.slice(last));
  return parts;
}
