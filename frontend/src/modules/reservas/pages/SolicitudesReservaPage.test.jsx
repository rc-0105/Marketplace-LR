import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { formatearPlazo } from '../models/reserva.js';
import { aprobarSolicitud, listarSolicitudes, rechazarSolicitud } from '../services/reservaService.js';
import SolicitudesReservaPage from './SolicitudesReservaPage.jsx';

vi.mock('../services/reservaService.js', () => ({
  listarSolicitudes: vi.fn(), aprobarSolicitud: vi.fn(), rechazarSolicitud: vi.fn(),
}));

function solicitud(datos) {
  return {
    id: 4, publicacionId: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null,
    huespedNombre: 'Ana Pérez', llegada: '2026-12-10', salida: '2026-12-14', noches: 4, total: 485000,
    venceEn: '2026-11-22T15:00:00Z', ...datos,
  };
}

const PRIMERA = solicitud({ id: 4 });
const SEGUNDA = solicitud({ id: 8, titulo: 'Loft centro', ciudad: 'Bogotá', huespedNombre: 'Luis Gómez', noches: 1,
  total: 120000 });

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <SolicitudesReservaPage />
    </MemoryRouter>,
  );
}

async function filas() {
  return within(await screen.findByRole('list', { name: 'Solicitudes de reserva' })).getAllByRole('listitem');
}

function abrirRechazo(fila) {
  fireEvent.click(within(fila).getByRole('button', { name: 'Rechazar' }));
  return screen.getByRole('dialog');
}

describe('SolicitudesReservaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lista cada solicitud con alojamiento, huésped, fechas, noches, total y plazo', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA, SEGUNDA]);

    renderizar();

    const items = await filas();
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Casa en la playa');
    expect(items[0]).toHaveTextContent('Santa Marta');
    expect(items[0]).toHaveTextContent('Ana Pérez');
    expect(items[0]).toHaveTextContent('4 noches');
    expect(items[0]).toHaveTextContent(/485\.000/);
    expect(items[0]).toHaveTextContent(/Responde antes del/);
    expect(items[1]).toHaveTextContent('Loft centro');
    expect(items[1]).toHaveTextContent('1 noche');
    expect(within(items[0]).getByRole('button', { name: 'Aprobar' })).toBeInTheDocument();
    expect(within(items[0]).getByRole('button', { name: 'Rechazar' })).toBeInTheDocument();
  });

  it('sin solicitudes muestra el estado vacío', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([]);

    renderizar();

    expect(await screen.findByText('No tienes solicitudes pendientes.')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Solicitudes de reserva' })).not.toBeInTheDocument();
  });

  it('si no se pueden cargar muestra el mensaje de la API o uno general', async () => {
    vi.mocked(listarSolicitudes).mockRejectedValueOnce(new ErrorApi(403, 'No tienes permiso.'));
    renderizar();
    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso.');
  });

  it('sin respuesta de la API muestra el mensaje general', async () => {
    vi.mocked(listarSolicitudes).mockRejectedValueOnce(new ErrorApi(0, 'Sin conexión'));
    renderizar();
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las solicitudes. Intenta de nuevo.');
  });

  it('aprobar quita la solicitud y avisa el plazo de pago que trae la API', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA, SEGUNDA]);
    vi.mocked(aprobarSolicitud).mockResolvedValue({ reservaId: 4, estado: 'PENDIENTE', venceEn: '2026-11-23T10:00:00Z' });
    renderizar();
    const [primera] = await filas();

    fireEvent.click(within(primera).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Solicitud aprobada');
    expect(screen.getByRole('status')).toHaveTextContent(formatearPlazo('2026-11-23T10:00:00Z'));
    expect(screen.getByRole('status')).not.toHaveTextContent('24 horas');
    expect(aprobarSolicitud).toHaveBeenCalledWith(4);
    const restantes = await filas();
    expect(restantes).toHaveLength(1);
    expect(restantes[0]).toHaveTextContent('Loft centro');
  });

  it('si al aprobar la API responde 409 muestra el mensaje y refresca la lista', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValueOnce([PRIMERA, SEGUNDA]).mockResolvedValueOnce([SEGUNDA]);
    vi.mocked(aprobarSolicitud).mockRejectedValue(new ErrorApi(409, 'La solicitud ya venció.'));
    renderizar();
    const [primera] = await filas();

    fireEvent.click(within(primera).getByRole('button', { name: 'Aprobar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La solicitud ya venció.');
    await waitFor(() => expect(listarSolicitudes).toHaveBeenCalledTimes(2));
    const restantes = await screen.findAllByRole('listitem');
    expect(restantes).toHaveLength(1);
    expect(restantes[0]).toHaveTextContent('Loft centro');
  });

  it('rechazar abre un diálogo con el motivo obligatorio, su contador y sin enviar vacío', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA]);
    renderizar();
    const [primera] = await filas();

    const dialogo = abrirRechazo(primera);

    expect(dialogo).toHaveAttribute('aria-modal', 'true');
    const motivo = within(dialogo).getByLabelText('Motivo del rechazo');
    expect(motivo).toHaveAttribute('maxLength', '500');
    expect(motivo).toHaveFocus();
    expect(within(dialogo).getByText('0/500')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' })).toBeDisabled();

    fireEvent.change(motivo, { target: { value: 'Fechas bloqueadas' } });
    expect(within(dialogo).getByText('17/500')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' })).toBeEnabled();

    fireEvent.change(motivo, { target: { value: '    ' } });
    expect(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' })).toBeDisabled();
  });

  it('confirmar el rechazo envía el motivo, cierra el diálogo y quita la solicitud', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA, SEGUNDA]);
    vi.mocked(rechazarSolicitud).mockResolvedValue({ reservaId: 4, estado: 'RECHAZADA', motivo: 'Fechas bloqueadas' });
    renderizar();
    const [primera] = await filas();
    const dialogo = abrirRechazo(primera);

    fireEvent.change(within(dialogo).getByLabelText('Motivo del rechazo'), { target: { value: 'Fechas bloqueadas' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(rechazarSolicitud).toHaveBeenCalledWith(4, 'Fechas bloqueadas');
    expect(screen.getByRole('status')).toHaveTextContent('Solicitud rechazada');
    const restantes = await filas();
    expect(restantes).toHaveLength(1);
    expect(restantes[0]).toHaveTextContent('Loft centro');
  });

  it('un motivo inválido (400) se muestra en el diálogo y deja corregirlo', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA]);
    vi.mocked(rechazarSolicitud).mockRejectedValue(new ErrorApi(400, 'El motivo no es válido.'));
    renderizar();
    const [primera] = await filas();
    const dialogo = abrirRechazo(primera);

    fireEvent.change(within(dialogo).getByLabelText('Motivo del rechazo'), { target: { value: 'x' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' }));

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('El motivo no es válido.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' })).toBeEnabled();
  });

  it('si al rechazar la API responde 409 cierra el diálogo, avisa y refresca la lista', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValueOnce([PRIMERA]).mockResolvedValueOnce([]);
    vi.mocked(rechazarSolicitud).mockRejectedValue(new ErrorApi(409, 'La solicitud ya fue respondida.'));
    renderizar();
    const [primera] = await filas();
    const dialogo = abrirRechazo(primera);

    fireEvent.change(within(dialogo).getByLabelText('Motivo del rechazo'), { target: { value: 'No puedo' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar rechazo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La solicitud ya fue respondida.');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByText('No tienes solicitudes pendientes.')).toBeInTheDocument();
    expect(listarSolicitudes).toHaveBeenCalledTimes(2);
  });

  it('Cancelar o Escape cierran el diálogo sin llamar a la API', async () => {
    vi.mocked(listarSolicitudes).mockResolvedValue([PRIMERA]);
    renderizar();
    const [primera] = await filas();

    fireEvent.click(within(abrirRechazo(primera)).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.keyDown(abrirRechazo(primera), { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(rechazarSolicitud).not.toHaveBeenCalled();
  });
});
