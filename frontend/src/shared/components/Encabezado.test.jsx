import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import Encabezado from './Encabezado.jsx';

function iniciar() {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true } });
}

function renderizar(alCerrarSesion) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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

  it('con sesión muestra el botón de cerrar sesión', () => {
    iniciar();
    renderizar(vi.fn());

    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
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
});
