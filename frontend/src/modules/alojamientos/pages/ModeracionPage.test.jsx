import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { bloquearPublicacion, buscarPublicacionesModerables } from '../services/alojamientosService.js';
import {
  listarReservasEnRevision, resolverReservaEnRevision,
} from '../../reservas/services/reservaService.js';
import ModeracionPage from './ModeracionPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  bloquearPublicacion: vi.fn(),
  buscarPublicacionesModerables: vi.fn(),
}));

vi.mock('../../reservas/services/reservaService.js', () => ({
  listarReservasEnRevision: vi.fn(),
  resolverReservaEnRevision: vi.fn(),
}));

function moderable(datos) {
  return {
    id: 5, titulo: 'Casa en la playa', ciudad: 'Cartagena', estado: 'ACTIVA', anfitrion: 'Ana Pérez',
    motivoBloqueo: null, ...datos,
  };
}

function enRevision(datos) {
  return {
    id: 9, publicacionId: 5, titulo: 'Casa en la playa', huespedId: 3, huesped: 'Luis Gómez',
    llegada: '2026-12-10', salida: '2026-12-14', total: 1200000, estadoPago: 'APROBADO',
    motivoBloqueo: 'Fotos falsas', ...datos,
  };
}

const normalizar = (texto) => texto.replace(/\s+/g, ' ');

async function buscar(texto) {
  fireEvent.change(screen.getByLabelText('Buscar por título'), { target: { value: texto } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
}

describe('ModeracionPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listarReservasEnRevision).mockResolvedValue([]);
  });

  it('busca por título y lista los resultados con su estado; las bloqueadas muestran el motivo sin acción', async () => {
    vi.mocked(buscarPublicacionesModerables).mockResolvedValue([
      moderable({ id: 5, titulo: 'Casa en la playa', estado: 'ACTIVA' }),
      moderable({ id: 6, titulo: 'Casa de campo', estado: 'PAUSADA', anfitrion: null }),
      moderable({ id: 7, titulo: 'Casa colonial', estado: 'BLOQUEADA', motivoBloqueo: 'Fotos falsas' }),
    ]);
    render(<ModeracionPage />);

    await buscar('casa');

    const resultados = await screen.findByRole('list', { name: 'Resultados de la búsqueda' });
    expect(buscarPublicacionesModerables).toHaveBeenCalledWith('casa');
    const tarjetas = within(resultados).getAllByRole('listitem');
    expect(within(tarjetas[0]).getByText('Activa')).toBeInTheDocument();
    expect(within(tarjetas[0]).getByText('Cartagena · Anfitrión: Ana Pérez')).toBeInTheDocument();
    expect(within(tarjetas[1]).getByText('Pausada')).toBeInTheDocument();
    expect(within(tarjetas[1]).getByText('Cartagena')).toBeInTheDocument();
    expect(within(tarjetas[2]).getByText('Bloqueada')).toBeInTheDocument();
    expect(within(tarjetas[2]).getByText('Motivo del bloqueo: Fotos falsas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bloquear Casa en la playa' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Bloquear Casa de campo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Bloquear Casa colonial' })).not.toBeInTheDocument();
  });

  it('no bloquea sin motivo y lo pide junto al campo', async () => {
    vi.mocked(buscarPublicacionesModerables).mockResolvedValue([moderable()]);
    render(<ModeracionPage />);
    await buscar('casa');

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear Casa en la playa' }));

    expect(await screen.findByText('Indica el motivo.')).toBeInTheDocument();
    expect(screen.getByLabelText('Motivo del bloqueo de Casa en la playa')).toHaveAttribute('aria-invalid', 'true');
    expect(bloquearPublicacion).not.toHaveBeenCalled();
  });

  it('al bloquear muestra la publicación bloqueada con su motivo y recarga las reservas en revisión', async () => {
    vi.mocked(buscarPublicacionesModerables).mockResolvedValue([moderable()]);
    vi.mocked(bloquearPublicacion).mockResolvedValue({ id: 5, titulo: 'Casa en la playa', estado: 'BLOQUEADA',
      motivoBloqueo: 'Fotos falsas' });
    render(<ModeracionPage />);
    await waitFor(() => expect(listarReservasEnRevision).toHaveBeenCalledTimes(1));
    vi.mocked(listarReservasEnRevision).mockResolvedValue([enRevision()]);
    await buscar('casa');

    fireEvent.change(await screen.findByLabelText('Motivo del bloqueo de Casa en la playa'),
      { target: { value: '  Fotos falsas ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear Casa en la playa' }));

    expect(await screen.findByRole('status')).toHaveTextContent('«Casa en la playa» quedó bloqueada.');
    expect(bloquearPublicacion).toHaveBeenCalledWith(5, 'Fotos falsas');
    const resultados = screen.getByRole('list', { name: 'Resultados de la búsqueda' });
    expect(within(resultados).getByText('Bloqueada')).toBeInTheDocument();
    expect(within(resultados).getByText('Motivo del bloqueo: Fotos falsas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Bloquear Casa en la playa' })).not.toBeInTheDocument();
    expect(listarReservasEnRevision).toHaveBeenCalledTimes(2);
    expect(await screen.findByRole('button', { name: 'Confirmar reserva #9' })).toBeInTheDocument();
  });

  it('muestra el mensaje de la API si rechaza el bloqueo', async () => {
    vi.mocked(buscarPublicacionesModerables).mockResolvedValue([moderable()]);
    vi.mocked(bloquearPublicacion).mockRejectedValue(
      new ErrorApi(409, 'Solo se puede bloquear una publicación activa o pausada.'));
    render(<ModeracionPage />);
    await buscar('casa');

    fireEvent.change(await screen.findByLabelText('Motivo del bloqueo de Casa en la playa'),
      { target: { value: 'Fotos falsas' } });
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear Casa en la playa' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Solo se puede bloquear una publicación activa o pausada.');
    expect(screen.getByRole('button', { name: 'Bloquear Casa en la playa' })).toBeInTheDocument();
  });

  it('lista las reservas en revisión con huésped, fechas, total y motivo del bloqueo', async () => {
    vi.mocked(listarReservasEnRevision).mockResolvedValue([
      enRevision(),
      enRevision({ id: 10, huesped: null, llegada: '2026-12-20', salida: '2026-12-22', total: 500000 }),
    ]);
    render(<ModeracionPage />);

    const lista = await screen.findByRole('list', { name: 'Reservas en revisión' });
    const [primera, segunda] = within(lista).getAllByRole('listitem');
    expect(within(primera).getByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(within(primera).getByText('Reserva #9 · Huésped: Luis Gómez')).toBeInTheDocument();
    expect(normalizar(primera.textContent)).toContain('10 de dic de 2026 – 14 de dic de 2026');
    expect(normalizar(primera.textContent)).toContain('$ 1.200.000');
    expect(within(primera).getByText('Motivo del bloqueo: Fotos falsas')).toBeInTheDocument();
    expect(within(segunda).getByText('Reserva #10')).toBeInTheDocument();
    expect(normalizar(segunda.textContent)).toContain('$ 500.000');
  });

  it('avisa cuando no hay reservas en revisión', async () => {
    render(<ModeracionPage />);

    expect(await screen.findByText('No hay reservas en revisión.')).toBeInTheDocument();
  });

  it('al confirmar una reserva la quita de la lista y lo confirma', async () => {
    vi.mocked(listarReservasEnRevision).mockResolvedValue([enRevision(), enRevision({ id: 10 })]);
    vi.mocked(resolverReservaEnRevision).mockResolvedValue({});
    render(<ModeracionPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar reserva #9' }));

    expect(await screen.findByRole('status')).toHaveTextContent('La reserva #9 quedó confirmada.');
    expect(resolverReservaEnRevision).toHaveBeenCalledWith(9, 'CONFIRMAR');
    expect(screen.queryByRole('button', { name: 'Confirmar reserva #9' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar reserva #10' })).toBeInTheDocument();
  });

  it('no cancela una reserva sin motivo', async () => {
    vi.mocked(listarReservasEnRevision).mockResolvedValue([enRevision()]);
    render(<ModeracionPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar con devolución la reserva #9' }));

    expect(await screen.findByText('Indica el motivo.')).toBeInTheDocument();
    expect(screen.getByLabelText('Motivo de cancelación de la reserva #9')).toHaveAttribute('aria-invalid', 'true');
    expect(resolverReservaEnRevision).not.toHaveBeenCalled();
  });

  it('cancela con motivo, quita la reserva y lo confirma', async () => {
    vi.mocked(listarReservasEnRevision).mockResolvedValue([enRevision()]);
    vi.mocked(resolverReservaEnRevision).mockResolvedValue({});
    render(<ModeracionPage />);

    fireEvent.change(await screen.findByLabelText('Motivo de cancelación de la reserva #9'),
      { target: { value: ' Publicación engañosa ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar con devolución la reserva #9' }));

    expect(await screen.findByRole('status'))
      .toHaveTextContent('La reserva #9 quedó cancelada y se devolvió el total al huésped.');
    expect(resolverReservaEnRevision).toHaveBeenCalledWith(9, 'CANCELAR', 'Publicación engañosa');
    expect(screen.getByText('No hay reservas en revisión.')).toBeInTheDocument();
  });

  it('si la pasarela no procesó la devolución muestra el detalle y la reserva sigue en la lista', async () => {
    vi.mocked(listarReservasEnRevision).mockResolvedValue([enRevision()]);
    vi.mocked(resolverReservaEnRevision).mockRejectedValue(new ErrorApi(502,
      'No se pudo procesar la devolución en la pasarela de pagos. Intenta de nuevo.'));
    render(<ModeracionPage />);

    fireEvent.change(await screen.findByLabelText('Motivo de cancelación de la reserva #9'),
      { target: { value: 'Publicación engañosa' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar con devolución la reserva #9' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('No se pudo procesar la devolución en la pasarela de pagos. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Confirmar reserva #9' })).toBeInTheDocument();
  });

  it('muestra un error genérico si no puede cargar las reservas en revisión', async () => {
    vi.mocked(listarReservasEnRevision).mockRejectedValue(new Error('red'));
    render(<ModeracionPage />);

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('No pudimos cargar las reservas en revisión. Intenta de nuevo.');
  });
});
