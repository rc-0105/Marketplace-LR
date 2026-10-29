import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ConfirmacionCuenta from './ConfirmacionCuenta.jsx';

function renderizar(props = {}) {
  const alConfirmar = vi.fn();
  const alCancelar = vi.fn();
  const resultado = render(
    <ConfirmacionCuenta
      titulo="Desactivar cuenta"
      advertencia="Podrás reactivarla desde el inicio de sesión."
      textoConfirmar="Desactivar mi cuenta"
      enviando={false}
      alConfirmar={alConfirmar}
      alCancelar={alCancelar}
      {...props}
    />,
  );
  return { alConfirmar, alCancelar, ...resultado };
}

describe('ConfirmacionCuenta', () => {
  it('muestra el título, la advertencia y el campo de contraseña con su etiqueta', () => {
    renderizar();

    expect(screen.getByRole('form', { name: 'Desactivar cuenta' })).toHaveAccessibleDescription(
      'Podrás reactivarla desde el inicio de sesión.',
    );
    expect(screen.getByLabelText('Contraseña actual')).toHaveAttribute('type', 'password');
  });

  it('con la contraseña vacía muestra el error del campo y no confirma', () => {
    const { alConfirmar } = renderizar();

    fireEvent.click(screen.getByRole('button', { name: 'Desactivar mi cuenta' }));

    expect(screen.getByLabelText('Contraseña actual')).toBeInvalid();
    expect(screen.getByText('Escribe tu contraseña para confirmar.')).toBeInTheDocument();
    expect(alConfirmar).not.toHaveBeenCalled();
  });

  it('confirma con la contraseña escrita', () => {
    const { alConfirmar } = renderizar();

    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'Segura#2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Desactivar mi cuenta' }));

    expect(alConfirmar).toHaveBeenCalledWith('Segura#2026');
  });

  it('cancelar avisa sin confirmar', () => {
    const { alConfirmar, alCancelar } = renderizar();

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(alCancelar).toHaveBeenCalledTimes(1);
    expect(alConfirmar).not.toHaveBeenCalled();
  });

  it('muestra el error de la API en un aviso', () => {
    renderizar({ error: 'La contraseña no es correcta' });

    expect(screen.getByRole('alert')).toHaveTextContent('La contraseña no es correcta');
  });

  it('sin error no hay aviso', () => {
    renderizar();

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('mientras envía deshabilita confirmar y cancelar', () => {
    renderizar({ enviando: true });

    expect(screen.getByRole('button', { name: 'Desactivar mi cuenta' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
  });

  it('los textos propios no usan rayas largas', () => {
    const { container } = renderizar();

    expect(container.textContent).not.toMatch(/[—–]/);
  });
});
