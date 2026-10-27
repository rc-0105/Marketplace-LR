import { render, screen } from '@testing-library/react';
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
});
