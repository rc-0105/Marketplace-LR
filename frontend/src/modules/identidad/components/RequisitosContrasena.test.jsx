import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import RequisitosContrasena from './RequisitosContrasena.jsx';

function requisitos() {
  return within(screen.getByRole('list', { name: 'Requisitos de la contraseña' })).getAllByRole('listitem');
}

describe('RequisitosContrasena', () => {
  it('muestra los tres requisitos pendientes cuando la contraseña está vacía', () => {
    render(<RequisitosContrasena contrasena="" />);

    const [longitud, mayuscula, especial] = requisitos();
    expect(longitud).toHaveTextContent('Pendiente: Al menos 8 caracteres');
    expect(mayuscula).toHaveTextContent('Pendiente: Una mayúscula');
    expect(especial).toHaveTextContent('Pendiente: Un carácter especial');
  });

  it('marca como cumplido cada requisito que la contraseña ya satisface', () => {
    render(<RequisitosContrasena contrasena="Corta#" />);

    const [longitud, mayuscula, especial] = requisitos();
    expect(longitud).toHaveTextContent('Pendiente: Al menos 8 caracteres');
    expect(mayuscula).toHaveTextContent('Cumplido: Una mayúscula');
    expect(especial).toHaveTextContent('Cumplido: Un carácter especial');
  });
});
