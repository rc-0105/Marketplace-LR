import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import InicioSesionPage from './InicioSesionPage.jsx';

function renderizar(estado) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[{ pathname: '/inicio-sesion', state: estado }]}>
      <InicioSesionPage />
    </MemoryRouter>,
  );
}

describe('InicioSesionPage', () => {
  it('confirma la cuenta creada cuando viene del registro', () => {
    renderizar({ registrado: true });
    expect(screen.getByRole('status')).toHaveTextContent('Tu cuenta quedó creada. Ya puedes iniciar sesión.');
  });

  it('no muestra la confirmación si se entra directo', () => {
    renderizar(undefined);
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
