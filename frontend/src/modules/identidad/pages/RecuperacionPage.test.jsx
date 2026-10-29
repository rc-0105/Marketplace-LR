import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { confirmarRecuperacion, solicitarRecuperacion } from '../services/identidadService.js';
import RecuperacionPage from './RecuperacionPage.jsx';

vi.mock('../services/identidadService.js', () => ({
  confirmarRecuperacion: vi.fn(),
  solicitarRecuperacion: vi.fn(),
}));

const MENSAJE_SOLICITUD = 'Si el correo está registrado, te enviamos un código para restablecer la contraseña.';

function InicioSesionFalso() {
  const { state } = useLocation();
  return <p>{state?.contrasenaRestablecida ? 'Login con contraseña restablecida' : 'Login'}</p>;
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/recuperar-contrasena']}>
      <Routes>
        <Route path="/recuperar-contrasena" element={<RecuperacionPage />} />
        <Route path="/inicio-sesion" element={<InicioSesionFalso />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function pedirCodigo(correo = 'ana@correo.com') {
  fireEvent.change(screen.getByLabelText('Correo electrónico'), { target: { value: correo } });
  fireEvent.click(screen.getByRole('button', { name: 'Enviar código' }));
  await screen.findByRole('status');
}

function confirmar(codigo, contrasenaNueva) {
  fireEvent.change(screen.getByLabelText('Código de recuperación'), { target: { value: codigo } });
  fireEvent.change(screen.getByLabelText('Contraseña nueva'), { target: { value: contrasenaNueva } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
}

describe('RecuperacionPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    solicitarRecuperacion.mockResolvedValue(MENSAJE_SOLICITUD);
  });

  it('exige un correo con formato válido sin llamar al servicio', () => {
    renderizar();
    fireEvent.change(screen.getByLabelText('Correo electrónico'), { target: { value: 'no-es-correo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar código' }));

    expect(screen.getByText('El correo no tiene un formato válido.')).toBeInTheDocument();
    expect(solicitarRecuperacion).not.toHaveBeenCalled();
  });

  it('muestra el mensaje de la API y pasa a pedir el código y la contraseña nueva', async () => {
    renderizar();
    await pedirCodigo(' ana@correo.com ');

    expect(solicitarRecuperacion).toHaveBeenCalledWith('ana@correo.com');
    expect(screen.getByRole('status')).toHaveTextContent(MENSAJE_SOLICITUD);
    expect(screen.getByLabelText('Código de recuperación')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña nueva')).toBeInTheDocument();
  });

  it('valida el código y la contraseña nueva antes de enviarlos', async () => {
    renderizar();
    await pedirCodigo();
    confirmar('12', 'debil');

    expect(screen.getByText('Escribe el código de 6 dígitos.')).toBeInTheDocument();
    expect(screen.getByText(/La contraseña debe tener al menos 8 caracteres/)).toBeInTheDocument();
    expect(confirmarRecuperacion).not.toHaveBeenCalled();
  });

  it('con un código válido lleva al inicio de sesión avisando que la contraseña cambió', async () => {
    confirmarRecuperacion.mockResolvedValue(undefined);
    renderizar();
    await pedirCodigo();
    confirmar('123456', 'Nueva#2027');

    expect(await screen.findByText('Login con contraseña restablecida')).toBeInTheDocument();
    expect(confirmarRecuperacion).toHaveBeenCalledWith({
      correo: 'ana@correo.com', codigo: '123456', contrasenaNueva: 'Nueva#2027',
    });
  });

  it('muestra el rechazo de la API cuando el código venció o ya se usó', async () => {
    confirmarRecuperacion.mockRejectedValue(new ErrorApi(400, 'El código no es válido o ya venció. Solicita uno nuevo.'));
    renderizar();
    await pedirCodigo();
    confirmar('123456', 'Nueva#2027');

    expect(await screen.findByRole('alert')).toHaveTextContent('El código no es válido o ya venció. Solicita uno nuevo.');
    expect(screen.getByRole('button', { name: 'Guardar contraseña' })).toBeEnabled();
  });

  it('muestra el error de conexión al pedir el código', async () => {
    solicitarRecuperacion.mockRejectedValue(new ErrorApi(0, 'No pudimos conectar con el servidor.'));
    renderizar();
    fireEvent.change(screen.getByLabelText('Correo electrónico'), { target: { value: 'ana@correo.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar código' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos conectar con el servidor.');
    expect(screen.getByRole('button', { name: 'Enviar código' })).toBeEnabled();
  });
});
