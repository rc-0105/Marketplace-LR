import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { registrarUsuario } from '../services/identidadService.js';
import InicioSesionPage from './InicioSesionPage.jsx';
import RegistroPage from './RegistroPage.jsx';

vi.mock('../services/identidadService.js', () => ({ registrarUsuario: vi.fn() }));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/registro']}>
      <Routes>
        <Route path="/registro" element={<RegistroPage />} />
        <Route path="/inicio-sesion" element={<InicioSesionPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function llenar({ nombre = 'Ana Gómez', correo = 'ana@correo.com', contrasena = 'Segura#2026' } = {}) {
  fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: nombre } });
  fireEvent.change(screen.getByLabelText('Correo electrónico'), { target: { value: correo } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: contrasena } });
}

function enviar() {
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }));
}

describe('RegistroPage', () => {
  beforeEach(() => {
    vi.mocked(registrarUsuario).mockReset();
  });

  it('muestra el formulario con huésped elegido por defecto', () => {
    renderizar();

    expect(screen.getByRole('heading', { name: 'Crea tu cuenta' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /huésped/i })).toBeChecked();
    expect(screen.getByRole('radio', { name: /anfitrión/i })).not.toBeChecked();
  });

  it('indica el error de un campo al salir de él, sin esperar al envío ni marcar los demás', () => {
    renderizar();
    const correo = screen.getByLabelText('Correo electrónico');
    fireEvent.change(correo, { target: { value: 'ana-sin-arroba' } });
    fireEvent.blur(correo);

    expect(correo).toHaveAccessibleDescription('El correo no tiene un formato válido.');
    expect(screen.getByLabelText('Nombre completo')).not.toBeInvalid();
    expect(registrarUsuario).not.toHaveBeenCalled();
  });

  it('quita el error del campo en cuanto el usuario lo corrige', () => {
    renderizar();
    const correo = screen.getByLabelText('Correo electrónico');
    fireEvent.change(correo, { target: { value: 'ana-sin-arroba' } });
    fireEvent.blur(correo);

    fireEvent.change(correo, { target: { value: 'ana@correo.com' } });

    expect(correo).not.toBeInvalid();
    expect(correo).toHaveAccessibleDescription('');
  });

  it('marca los requisitos de la contraseña a medida que se escribe', () => {
    renderizar();
    const requisitos = screen.getByRole('list', { name: 'Requisitos de la contraseña' });
    expect(requisitos).not.toHaveTextContent('Cumplido');

    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'Segura#2026' } });

    expect(requisitos).not.toHaveTextContent('Pendiente');
  });

  it.each([
    ['es corta', 'Cort#1'],
    ['no tiene mayúscula', 'sinmayuscula#1'],
    ['no tiene número', 'SinNumero#Ab'],
    ['no tiene carácter especial', 'SinEspecial123'],
  ])('indica que la contraseña no cumple la regla cuando %s, sin enviar el formulario', (_caso, contrasena) => {
    renderizar();
    llenar({ contrasena });
    enviar();

    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription(
      /debe tener al menos 8 caracteres, una mayúscula, un número y un carácter especial/i,
    );
    expect(registrarUsuario).not.toHaveBeenCalled();
  });

  it('indica los campos obligatorios vacíos sin enviar el formulario', () => {
    renderizar();
    enviar();

    expect(screen.getByLabelText('Nombre completo')).toHaveAccessibleDescription('Escribe tu nombre.');
    expect(screen.getByLabelText('Correo electrónico')).toHaveAccessibleDescription('Escribe tu correo.');
    expect(registrarUsuario).not.toHaveBeenCalled();
  });

  it('indica el correo con formato inválido sin enviar el formulario', () => {
    renderizar();
    llenar({ correo: 'ana-sin-arroba' });
    enviar();

    expect(screen.getByLabelText('Correo electrónico')).toHaveAccessibleDescription(
      'El correo no tiene un formato válido.',
    );
    expect(registrarUsuario).not.toHaveBeenCalled();
  });

  it('con datos válidos crea la cuenta de huésped y lleva al inicio de sesión', async () => {
    vi.mocked(registrarUsuario).mockResolvedValue({ id: 1, rol: 'HUESPED' });
    renderizar();
    llenar();
    enviar();

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Tu cuenta quedó creada');
    expect(registrarUsuario).toHaveBeenCalledWith({
      nombre: 'Ana Gómez',
      correo: 'ana@correo.com',
      contrasena: 'Segura#2026',
      rol: 'HUESPED',
    });
  });

  it('envía el rol anfitrión cuando el usuario lo elige', async () => {
    vi.mocked(registrarUsuario).mockResolvedValue({ id: 2, rol: 'ANFITRION' });
    renderizar();
    llenar();
    fireEvent.click(screen.getByRole('radio', { name: /anfitrión/i }));
    enviar();

    await screen.findByRole('heading', { name: 'Inicia sesión' });
    expect(registrarUsuario).toHaveBeenCalledWith(expect.objectContaining({ rol: 'ANFITRION' }));
  });

  it('si el correo ya está en uso lo informa en el campo y no sale del registro', async () => {
    vi.mocked(registrarUsuario).mockRejectedValue(new ErrorApi(409, 'El correo ya está en uso.'));
    renderizar();
    llenar();
    enviar();

    await waitFor(() =>
      expect(screen.getByLabelText('Correo electrónico')).toHaveAccessibleDescription('El correo ya está en uso.'),
    );
    expect(screen.getByRole('heading', { name: 'Crea tu cuenta' })).toBeInTheDocument();
  });

  it('muestra en cada campo los errores de validación que devuelve el servidor', async () => {
    vi.mocked(registrarUsuario).mockRejectedValue(
      new ErrorApi(400, 'La solicitud tiene datos inválidos.', { nombre: 'El nombre admite máximo 100 caracteres.' }),
    );
    renderizar();
    llenar();
    enviar();

    await waitFor(() =>
      expect(screen.getByLabelText('Nombre completo')).toHaveAccessibleDescription(
        'El nombre admite máximo 100 caracteres.',
      ),
    );
  });

  it('muestra un aviso general cuando el servidor no responde', async () => {
    vi.mocked(registrarUsuario).mockRejectedValue(new ErrorApi(0, 'No pudimos conectar con el servidor.'));
    renderizar();
    llenar();
    enviar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos conectar con el servidor.');
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeEnabled();
  });
});
