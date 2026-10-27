import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { guardarSesion, limpiarSesion } from '../shared/services/sesion.js';
import App from './App.jsx';

function iniciar(rol) {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true } });
}

function renderizarEn(ruta) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  afterEach(() => limpiarSesion());

  it('muestra el nombre del marketplace', () => {
    renderizarEn('/');
    expect(
      screen.getByRole('heading', { name: /marketplace de alojamientos/i }),
    ).toBeInTheDocument();
  });

  it('ofrece crear una cuenta desde el inicio', () => {
    renderizarEn('/');
    expect(screen.getByRole('link', { name: 'Crear una cuenta' })).toHaveAttribute('href', '/registro');
  });

  it('muestra el registro en su ruta', () => {
    renderizarEn('/registro');
    expect(screen.getByRole('heading', { name: 'Crea tu cuenta' })).toBeInTheDocument();
  });

  it('muestra el encabezado con la marca en todas las páginas', () => {
    renderizarEn('/registro');
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir al inicio' })).toBeInTheDocument();
  });

  it.each([
    ['HUESPED', '/huesped'],
    ['ANFITRION', '/anfitrion'],
    ['ADMINISTRADOR', '/administrador'],
  ])('muestra el panel de %s en su ruta', (rol, ruta) => {
    iniciar(rol);
    renderizarEn(ruta);
    expect(screen.getByText(/^Ingresaste como/)).toBeInTheDocument();
  });

  it('sin sesión un panel lleva al inicio de sesión', () => {
    renderizarEn('/huesped');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it('un rol ajeno vuelve a su propio panel', () => {
    iniciar('HUESPED');
    renderizarEn('/administrador');
    expect(screen.getByText('Ingresaste como Huésped.')).toBeInTheDocument();
  });

  it.each(['/inicio-sesion', '/registro'])('con sesión %s lleva al panel del rol', (ruta) => {
    iniciar('ANFITRION');
    renderizarEn(ruta);
    expect(screen.getByText('Ingresaste como Anfitrión.')).toBeInTheDocument();
  });

  it('sin sesión muestra el inicio de sesión normalmente', () => {
    renderizarEn('/inicio-sesion');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });
});
