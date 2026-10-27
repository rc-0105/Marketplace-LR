import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import Campo from './Campo.jsx';

describe('Campo', () => {
  it('asocia la etiqueta y la ayuda al control', () => {
    render(<Campo id="correo" etiqueta="Correo" ayuda="Lo usarás para entrar." value="" onChange={() => {}} />);

    const control = screen.getByLabelText('Correo');
    expect(control).toHaveAccessibleDescription('Lo usarás para entrar.');
    expect(control).not.toBeInvalid();
  });

  it('cuando hay error lo anuncia en lugar de la ayuda y marca el control inválido', () => {
    render(
      <Campo id="correo" etiqueta="Correo" ayuda="Lo usarás para entrar." error="El correo ya está en uso."
        value="" onChange={() => {}} />,
    );

    const control = screen.getByLabelText('Correo');
    expect(control).toHaveAccessibleDescription('El correo ya está en uso.');
    expect(control).toBeInvalid();
  });

  it('muestra y oculta la contraseña con el botón', () => {
    render(<Campo id="contrasena" etiqueta="Contraseña" type="password" value="Secreta#1" onChange={() => {}} />);

    const control = screen.getByLabelText('Contraseña');
    expect(control).toHaveAttribute('type', 'password');

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }));
    expect(control).toHaveAttribute('type', 'text');

    fireEvent.click(screen.getByRole('button', { name: 'Ocultar contraseña' }));
    expect(control).toHaveAttribute('type', 'password');
  });

  it('no ofrece el botón en campos que no son contraseña', () => {
    render(<Campo id="correo" etiqueta="Correo" type="email" value="" onChange={() => {}} />);

    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
