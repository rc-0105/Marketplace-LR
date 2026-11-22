import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listarSolicitudes } from '../services/reservaService.js';
import SolicitudesReservaPage from './SolicitudesReservaPage.jsx';

vi.mock('../services/reservaService.js', () => ({
  listarSolicitudes: vi.fn(),
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
});
