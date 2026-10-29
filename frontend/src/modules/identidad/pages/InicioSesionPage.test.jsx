import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { iniciarSesion, reactivarCuenta } from '../services/identidadService.js';
import InicioSesionPage from './InicioSesionPage.jsx';

vi.mock('../services/identidadService.js', () => ({ iniciarSesion: vi.fn(), reactivarCuenta: vi.fn() }));

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
        <Route path="/anfitrion" element={<p>Panel anfitrion</p>} />
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
    expect(screen.getByRole('status').tagName).toBe('OUTPUT');
  });

  it('retira la confirmación del registro al intentar iniciar sesión', async () => {
    iniciarSesion.mockRejectedValue(new ErrorApi(401, 'Correo o contraseña incorrectos. Revísalos e intenta de nuevo.'));
    renderizar({ registrado: true });
    ingresar('a@b.co', 'mala');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByText('Tu cuenta quedó creada. Ya puedes iniciar sesión.')).not.toBeInTheDocument();
  });

  it('confirma la contraseña restablecida cuando viene de la recuperación', () => {
    renderizar({ contrasenaRestablecida: true });
    expect(screen.getByRole('status')).toHaveTextContent('Tu contraseña quedó actualizada. Inicia sesión con la nueva.');
  });

  it('ofrece recuperar la contraseña', () => {
    renderizar(undefined);
    expect(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' })).toHaveAttribute('href', '/recuperar-contrasena');
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

  describe('con la cuenta restringida o desactivada', () => {
    const restringida = (motivo) => new ErrorApi(403, 'Tu cuenta está restringida.', {}, { codigo: 'CUENTA_RESTRINGIDA', motivo });
    const desactivada = () => new ErrorApi(403, 'Tu cuenta está desactivada.', {}, { codigo: 'CUENTA_DESACTIVADA' });
    const botonReactivar = () => screen.getByRole('button', { name: /reactivar mi cuenta/i });

    it('muestra el aviso con el motivo, sin botón de reactivar, sin sesión ni redirección', async () => {
      iniciarSesion.mockRejectedValue(restringida('Pagos en disputa'));
      renderizar(undefined);
      ingresar('a@b.co', 'abc');

      const aviso = await screen.findByRole('alert');
      expect(aviso).toHaveTextContent('Tu cuenta está restringida.');
      expect(aviso).toHaveTextContent('Pagos en disputa');
      expect(screen.queryByRole('button', { name: /reactivar mi cuenta/i })).not.toBeInTheDocument();
      expect(sessionStorage.length).toBe(0);
      expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    });

    it('muestra el motivo como texto y no interpreta el marcado', async () => {
      iniciarSesion.mockRejectedValue(restringida('<b>x</b>'));
      renderizar(undefined);
      ingresar('a@b.co', 'abc');

      const aviso = await screen.findByRole('alert');
      expect(aviso).toHaveTextContent('<b>x</b>');
      expect(aviso.querySelector('b')).toBeNull();
    });

    it('ofrece reactivar la cuenta desactivada, sin sesión ni redirección', async () => {
      iniciarSesion.mockRejectedValue(desactivada());
      renderizar(undefined);
      ingresar('a@b.co', 'abc');

      expect(await screen.findByRole('alert')).toHaveTextContent('Tu cuenta está desactivada.');
      expect(botonReactivar()).toBeEnabled();
      expect(botonReactivar()).toHaveAttribute('type', 'button');
      expect(sessionStorage.length).toBe(0);
      expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    });

    it.each([
      ['HUESPED', 'Panel huesped'],
      ['ANFITRION', 'Panel anfitrion'],
      ['ADMINISTRADOR', 'Panel administrador'],
    ])('al reactivar como %s usa lo escrito en el formulario y navega a su panel', async (rol, texto) => {
      iniciarSesion.mockRejectedValue(desactivada());
      reactivarCuenta.mockResolvedValue({ id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true });
      renderizar(undefined);
      ingresar(' a@b.co ', 'abc');
      await screen.findByRole('alert');
      fireEvent.click(botonReactivar());

      expect(await screen.findByText(texto)).toBeInTheDocument();
      expect(reactivarCuenta).toHaveBeenCalledWith({ correo: 'a@b.co', contrasena: 'abc' });
    });

    it.each([
      [new ErrorApi(401, 'Correo o contraseña incorrectos. Revísalos e intenta de nuevo.'), 'Correo o contraseña incorrectos.'],
      [restringida('Pagos en disputa'), 'Pagos en disputa'],
    ])('si reactivar falla muestra el error en el mismo aviso y no guarda sesión', async (error, texto) => {
      iniciarSesion.mockRejectedValue(desactivada());
      reactivarCuenta.mockRejectedValue(error);
      renderizar(undefined);
      ingresar('a@b.co', 'abc');
      await screen.findByRole('alert');
      fireEvent.click(botonReactivar());

      await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(texto));
      expect(screen.getAllByRole('alert')).toHaveLength(1);
      expect(screen.queryByRole('button', { name: /reactivar mi cuenta/i })).not.toBeInTheDocument();
      expect(sessionStorage.length).toBe(0);
    });

    it('deshabilita el botón mientras reactiva y no repite la llamada', async () => {
      iniciarSesion.mockRejectedValue(desactivada());
      reactivarCuenta.mockReturnValue(new Promise(() => {}));
      renderizar(undefined);
      ingresar('a@b.co', 'abc');
      await screen.findByRole('alert');
      fireEvent.click(botonReactivar());

      await waitFor(() => expect(botonReactivar()).toBeDisabled());
      fireEvent.click(botonReactivar());
      expect(reactivarCuenta).toHaveBeenCalledTimes(1);
    });

    it('retira el aviso y el botón en el siguiente intento', async () => {
      iniciarSesion.mockRejectedValueOnce(desactivada()).mockReturnValue(new Promise(() => {}));
      renderizar(undefined);
      ingresar('a@b.co', 'abc');
      await screen.findByRole('alert');
      fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));

      await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
      expect(screen.queryByRole('button', { name: /reactivar mi cuenta/i })).not.toBeInTheDocument();
    });
  });
});
