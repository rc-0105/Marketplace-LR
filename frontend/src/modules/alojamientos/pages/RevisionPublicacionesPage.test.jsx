import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import {
  aprobarPublicacion, devolverPublicacion, listarPendientesDeRevision,
} from '../services/alojamientosService.js';
import RevisionPublicacionesPage from './RevisionPublicacionesPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  aprobarPublicacion: vi.fn(),
  devolverPublicacion: vi.fn(),
  listarPendientesDeRevision: vi.fn(),
}));

function pendiente(datos) {
  return {
    id: 1,
    titulo: 'Casa en la playa',
    descripcion: 'Frente al mar',
    ciudad: 'Santa Marta',
    tipo: { id: 2, nombre: 'Casa' },
    capacidad: 4,
    precioNoche: 250000,
    estado: 'PENDIENTE_REVISION',
    enviadaARevisionEn: '2026-10-15T15:00:00Z',
    observacionesRevision: null,
    ...datos,
  };
}

describe('RevisionPublicacionesPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra las pendientes en el orden de la API con sus datos y la fecha de envío', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([
      pendiente({ id: 1, titulo: 'Casa en la playa' }),
      pendiente({ id: 2, titulo: 'Apartamento en el centro', descripcion: 'Cerca al parque', ciudad: null }),
    ]);

    render(<RevisionPublicacionesPage />);

    const titulos = await screen.findAllByRole('heading', { level: 2 });
    expect(titulos.map((titulo) => titulo.textContent)).toEqual(['Casa en la playa', 'Apartamento en el centro']);
    expect(screen.getByText('Frente al mar')).toBeInTheDocument();
    expect(screen.getByText('Casa · 4 huéspedes · $250.000 por noche')).toBeInTheDocument();
    expect(screen.getByText('Casa · Santa Marta · 4 huéspedes · $250.000 por noche')).toBeInTheDocument();
    expect(screen.getAllByText('Enviada a revisión el 15 de octubre de 2026')).toHaveLength(2);
  });

  it('avisa mientras carga', () => {
    vi.mocked(listarPendientesDeRevision).mockReturnValue(new Promise(() => {}));

    render(<RevisionPublicacionesPage />);

    expect(screen.getByText('Cargando publicaciones pendientes…')).toBeInTheDocument();
  });

  it('avisa cuando no hay publicaciones pendientes', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([]);

    render(<RevisionPublicacionesPage />);

    expect(await screen.findByText('No hay publicaciones pendientes de revisión.')).toBeInTheDocument();
  });

  it('al aprobar, la publicación sale del panel y se confirma', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([pendiente({ id: 1 })]);
    vi.mocked(aprobarPublicacion).mockResolvedValue(pendiente({ id: 1, estado: 'ACTIVA' }));

    render(<RevisionPublicacionesPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Aprobar Casa en la playa' }));

    expect(await screen.findByRole('status')).toHaveTextContent('«Casa en la playa» quedó publicada.');
    expect(screen.queryByRole('heading', { name: 'Casa en la playa' })).not.toBeInTheDocument();
    expect(screen.getByText('No hay publicaciones pendientes de revisión.')).toBeInTheDocument();
    expect(aprobarPublicacion).toHaveBeenCalledWith(1);
  });

  it('al devolver con observaciones, las envía sin espacios sobrantes y la publicación sale del panel', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([pendiente({ id: 1 })]);
    vi.mocked(devolverPublicacion).mockResolvedValue(pendiente({ id: 1, estado: 'BORRADOR' }));

    render(<RevisionPublicacionesPage />);
    fireEvent.change(await screen.findByLabelText('Observaciones para Casa en la playa'),
      { target: { value: '  Faltan fotos del baño.  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Devolver Casa en la playa' }));

    expect(await screen.findByRole('status')).toHaveTextContent('«Casa en la playa» volvió al anfitrión.');
    expect(screen.queryByRole('heading', { name: 'Casa en la playa' })).not.toBeInTheDocument();
    expect(devolverPublicacion).toHaveBeenCalledWith(1, 'Faltan fotos del baño.');
  });

  it('no devuelve sin observaciones y explica qué falta', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([pendiente({ id: 1 })]);

    render(<RevisionPublicacionesPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Devolver Casa en la playa' }));

    expect(await screen.findByText('Indica las observaciones para el anfitrión.')).toBeInTheDocument();
    expect(screen.getByLabelText('Observaciones para Casa en la playa')).toHaveAttribute('aria-invalid', 'true');
    expect(devolverPublicacion).not.toHaveBeenCalled();
  });

  it('muestra el mensaje de la API cuando rechaza la revisión y la publicación sigue en el panel', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([pendiente({ id: 1 })]);
    vi.mocked(aprobarPublicacion)
      .mockRejectedValue(new ErrorApi(409, 'Solo se puede revisar una publicación pendiente de revisión.'));

    render(<RevisionPublicacionesPage />);
    fireEvent.click(await screen.findByRole('button', { name: 'Aprobar Casa en la playa' }));

    await waitFor(() => expect(screen.getByRole('alert'))
      .toHaveTextContent('Solo se puede revisar una publicación pendiente de revisión.'));
    expect(screen.getByRole('button', { name: 'Aprobar Casa en la playa' })).toBeEnabled();
  });

  it('muestra un error si no puede cargar las pendientes', async () => {
    vi.mocked(listarPendientesDeRevision).mockRejectedValue(new ErrorApi(500, 'Error interno.'));

    render(<RevisionPublicacionesPage />);

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('No pudimos cargar las publicaciones pendientes. Intenta de nuevo.');
  });
});
