import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import SoloInvitados from './SoloInvitados.jsx';

function iniciar(rol) {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true } });
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/registro']}>
      <Routes>
        <Route path="/registro" element={<SoloInvitados><p>Formulario</p></SoloInvitados>} />
        <Route path="/huesped" element={<p>Panel huesped</p>} />
        <Route path="/anfitrion" element={<p>Panel anfitrion</p>} />
        <Route path="/administrador" element={<p>Panel administrador</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('SoloInvitados', () => {
  afterEach(() => limpiarSesion());

  it('sin sesión muestra el contenido', () => {
    renderizar();
    expect(screen.getByText('Formulario')).toBeInTheDocument();
  });

  it.each([
    ['HUESPED', 'Panel huesped'],
    ['ANFITRION', 'Panel anfitrion'],
    ['ADMINISTRADOR', 'Panel administrador'],
  ])('con sesión %s redirige a su panel', (rol, texto) => {
    iniciar(rol);
    renderizar();
    expect(screen.getByText(texto)).toBeInTheDocument();
  });
});
