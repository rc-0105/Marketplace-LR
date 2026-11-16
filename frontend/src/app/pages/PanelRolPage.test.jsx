import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listarPendientesDeRevision } from '../../modules/alojamientos/services/alojamientosService.js';
import { guardarSesion, limpiarSesion } from '../../shared/services/sesion.js';
import PanelRolPage from './PanelRolPage.jsx';

vi.mock('../../modules/alojamientos/services/alojamientosService.js', () => ({
  listarPendientesDeRevision: vi.fn(),
}));

function iniciar(rol) {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana Pérez', correo: 'a@b.co', rol, activa: true } });
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <PanelRolPage />
    </MemoryRouter>,
  );
}

describe('PanelRolPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([]);
  });
  afterEach(() => limpiarSesion());

  it.each([
    ['HUESPED', 'Huésped'],
    ['ANFITRION', 'Anfitrión'],
    ['ADMINISTRADOR', 'Administrador'],
  ])('saluda por nombre e indica el rol %s', (rol, nombre) => {
    iniciar(rol);
    renderizar();

    expect(screen.getByRole('heading', { name: 'Hola, Ana Pérez' })).toBeInTheDocument();
    expect(screen.getByText(`Ingresaste como ${nombre}.`)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('—');
  });

  it('al administrador le muestra sus herramientas en lugar del aviso de construcción', async () => {
    iniciar('ADMINISTRADOR');
    renderizar();

    expect(screen.getByRole('heading', { name: 'Tus herramientas' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Revisar publicaciones' }))
      .toHaveAttribute('href', '/administrador/publicaciones');
    expect(screen.getByRole('link', { name: 'Ir a Mi cuenta' })).toHaveAttribute('href', '/mi-cuenta');
    expect(screen.queryByText(/en construcción/)).not.toBeInTheDocument();
    expect(await screen.findByText('No hay publicaciones pendientes de revisión.')).toBeInTheDocument();
  });

  it('al huésped le ofrece buscar alojamiento', () => {
    iniciar('HUESPED');
    renderizar();

    expect(screen.getByRole('link', { name: 'Buscar alojamiento' })).toHaveAttribute('href', '/huesped/busqueda');
  });

  it.each(['HUESPED', 'ANFITRION'])('a un %s no le muestra las herramientas del administrador', (rol) => {
    iniciar(rol);
    renderizar();

    expect(screen.queryByRole('link', { name: 'Revisar publicaciones' })).not.toBeInTheDocument();
    expect(screen.getByText('Tu panel está en construcción. Pronto verás aquí tus herramientas.')).toBeInTheDocument();
    expect(listarPendientesDeRevision).not.toHaveBeenCalled();
  });

  it('no falla si la sesión desaparece mientras está montado', () => {
    renderizar();

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
