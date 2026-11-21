import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { consultarResenas } from '../services/resenaService.js';
import ResenasPublicacion from './ResenasPublicacion.jsx';

vi.mock('../services/resenaService.js', () => ({ consultarResenas: vi.fn() }));

describe('ResenasPublicacion', () => {
  beforeEach(() => vi.resetAllMocks());

  it('carga las reseñas de la publicación y las muestra', async () => {
    vi.mocked(consultarResenas).mockResolvedValue({
      promedio: 4.5,
      resenas: [
        { id: 1, reservaId: 11, autor: 'Ana Gómez', calificacion: 4, contenido: 'Muy cómodo', fecha: '2026-01-10T10:00:00Z' },
      ],
    });

    render(<ResenasPublicacion publicacionId={5} />);

    expect(await screen.findByText('Muy cómodo')).toBeInTheDocument();
    expect(consultarResenas).toHaveBeenCalledWith(5);
  });

  it('sin reseñas muestra que la publicación aún no tiene', async () => {
    vi.mocked(consultarResenas).mockResolvedValue({ promedio: null, resenas: [] });

    render(<ResenasPublicacion publicacionId={5} />);

    expect(await screen.findByText('Esta publicación aún no tiene reseñas.')).toBeInTheDocument();
  });

  it('si la consulta falla avisa sin romper el resto de la pantalla', async () => {
    vi.mocked(consultarResenas).mockRejectedValue(new Error('caído'));

    render(<ResenasPublicacion publicacionId={5} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las reseñas.');
  });
});
