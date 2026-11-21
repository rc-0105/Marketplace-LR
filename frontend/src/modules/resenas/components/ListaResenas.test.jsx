import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ListaResenas from './ListaResenas.jsx';

const RESENAS = [
  { id: 2, reservaId: 12, autor: 'Carla Ruiz', calificacion: 5, contenido: 'Perfecto', fecha: '2026-02-10T10:00:00Z' },
  { id: 1, reservaId: 11, autor: 'Ana Gómez', calificacion: 4, contenido: 'Muy cómodo', fecha: '2026-01-10T10:00:00Z' },
];

describe('ListaResenas', () => {
  it('muestra el autor, la calificación, la fecha y el contenido de cada reseña, en el orden recibido', () => {
    render(<ListaResenas resenas={RESENAS} />);

    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Carla Ruiz');
    expect(items[0]).toHaveTextContent('Calificación: 5 de 5');
    expect(items[0]).toHaveTextContent('Perfecto');
    expect(items[0]).toHaveTextContent('10 de febrero de 2026');
    expect(items[1]).toHaveTextContent('Ana Gómez');
    expect(items[1]).toHaveTextContent('Calificación: 4 de 5');
  });

  it('sin reseñas lo indica en lugar de dejar la sección vacía', () => {
    render(<ListaResenas resenas={[]} />);

    expect(screen.getByText('Esta publicación aún no tiene reseñas.')).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });
});
