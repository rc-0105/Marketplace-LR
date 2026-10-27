import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from './App.jsx';

function renderizarEn(ruta) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
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
});
