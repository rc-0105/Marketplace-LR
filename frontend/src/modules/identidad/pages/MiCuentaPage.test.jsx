import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { desactivarCuenta, eliminarCuenta } from '../services/identidadService.js';
import MiCuentaPage from './MiCuentaPage.jsx';

vi.mock('../services/identidadService.js', () => ({ desactivarCuenta: vi.fn(), eliminarCuenta: vi.fn() }));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/mi-cuenta']}>
      <Routes>
        <Route path="/mi-cuenta" element={<MiCuentaPage />} />
        <Route path="/inicio-sesion" element={<h1>Inicia sesión</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

function abrir(nombre) {
  fireEvent.click(screen.getByRole('button', { name: nombre }));
}

function confirmar(textoBoton, contrasena = 'Segura#2026') {
  fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: contrasena } });
  fireEvent.click(screen.getByRole('button', { name: textoBoton }));
}

const OPERACIONES = [
  ['desactivar', 'Desactivar mi cuenta', 'Sí, desactivar mi cuenta', desactivarCuenta],
  ['eliminar', 'Eliminar mi cuenta', 'Sí, eliminar mi cuenta', eliminarCuenta],
];

describe('MiCuentaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('ofrece desactivar y eliminar la cuenta sin pedir contraseña todavía', () => {
    renderizar();

    expect(screen.getByRole('heading', { name: 'Mi cuenta' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desactivar mi cuenta' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Eliminar mi cuenta' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Contraseña actual')).not.toBeInTheDocument();
  });

  it.each(OPERACIONES)('al elegir %s pide la contraseña sin llamar al servicio', (_op, abrirNombre, _c, servicio) => {
    renderizar();

    abrir(abrirNombre);

    expect(screen.getByLabelText('Contraseña actual')).toBeInTheDocument();
    expect(servicio).not.toHaveBeenCalled();
  });

  it('eliminar avisa que no se puede deshacer y desactivar no', () => {
    renderizar();

    abrir('Desactivar mi cuenta');
    expect(screen.queryByText(/no se puede deshacer/i)).not.toBeInTheDocument();

    abrir('Eliminar mi cuenta');
    expect(screen.getByText(/no se puede deshacer/i)).toBeInTheDocument();
  });

  it.each(OPERACIONES)('al confirmar %s llama al servicio y lleva al inicio de sesión', async (_op, abrirNombre, confirmarNombre, servicio) => {
    servicio.mockResolvedValue(undefined);
    renderizar();

    abrir(abrirNombre);
    confirmar(confirmarNombre);

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(servicio).toHaveBeenCalledWith('Segura#2026');
  });

  it('mientras la petición está en curso no deja confirmar de nuevo', async () => {
    let terminar;
    desactivarCuenta.mockReturnValue(new Promise((resolver) => { terminar = resolver; }));
    renderizar();

    abrir('Desactivar mi cuenta');
    confirmar('Sí, desactivar mi cuenta');

    expect(screen.getByRole('button', { name: 'Sí, desactivar mi cuenta' })).toBeDisabled();
    terminar();
    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it.each(OPERACIONES)('con contraseña incorrecta en %s avisa y permite reintentar', async (_op, abrirNombre, confirmarNombre, servicio) => {
    servicio.mockRejectedValueOnce(new ErrorApi(403, 'La contraseña no es correcta.', {}, { codigo: 'CONFIRMACION_INVALIDA' }));
    servicio.mockResolvedValueOnce(undefined);
    renderizar();

    abrir(abrirNombre);
    confirmar(confirmarNombre, 'mala');

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña no es correcta');
    expect(screen.queryByRole('heading', { name: 'Inicia sesión' })).not.toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('button', { name: confirmarNombre })).toBeEnabled());
    confirmar(confirmarNombre, 'Segura#2026');
    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(servicio).toHaveBeenCalledTimes(2);
  });

  it('con reservas futuras muestra el mensaje de la API y se queda en la página', async () => {
    const detalle = 'Tienes reservas confirmadas pendientes (2). Cancélalas o espera a que terminen antes de continuar.';
    desactivarCuenta.mockRejectedValue(new ErrorApi(409, detalle, {}, { codigo: 'RESERVAS_FUTURAS_ACTIVAS' }));
    renderizar();

    abrir('Desactivar mi cuenta');
    confirmar('Sí, desactivar mi cuenta');

    expect(await screen.findByRole('alert')).toHaveTextContent(detalle);
    expect(screen.queryByRole('heading', { name: 'Inicia sesión' })).not.toBeInTheDocument();
  });

  it.each([
    ['403 con otro código', new ErrorApi(403, 'La contraseña no es correcta.', {}, { codigo: 'OTRO' })],
    ['409 con código desconocido', new ErrorApi(409, 'Tienes reservas confirmadas pendientes (9).', {}, { codigo: 'DESCONOCIDO' })],
    ['400', new ErrorApi(400, 'Solicitud inválida.')],
    ['500', new ErrorApi(500, 'Falla interna.')],
    ['503', new ErrorApi(503, 'No disponible.')],
  ])('ante un %s muestra un mensaje genérico sin usar el detalle', async (_caso, error) => {
    eliminarCuenta.mockRejectedValue(error);
    renderizar();

    abrir('Eliminar mi cuenta');
    confirmar('Sí, eliminar mi cuenta');

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent('No pudimos completar la operación. Intenta de nuevo.');
    expect(aviso).not.toHaveTextContent(error.message);
  });

  it('cancelar cierra la confirmación, descarta la contraseña y no llama al servicio', () => {
    renderizar();

    abrir('Desactivar mi cuenta');
    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'Segura#2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByLabelText('Contraseña actual')).not.toBeInTheDocument();
    abrir('Eliminar mi cuenta');
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('');
    expect(desactivarCuenta).not.toHaveBeenCalled();
    expect(eliminarCuenta).not.toHaveBeenCalled();
  });
});
