import { act, fireEvent, render, screen } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { guardarSesion, limpiarSesion, obtenerSesion } from '../../../shared/services/sesion.js';
import ProveedorSesion from '../../../shared/sesion/ProveedorSesion.jsx';
import CalendarioPage from './CalendarioPage.jsx';

const CALENDARIO = {
  publicacionId: 10,
  desde: '2026-12-01',
  hasta: '2026-12-04',
  tienePeriodos: true,
  dias: [
    { fecha: '2026-12-01', estado: 'DISPONIBLE' },
    { fecha: '2026-12-02', estado: 'RESERVADO' },
    { fecha: '2026-12-03', estado: 'BLOQUEADO' },
  ],
};

function responder(estado, cuerpo) {
  return { ok: estado < 400, status: estado, text: async () => JSON.stringify(cuerpo) };
}

function Ubicacion() {
  const { pathname, state } = useLocation();
  return <p>{`${pathname} ${state?.sesionExpirada ? 'expirada' : ''}`}</p>;
}

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/huesped/publicaciones/10/calendario']}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/huesped/publicaciones/:id/calendario" element={<CalendarioPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('CalendarioPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    guardarSesion({
      token: 't',
      expiraEn: new Date(Date.now() + 3600_000).toISOString(),
      usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true },
    });
  });

  afterEach(() => {
    limpiarSesion();
    vi.unstubAllGlobals();
  });

  it('muestra que está cargando y no la grilla mientras espera a la API', () => {
    fetch.mockReturnValue(new Promise(() => {}));

    renderizar();

    expect(screen.getByRole('status')).toHaveTextContent('Cargando calendario');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('pide el calendario de la publicación de la ruta y lo muestra', async () => {
    fetch.mockResolvedValue(responder(200, CALENDARIO));

    renderizar();

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/v1/publicaciones/10/calendario', expect.objectContaining({ method: 'GET' }));
    expect(screen.getByRole('heading', { level: 1, name: 'Calendario de disponibilidad' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^2 de diciembre de 2026.*Reservado/ })).toBeInTheDocument();
    expect(screen.queryByText(/Cargando calendario/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la búsqueda' })).toHaveAttribute('href', '/huesped/busqueda');
  });

  it('al huésped solo le dice Reservado, sin número de reserva', async () => {
    fetch.mockResolvedValue(responder(200, CALENDARIO));

    renderizar();

    const dia = await screen.findByRole('cell', { name: /^2 de diciembre de 2026/ });
    expect(dia.getAttribute('aria-label')).not.toMatch(/Reserva \d/);
  });

  it('ante un error muestra la alerta sin grilla y permite reintentar', async () => {
    fetch.mockResolvedValueOnce(responder(500, { detail: 'Fallo interno' }));
    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el calendario');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();

    fetch.mockResolvedValueOnce(responder(200, CALENDARIO));
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('si no hay conexión también ofrece reintentar', async () => {
    fetch.mockRejectedValue(new TypeError('Failed to fetch'));

    renderizar();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('con un 404 muestra siempre el mismo mensaje, sin importar el detalle ni cerrar la sesión', async () => {
    fetch.mockResolvedValue(responder(404, { detail: 'No encontramos esa publicación.' }));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta publicación no está disponible.');
    expect(screen.queryByText('No encontramos esa publicación.')).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(obtenerSesion()).not.toBeNull();
  });

  it('con un 401 cierra la sesión y redirige al inicio de sesión con el aviso de sesión expirada', async () => {
    fetch.mockResolvedValue(responder(401, { detail: 'Sesión vencida' }));

    render(
      <MemoryRouter initialEntries={['/huesped/publicaciones/10/calendario']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <ProveedorSesion>
          <Routes>
            <Route path="/huesped/publicaciones/:id/calendario" element={<CalendarioPage />} />
            <Route path="/inicio-sesion" element={<Ubicacion />} />
          </Routes>
        </ProveedorSesion>
      </MemoryRouter>,
    );

    expect(await screen.findByText('/inicio-sesion expirada')).toBeInTheDocument();
    expect(obtenerSesion()).toBeNull();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('ignora la respuesta tardía de una publicación anterior si la ruta cambió', async () => {
    const pendientes = {};
    fetch.mockImplementation((ruta) => new Promise((resultado) => { pendientes[ruta] = resultado; }));
    render(
      <MemoryRouter initialEntries={['/huesped/publicaciones/10/calendario']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Link to="/huesped/publicaciones/20/calendario">Ir a la 20</Link>
        <Routes>
          <Route path="/huesped/publicaciones/:id/calendario" element={<CalendarioPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('link', { name: 'Ir a la 20' }));
    await act(async () => {
      pendientes['/api/v1/publicaciones/20/calendario'](
        responder(200, { ...CALENDARIO, publicacionId: 20, dias: [{ fecha: '2026-12-01', estado: 'BLOQUEADO' }] }));
    });
    expect(await screen.findByRole('cell', { name: /^1 de diciembre de 2026.*Bloqueado/ })).toBeInTheDocument();

    await act(async () => {
      pendientes['/api/v1/publicaciones/10/calendario'](responder(200, CALENDARIO));
    });

    expect(screen.getByRole('cell', { name: /^1 de diciembre de 2026.*Bloqueado/ })).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: /Disponible/ })).not.toBeInTheDocument();
  });
});
