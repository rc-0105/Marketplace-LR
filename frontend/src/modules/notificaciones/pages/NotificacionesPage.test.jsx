import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { fijarNoLeidas, obtenerNoLeidas } from '../services/contadorNotificaciones.js';
import { listarNotificaciones, marcarLeida } from '../services/notificacionesService.js';
import NotificacionesPage from './NotificacionesPage.jsx';

vi.mock('../services/notificacionesService.js', () => ({
  listarNotificaciones: vi.fn(),
  marcarLeida: vi.fn(),
}));

function notificacion(datos) {
  return {
    id: 1,
    tipo: 'RESERVA_CONFIRMADA',
    mensaje: 'Tu reserva en «Casa» del 01/12/2026 al 05/12/2026 quedó confirmada.',
    entidad: 'Reserva',
    entidadId: 7,
    leida: false,
    creadaEn: '2026-11-21T10:00:00-05:00',
    ...datos,
  };
}

const NO_LEIDA = notificacion({ id: 1 });
const LEIDA = notificacion({ id: 2, tipo: 'PUBLICACION_ESTADO', mensaje: 'Tu publicación «Loft» quedó Pausada.',
  entidad: 'Publicacion', leida: true, creadaEn: '2026-11-20T08:30:00-05:00' });

function textoDe(elemento) {
  return elemento.textContent.replace(/\s+/g, ' ');
}

describe('NotificacionesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    fijarNoLeidas(0);
  });

  it('lista las notificaciones con su mensaje y fecha, distinguiendo las no leídas', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 1, notificaciones: [NO_LEIDA, LEIDA] });
    render(<NotificacionesPage />);

    expect(screen.getByRole('heading', { name: 'Notificaciones' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent(NO_LEIDA.mensaje);
    expect(textoDe(items[0])).toContain('21/11/2026 10:00');
    expect(items[0]).toHaveTextContent('Sin leer');
    expect(items[1]).toHaveTextContent(LEIDA.mensaje);
    expect(textoDe(items[1])).toContain('20/11/2026 08:30');
    expect(items[1]).not.toHaveTextContent('Sin leer');
    expect(obtenerNoLeidas()).toBe(1);
  });

  it.each([
    ['SOLICITUD_RECIBIDA', 'Solicitud', 'Ver solicitudes', '/anfitrion/solicitudes'],
    ['SOLICITUD_APROBADA', 'Solicitud', 'Ver reserva', '/huesped/reservas/7'],
    ['SOLICITUD_RECHAZADA', 'Solicitud', 'Ver reserva', '/huesped/reservas/7'],
    ['SOLICITUD_VENCIDA', 'Solicitud', 'Ver reserva', '/huesped/reservas/7'],
  ])('muestra %s con su categoría y su enlace (SOF-79)', async (tipo, categoria, texto, ruta) => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 1, notificaciones: [
      notificacion({ id: 5, tipo, mensaje: 'Mensaje de solicitud' })] });
    render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><NotificacionesPage /></MemoryRouter>);

    const item = (await screen.findAllByRole('listitem'))[0];
    expect(item).toHaveTextContent(categoria);
    expect(item).toHaveTextContent('Mensaje de solicitud');
    expect(within(item).getByRole('link', { name: texto })).toHaveAttribute('href', ruta);
  });

  it('un tipo sin enlace no muestra ninguno', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 1, notificaciones: [NO_LEIDA] });
    render(<MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><NotificacionesPage /></MemoryRouter>);

    await screen.findAllByRole('listitem');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('sin notificaciones muestra el estado vacío', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [] });
    render(<NotificacionesPage />);

    expect(await screen.findByText('No tienes notificaciones.')).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  it('al abrir una no leída la marca como leída y baja el contador', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 2, notificaciones: [NO_LEIDA, LEIDA] });
    vi.mocked(marcarLeida).mockResolvedValue({ ...NO_LEIDA, leida: true });
    render(<NotificacionesPage />);

    fireEvent.click(await screen.findByRole('button', { name: /quedó confirmada/ }));

    await waitFor(() => expect(obtenerNoLeidas()).toBe(1));
    expect(marcarLeida).toHaveBeenCalledWith(1);
    expect(marcarLeida).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: /quedó confirmada/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole('listitem')[0]).not.toHaveTextContent('Sin leer');
  });

  it('abrir una ya leída no llama a la API ni cambia el contador', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [LEIDA] });
    render(<NotificacionesPage />);

    fireEvent.click(await screen.findByText(LEIDA.mensaje));

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(marcarLeida).not.toHaveBeenCalled();
    expect(obtenerNoLeidas()).toBe(0);
  });

  it('si la carga falla muestra el detalle de la API', async () => {
    vi.mocked(listarNotificaciones).mockRejectedValue(new ErrorApi(503, 'Servicio no disponible'));
    render(<NotificacionesPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Servicio no disponible');
  });

  it('si la carga falla sin respuesta muestra un mensaje genérico', async () => {
    vi.mocked(listarNotificaciones).mockRejectedValue(new Error('boom'));
    render(<NotificacionesPage />);

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('No pudimos cargar tus notificaciones. Intenta de nuevo.');
  });

  it('si marcarla falla muestra el error y la deja sin leer', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 1, notificaciones: [NO_LEIDA] });
    vi.mocked(marcarLeida).mockRejectedValue(new ErrorApi(404, 'La notificación no existe.'));
    render(<NotificacionesPage />);

    fireEvent.click(await screen.findByRole('button', { name: /quedó confirmada/ }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La notificación no existe.');
    expect(screen.getByRole('button', { name: /quedó confirmada/ })).toBeEnabled();
    expect(obtenerNoLeidas()).toBe(1);
  });
});
