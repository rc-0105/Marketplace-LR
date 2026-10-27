import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { iniciarSesion } from '../services/identidadService.js';
import InicioSesionPage from './InicioSesionPage.jsx';

vi.mock('../services/identidadService.js', () => ({ iniciarSesion: vi.fn() }));

function ingresar(correo, contrasena) {
  fireEvent.change(screen.getByLabelText('Correo electrónico'), { target: { value: correo } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: contrasena } });
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
}

function renderizar(estado) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[{ pathname: '/inicio-sesion', state: estado }]}>
      <Routes>
        <Route path="/inicio-sesion" element={<InicioSesionPage />} />
        <Route path="/huesped" element={<p>Panel huesped</p>} />
        <Route path="/administrador" element={<p>Panel administrador</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('InicioSesionPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('confirma la cuenta creada cuando viene del registro', () => {
    renderizar({ registrado: true });
    expect(screen.getByRole('status')).toHaveTextContent('Tu cuenta quedó creada. Ya puedes iniciar sesión.');
    expect(screen.getByRole('status').className).toMatch(/exito/);
  });

  it('retira la confirmación del registro al intentar iniciar sesión', async () => {
    iniciarSesion.mockRejectedValue(new ErrorApi(401, 'Correo o contraseña incorrectos. Revísalos e intenta de nuevo.'));
    renderizar({ registrado: true });
    ingresar('a@b.co', 'mala');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText('Tu cuenta quedó creada. Ya puedes iniciar sesión.')).not.toBeInTheDocument();
  });

  it('no muestra la confirmación si se entra directo', () => {
    renderizar(undefined);
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('avisa que la sesión venció', () => {
    renderizar({ sesionExpirada: true });
    expect(screen.getByRole('status')).toHaveTextContent('Tu sesión venció. Inicia sesión de nuevo para continuar.');
    expect(screen.getByRole('status').className).not.toMatch(/exito/);
  });

  it('exige correo y contraseña sin llamar al servicio', () => {
    renderizar(undefined);
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

    expect(screen.getByText('Escribe tu correo.')).toBeInTheDocument();
    expect(screen.getByText('Escribe tu contraseña.')).toBeInTheDocument();
    expect(iniciarSesion).not.toHaveBeenCalled();
  });

  it.each([
    ['HUESPED', 'Panel huesped'],
    ['ADMINISTRADOR', 'Panel administrador'],
  ])('al ingresar como %s navega a su panel', async (rol, texto) => {
    iniciarSesion.mockResolvedValue({ id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true });
    renderizar(undefined);
    ingresar(' a@b.co ', 'abc');

    expect(await screen.findByText(texto)).toBeInTheDocument();
    expect(iniciarSesion).toHaveBeenCalledWith({ correo: 'a@b.co', contrasena: 'abc' });
  });

  it('muestra el error de la API junto al formulario y permite reintentar', async () => {
    iniciarSesion.mockRejectedValue(new ErrorApi(401, 'Correo o contraseña incorrectos. Revísalos e intenta de nuevo.'));
    renderizar(undefined);
    ingresar('a@b.co', 'mala');

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos. Revísalos e intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  });

  it.each([
    [400, 'Revisa los datos ingresados.'],
    [503, 'El servicio no está disponible por ahora. Intenta más tarde.'],
    [0, 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.'],
  ])('ante un error %s muestra su mensaje, no guarda sesión y permite reintentar', async (estado, mensaje) => {
    iniciarSesion.mockRejectedValue(new ErrorApi(estado, mensaje));
    renderizar(undefined);
    ingresar('a@b.co', 'abc');

    expect(await screen.findByRole('alert')).toHaveTextContent(mensaje);
    expect(sessionStorage.length).toBe(0);
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  });

  it('deshabilita el envío mientras espera y no repite la llamada', async () => {
    let rechazar;
    iniciarSesion.mockReturnValue(new Promise((_, rechazo) => { rechazar = rechazo; }));
    renderizar(undefined);
    ingresar('a@b.co', 'abc');

    const boton = screen.getByRole('button', { name: 'Iniciando sesión…' });
    expect(boton).toBeDisabled();
    fireEvent.click(boton);
    expect(iniciarSesion).toHaveBeenCalledTimes(1);

    rechazar(new ErrorApi(503, 'El servicio no está disponible por ahora. Intenta más tarde.'));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  });
});
