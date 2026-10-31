import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import CambioContrasena from './CambioContrasena.jsx';

function renderizar(alCambiar = vi.fn()) {
  render(<CambioContrasena alCambiar={alCambiar} />);
  return alCambiar;
}

function enviar(actual, nueva) {
  fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: actual } });
  fireEvent.change(screen.getByLabelText('Contraseña nueva'), { target: { value: nueva } });
  fireEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));
}

describe('CambioContrasena', () => {
  it('con la actual y una nueva válida cambia la contraseña, avisa y limpia los campos', async () => {
    const alCambiar = renderizar(vi.fn().mockResolvedValue());

    enviar('Segura#2026', 'Nueva#Clave7');

    expect(await screen.findByText('Tu contraseña cambió.')).toBeInTheDocument();
    expect(alCambiar).toHaveBeenCalledWith({ contrasenaActual: 'Segura#2026', contrasenaNueva: 'Nueva#Clave7' });
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('');
    expect(screen.getByLabelText('Contraseña nueva')).toHaveValue('');
  });

  it('si la contraseña actual es incorrecta lo dice en ese campo y no da el cambio por hecho', async () => {
    renderizar(vi.fn().mockRejectedValue(
      new ErrorApi(403, 'La contraseña no es correcta.', {}, { codigo: 'CONFIRMACION_INVALIDA' })));

    enviar('Otra#Clave1', 'Nueva#Clave7');

    expect(await screen.findByText('La contraseña actual no es correcta.')).toBeInTheDocument();
    expect(screen.queryByText('Tu contraseña cambió.')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('Otra#Clave1');
  });

  it('sin la actual o con una nueva que no cumple la regla no llama a la API', () => {
    const alCambiar = renderizar();

    enviar('', 'corta');

    expect(screen.getByText('Escribe tu contraseña actual.')).toBeInTheDocument();
    expect(screen.getByText(/al menos 8 caracteres, una mayúscula/)).toBeInTheDocument();
    expect(alCambiar).not.toHaveBeenCalled();
  });

  it('ante otro error muestra el mensaje de la API', async () => {
    renderizar(vi.fn().mockRejectedValue(new ErrorApi(0, 'No pudimos conectar con el servidor.')));

    enviar('Segura#2026', 'Nueva#Clave7');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos conectar con el servidor.');
  });
});
