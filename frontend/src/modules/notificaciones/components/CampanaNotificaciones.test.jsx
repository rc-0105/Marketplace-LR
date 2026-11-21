import { act, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { guardarSesion, limpiarSesion } from '../../../shared/services/sesion.js';
import { fijarNoLeidas } from '../services/contadorNotificaciones.js';
import { listarNotificaciones } from '../services/notificacionesService.js';
import CampanaNotificaciones from './CampanaNotificaciones.jsx';

vi.mock('../services/notificacionesService.js', () => ({
  listarNotificaciones: vi.fn(),
  marcarLeida: vi.fn(),
}));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/mi-cuenta']}>
      <CampanaNotificaciones />
    </MemoryRouter>,
  );
}

describe('CampanaNotificaciones', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    fijarNoLeidas(0);
    const expiraEn = new Date(Date.now() + 3600_000).toISOString();
    guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true } });
  });

  afterEach(() => limpiarSesion());

  it('muestra el contador de no leídas que devuelve la API y lleva a /notificaciones', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 2, notificaciones: [] });
    renderizar();

    const enlace = await screen.findByRole('link', { name: 'Notificaciones, 2 sin leer' });
    expect(enlace).toHaveAttribute('href', '/notificaciones');
    expect(enlace).toHaveTextContent('2');
    expect(listarNotificaciones).toHaveBeenCalledTimes(1);
  });

  it('sin notificaciones por leer no muestra el contador', async () => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [] });
    renderizar();

    await waitFor(() => expect(listarNotificaciones).toHaveBeenCalled());
    const enlace = screen.getByRole('link', { name: 'Notificaciones' });
    expect(enlace).toHaveAttribute('href', '/notificaciones');
    expect(enlace).not.toHaveTextContent('0');
  });

  it('si la consulta falla no muestra contador ni error', async () => {
    fijarNoLeidas(5);
    vi.mocked(listarNotificaciones).mockRejectedValue(new ErrorApi(0, 'Sin conexión'));
    await act(async () => renderizar());

    expect(screen.getByRole('link', { name: 'Notificaciones' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('5')).not.toBeInTheDocument();
  });
});
