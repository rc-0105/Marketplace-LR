import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { listarNotificaciones } from '../../modules/notificaciones/services/notificacionesService.js';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import Encabezado from './Encabezado.jsx';

vi.mock('../../modules/notificaciones/services/notificacionesService.js', () => ({
  listarNotificaciones: vi.fn(() => Promise.resolve({ noLeidas: 0, notificaciones: [] })),
  marcarLeida: vi.fn(),
}));

function iniciar(rol = 'HUESPED') {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true } });
}

function renderizar(alCerrarSesion, ruta = '/mi-cuenta') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <Encabezado alCerrarSesion={alCerrarSesion} />
    </MemoryRouter>,
  );
}

describe('Encabezado', () => {
  afterEach(() => {
    limpiarSesion();
    vi.clearAllMocks();
  });

  it('muestra la marca con un enlace al inicio', () => {
    renderizar(vi.fn());

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir al inicio' })).toHaveAttribute('href', '/');
  });

  it.each([
    ['HUESPED', '/huesped'],
    ['ANFITRION', '/anfitrion/publicaciones'],
    ['ADMINISTRADOR', '/administrador'],
  ])('con sesión de %s la marca lleva al panel de su rol', (rol, panel) => {
    iniciar(rol);
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Inversiones LR, ir a tu panel' })).toHaveAttribute('href', panel);
  });

  it.each([
    ['HUESPED', '/mi-cuenta', '/huesped'],
    ['ANFITRION', '/anfitrion/publicaciones/nueva', '/anfitrion/publicaciones'],
    ['ADMINISTRADOR', '/administrador/publicaciones', '/administrador'],
  ])('a un %s en %s no le ofrece "Volver a mi panel": la marca ya lleva a su panel', (rol, ruta, panel) => {
    iniciar(rol);
    renderizar(vi.fn(), ruta);

    expect(screen.queryByRole('link', { name: /Volver a mi panel/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir a tu panel' })).toHaveAttribute('href', panel);
  });

  it('con sesión muestra el botón de cerrar sesión', () => {
    iniciar();
    renderizar(vi.fn());

    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('al anfitrión le ofrece el enlace a sus publicaciones', () => {
    iniciar('ANFITRION');
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Mis publicaciones' })).toHaveAttribute('href', '/anfitrion/publicaciones');
  });

  it('al anfitrión le ofrece sus solicitudes de reserva y al huésped no (SOF-79)', () => {
    iniciar('ANFITRION');
    renderizar(vi.fn());
    expect(screen.getByRole('link', { name: 'Solicitudes' })).toHaveAttribute('href', '/anfitrion/solicitudes');
  });

  it('al huésped no le ofrece las solicitudes de reserva', () => {
    iniciar('HUESPED');
    renderizar(vi.fn());
    expect(screen.queryByRole('link', { name: 'Solicitudes' })).not.toBeInTheDocument();
  });

  it('al huésped le ofrece sus reservas y solo a él', () => {
    iniciar('HUESPED');
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Mis reservas' })).toHaveAttribute('href', '/huesped/reservas');
  });

  it.each(['ANFITRION', 'ADMINISTRADOR'])('al %s no le ofrece reservas de huésped', (rol) => {
    iniciar(rol);
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Mis reservas' })).not.toBeInTheDocument();
  });

  it('al huésped no le ofrece publicaciones', () => {
    iniciar('HUESPED');
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Mis publicaciones' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Revisar publicaciones' })).not.toBeInTheDocument();
  });

  it('al administrador le ofrece revisar publicaciones y solo a él', () => {
    iniciar('ADMINISTRADOR');
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Revisar publicaciones' }))
      .toHaveAttribute('href', '/administrador/publicaciones');
    expect(screen.queryByRole('link', { name: 'Mis publicaciones' })).not.toBeInTheDocument();
  });

  it('al anfitrión no le ofrece revisar publicaciones', () => {
    iniciar('ANFITRION');
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Revisar publicaciones' })).not.toBeInTheDocument();
  });

  it('sin sesión ofrece iniciar sesión y crear una cuenta', () => {
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/inicio-sesion');
    expect(screen.getByRole('link', { name: 'Crear cuenta' })).toHaveAttribute('href', '/registro');
  });

  it('con sesión no ofrece iniciar sesión ni crear una cuenta', () => {
    iniciar('HUESPED');
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Iniciar sesión' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Crear cuenta' })).not.toBeInTheDocument();
  });

  it('sin sesión no muestra el botón de cerrar sesión', () => {
    renderizar(vi.fn());

    expect(screen.queryByRole('button', { name: 'Cerrar sesión' })).not.toBeInTheDocument();
  });

  it('al pulsarlo invoca alCerrarSesion y se deshabilita mientras dura', async () => {
    iniciar();
    let terminar;
    const alCerrarSesion = vi.fn(() => new Promise((resolver) => { terminar = resolver; }));
    renderizar(alCerrarSesion);

    const boton = screen.getByRole('button', { name: 'Cerrar sesión' });
    fireEvent.click(boton);
    fireEvent.click(boton);

    expect(alCerrarSesion).toHaveBeenCalledTimes(1);
    expect(boton).toBeDisabled();
    terminar();
    await waitFor(() => expect(boton).toBeEnabled());
  });

  it('con sesión ofrece el enlace a Mi cuenta', () => {
    iniciar();
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute('href', '/mi-cuenta');
  });

  it.each(['HUESPED', 'ANFITRION', 'ADMINISTRADOR'])(
    'a un %s le muestra la campana con sus no leídas junto a las acciones de siempre', async (rol) => {
      vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 3, notificaciones: [] });
      iniciar(rol);
      renderizar(vi.fn());

      expect(await screen.findByRole('link', { name: 'Notificaciones, 3 sin leer' }))
        .toHaveAttribute('href', '/notificaciones');
      expect(screen.getByRole('link', { name: 'Mi cuenta' })).toHaveAttribute('href', '/mi-cuenta');
      expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
    },
  );

  it.each(['HUESPED', 'ANFITRION', 'ADMINISTRADOR'])(
    'a un %s le pone la campana justo antes de Cerrar sesión, después de la navegación', async (rol) => {
      vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [] });
      iniciar(rol);
      renderizar(vi.fn());

      const campana = await screen.findByRole('link', { name: /^Notificaciones/ });
      const cerrar = screen.getByRole('button', { name: 'Cerrar sesión' });
      const cuenta = screen.getByRole('link', { name: 'Mi cuenta' });
      const controles = [...screen.getByRole('banner').querySelectorAll('a, button')];

      expect(controles.indexOf(cerrar)).toBe(controles.indexOf(campana) + 1);
      expect(controles.indexOf(cuenta)).toBeLessThan(controles.indexOf(campana));
    },
  );

  it('al administrador le ofrece desde el encabezado todas sus secciones y a los demás roles no', () => {
    iniciar('ADMINISTRADOR');
    renderizar(vi.fn());

    const secciones = {
      'Revisar publicaciones': '/administrador/publicaciones',
      'Moderación': '/administrador/moderacion',
      'Reseñas': '/administrador/resenas',
      'Usuarios': '/administrador/usuarios',
      'Auditoría': '/administrador/auditoria',
    };
    Object.entries(secciones).forEach(([nombre, ruta]) => {
      expect(screen.getByRole('link', { name: nombre })).toHaveAttribute('href', ruta);
    });
  });

  it.each(['HUESPED', 'ANFITRION'])('a un %s no le ofrece las secciones de administración', (rol) => {
    iniciar(rol);
    renderizar(vi.fn());

    ['Moderación', 'Reseñas', 'Usuarios', 'Auditoría'].forEach((nombre) => {
      expect(screen.queryByRole('link', { name: nombre })).not.toBeInTheDocument();
    });
  });

  it('sin sesión no muestra la campana ni consulta notificaciones', () => {
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: /^Notificaciones/ })).not.toBeInTheDocument();
    expect(listarNotificaciones).not.toHaveBeenCalled();
  });

  it('sin sesión no ofrece el enlace a Mi cuenta', () => {
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Mi cuenta' })).not.toBeInTheDocument();
  });
});
