import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import Encabezado from './Encabezado.jsx';

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
  afterEach(() => limpiarSesion());

  it('muestra la marca con un enlace al inicio', () => {
    renderizar(vi.fn());

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir al inicio' })).toHaveAttribute('href', '/');
  });

  it.each([
    ['HUESPED', '/huesped'],
    ['ANFITRION', '/anfitrion'],
    ['ADMINISTRADOR', '/administrador'],
  ])('con sesión de %s la marca lleva al panel de su rol', (rol, panel) => {
    iniciar(rol);
    renderizar(vi.fn());

    expect(screen.getByRole('link', { name: 'Inversiones LR, ir a tu panel' })).toHaveAttribute('href', panel);
  });

  it.each([
    ['HUESPED', '/mi-cuenta', '/huesped'],
    ['ANFITRION', '/anfitrion/publicaciones/nueva', '/anfitrion'],
    ['ADMINISTRADOR', '/administrador/publicaciones', '/administrador'],
  ])('a un %s en %s le ofrece volver a su panel', (rol, ruta, panel) => {
    iniciar(rol);
    renderizar(vi.fn(), ruta);

    expect(screen.getByRole('link', { name: 'Volver a mi panel' })).toHaveAttribute('href', panel);
  });

  it('en su propio panel no ofrece volver al panel', () => {
    iniciar('ANFITRION');
    renderizar(vi.fn(), '/anfitrion');

    expect(screen.queryByRole('link', { name: 'Volver a mi panel' })).not.toBeInTheDocument();
  });

  it('sin sesión no ofrece volver al panel', () => {
    renderizar(vi.fn(), '/registro');

    expect(screen.queryByRole('link', { name: 'Volver a mi panel' })).not.toBeInTheDocument();
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

  it('sin sesión no ofrece el enlace a Mi cuenta', () => {
    renderizar(vi.fn());

    expect(screen.queryByRole('link', { name: 'Mi cuenta' })).not.toBeInTheDocument();
  });
});
