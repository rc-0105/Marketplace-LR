import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listarResenasParaModerar, ocultarResena } from '../services/resenaService.js';
import ModeracionResenasPage from './ModeracionResenasPage.jsx';

vi.mock('../services/resenaService.js', () => ({
  listarResenasParaModerar: vi.fn(),
  ocultarResena: vi.fn(),
}));

function resena(datos) {
  return {
    id: 1,
    publicacionId: 30,
    autor: 'Ana Gómez',
    calificacion: 4,
    contenido: 'Muy cómodo',
    fecha: '2026-01-10T15:00:00Z',
    ...datos,
  };
}

describe('ModeracionResenasPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra las reseñas en el orden de la API con autor, calificación y contenido', async () => {
    vi.mocked(listarResenasParaModerar).mockResolvedValue([
      resena({ id: 2, autor: 'Carla Ruiz', contenido: 'Perfecto', calificacion: 5 }),
      resena({ id: 1, autor: 'Ana Gómez' }),
    ]);

    render(<ModeracionResenasPage />);

    const nombres = await screen.findAllByRole('heading', { level: 2 });
    expect(nombres.map((nombre) => nombre.textContent)).toEqual(['Carla Ruiz', 'Ana Gómez']);
    expect(screen.getByText('Perfecto')).toBeInTheDocument();
    expect(screen.getByText(/5 de 5 · Alojamiento 30/)).toBeInTheDocument();
  });

  it('avisa cuando no hay reseñas visibles', async () => {
    vi.mocked(listarResenasParaModerar).mockResolvedValue([]);

    render(<ModeracionResenasPage />);

    expect(await screen.findByText('No hay reseñas visibles para moderar.')).toBeInTheDocument();
  });

  it('avisa si no puede cargar las reseñas', async () => {
    vi.mocked(listarResenasParaModerar).mockRejectedValue(new Error('sin conexión'));

    render(<ModeracionResenasPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las reseñas.');
  });

  it('sin motivo no llama a la API y pide el motivo', async () => {
    vi.mocked(listarResenasParaModerar).mockResolvedValue([resena({})]);
    render(<ModeracionResenasPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Ocultar la reseña de Ana Gómez' }));

    expect(screen.getByText('Indica el motivo para ocultar la reseña.')).toBeInTheDocument();
    expect(ocultarResena).not.toHaveBeenCalled();
  });

  it('oculta la reseña con su motivo y la quita de la lista', async () => {
    vi.mocked(listarResenasParaModerar).mockResolvedValue([
      resena({ id: 1, autor: 'Ana Gómez' }),
      resena({ id: 2, autor: 'Carla Ruiz' }),
    ]);
    vi.mocked(ocultarResena).mockResolvedValue({ id: 1, estado: 'OCULTA', motivo: 'Lenguaje ofensivo' });
    render(<ModeracionResenasPage />);

    fireEvent.change(await screen.findByLabelText('Motivo para ocultar la reseña de Ana Gómez'),
      { target: { value: '  Lenguaje ofensivo  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar la reseña de Ana Gómez' }));

    await waitFor(() => expect(ocultarResena).toHaveBeenCalledWith(1, 'Lenguaje ofensivo'));
    expect(await screen.findByText('La reseña de Ana Gómez quedó oculta.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Ana Gómez' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Carla Ruiz' })).toBeInTheDocument();
  });

  it('si la API rechaza la acción muestra su mensaje y conserva la reseña', async () => {
    vi.mocked(listarResenasParaModerar).mockResolvedValue([resena({})]);
    vi.mocked(ocultarResena).mockRejectedValue(new ErrorApi(409, 'Esta reseña ya está oculta.'));
    render(<ModeracionResenasPage />);

    fireEvent.change(await screen.findByLabelText('Motivo para ocultar la reseña de Ana Gómez'),
      { target: { value: 'Ofensiva' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar la reseña de Ana Gómez' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta reseña ya está oculta.');
    expect(screen.getByRole('heading', { name: 'Ana Gómez' })).toBeInTheDocument();
  });
});
