import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { guardarSesion, limpiarSesion } from '../../shared/services/sesion.js';
import PanelRolPage from './PanelRolPage.jsx';

function iniciar(rol) {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana Pérez', correo: 'a@b.co', rol, activa: true } });
}

describe('PanelRolPage', () => {
  afterEach(() => limpiarSesion());

  it.each([
    ['HUESPED', 'Huésped'],
    ['ANFITRION', 'Anfitrión'],
    ['ADMINISTRADOR', 'Administrador'],
  ])('saluda por nombre e indica el rol %s', (rol, nombre) => {
    iniciar(rol);
    render(<PanelRolPage />);

    expect(screen.getByRole('heading', { name: 'Hola, Ana Pérez' })).toBeInTheDocument();
    expect(screen.getByText(`Ingresaste como ${nombre}.`)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('—');
  });
});
