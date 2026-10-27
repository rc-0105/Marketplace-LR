import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { guardarSesion, limpiarSesion } from '../services/sesion.js';
import RutaProtegida from './RutaProtegida.jsx';

const FUTURO = new Date(Date.now() + 3600_000).toISOString();

function iniciar(rol, expiraEn = FUTURO) {
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true } });
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/administrador']}>
      <Routes>
        <Route path="/administrador" element={<RutaProtegida rol="ADMINISTRADOR"><p>Contenido admin</p></RutaProtegida>} />
        <Route path="/inicio-sesion" element={<p>Pantalla de ingreso</p>} />
        <Route path="/huesped" element={<p>Panel huesped</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RutaProtegida', () => {
  afterEach(() => limpiarSesion());

  it('sin sesión redirige al inicio de sesión', () => {
    renderizar();
    expect(screen.getByText('Pantalla de ingreso')).toBeInTheDocument();
  });

  it('una sesión vencida cuenta como ausente', () => {
    iniciar('ADMINISTRADOR', new Date(Date.now() - 1000).toISOString());
    renderizar();
    expect(screen.getByText('Pantalla de ingreso')).toBeInTheDocument();
  });

  it('con otro rol redirige al panel del propio rol', () => {
    iniciar('HUESPED');
    renderizar();
    expect(screen.getByText('Panel huesped')).toBeInTheDocument();
  });

  it('con el rol permitido muestra el contenido', () => {
    iniciar('ADMINISTRADOR');
    renderizar();
    expect(screen.getByText('Contenido admin')).toBeInTheDocument();
  });
});
