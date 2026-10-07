import type { ReactNode } from 'react';

export function Card({ title, children }: { title: string; children: ReactNode }) {
  const id = `card-${title.replace(/\s+/g, '-').toLowerCase()}`;
  return (
    <section className="card" aria-labelledby={id}>
      <h2 id={id}>{title}</h2>
      {children}
    </section>
  );
}
