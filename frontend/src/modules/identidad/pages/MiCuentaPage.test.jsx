import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import {
  actualizarPerfil, cambiarContrasena, desactivarCuenta, eliminarCuenta, obtenerPerfil,
} from '../services/identidadService.js';
import MiCuentaPage from './MiCuentaPage.jsx';

vi.mock('../services/identidadService.js', () => ({
  actualizarPerfil: vi.fn(), cambiarContrasena: vi.fn(), desactivarCuenta: vi.fn(), eliminarCuenta: vi.fn(),
  obtenerPerfil: vi.fn(),
}));

const PERFIL = {
  id: 7, nombre: 'Ana Gómez', correo: 'ana@correo.com', telefono: '', fotoUrl: '', rol: 'HUESPED',
};
const SECCIONES_BAJA = ['Desactivar mi cuenta', 'Eliminar mi cuenta'];

// La confirmación de la baja vive en la sección de cada operación.
function campoConfirmacion() {
  return SECCIONES_BAJA
    .map((nombre) => within(screen.getByRole('region', { name: nombre })).queryByLabelText('Contraseña actual'))
    .find(Boolean) ?? null;
}

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

function irAPestana(nombre) {
  fireEvent.click(screen.getByRole('tab', { name: nombre }));
}

function renderizarBaja() {
  renderizar();
  irAPestana('Desactivar o eliminar');
}

function abrir(nombre) {
  fireEvent.click(screen.getByRole('button', { name: nombre }));
}

function confirmar(textoBoton, contrasena = 'Segura#2026') {
  fireEvent.change(campoConfirmacion(), { target: { value: contrasena } });
  fireEvent.click(screen.getByRole('button', { name: textoBoton }));
}

const OPERACIONES = [
  ['desactivar', 'Desactivar mi cuenta', 'Sí, desactivar mi cuenta', desactivarCuenta],
  ['eliminar', 'Eliminar mi cuenta', 'Sí, eliminar mi cuenta', eliminarCuenta],
];

describe('MiCuentaPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // Por defecto la carga queda pendiente: las pruebas de la baja no dependen del perfil.
    vi.mocked(obtenerPerfil).mockReturnValue(new Promise(() => {}));
  });

  it('mientras carga el perfil avisa que está cargando', () => {
    renderizar();

    expect(screen.getByText('Cargando tus datos…')).toBeInTheDocument();
  });

  it('carga los datos del perfil con el correo como solo lectura', async () => {
    vi.mocked(obtenerPerfil).mockResolvedValue(PERFIL);
    renderizar();

    expect(await screen.findByLabelText('Nombre')).toHaveValue('Ana Gómez');
    expect(screen.getByText('ana@correo.com')).toBeInTheDocument();
    expect(screen.getByText('El correo no se puede cambiar.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /correo/i })).not.toBeInTheDocument();
  });

  it('muestra la foto de perfil cuando la cuenta tiene una', async () => {
    vi.mocked(obtenerPerfil).mockResolvedValue({ ...PERFIL, fotoUrl: 'https://fotos.ejemplo.com/ana.jpg' });
    renderizar();

    expect(await screen.findByRole('img', { name: 'Tu foto de perfil' }))
      .toHaveAttribute('src', 'https://fotos.ejemplo.com/ana.jpg');
  });

  it('si no puede cargar el perfil muestra el mensaje de la API', async () => {
    vi.mocked(obtenerPerfil).mockRejectedValue(new ErrorApi(0, 'No pudimos conectar con el servidor.'));
    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos conectar con el servidor.');
    expect(screen.queryByLabelText('Nombre')).not.toBeInTheDocument();
  });

  it('al guardar los datos los envía a la API y muestra la foto nueva', async () => {
    const foto = 'https://fotos.ejemplo.com/nueva.jpg';
    vi.mocked(obtenerPerfil).mockResolvedValue(PERFIL);
    vi.mocked(actualizarPerfil).mockResolvedValue({ ...PERFIL, nombre: 'Ana María', fotoUrl: foto });
    renderizar();

    fireEvent.change(await screen.findByLabelText('Nombre'), { target: { value: 'Ana María' } });
    fireEvent.change(screen.getByLabelText('Foto de perfil (dirección web)'), { target: { value: foto } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Tus datos quedaron guardados.')).toBeInTheDocument();
    expect(actualizarPerfil).toHaveBeenCalledWith({ nombre: 'Ana María', telefono: '', fotoUrl: foto });
    expect(screen.getByRole('img', { name: 'Tu foto de perfil' })).toHaveAttribute('src', foto);
  });

  it('el cambio de contraseña usa el servicio de la API', async () => {
    vi.mocked(cambiarContrasena).mockResolvedValue();
    renderizar();
    irAPestana('Contraseña');

    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'Segura#2026' } });
    fireEvent.change(screen.getByLabelText('Contraseña nueva'), { target: { value: 'Nueva#Clave7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));

    expect(await screen.findByText('Tu contraseña cambió.')).toBeInTheDocument();
    expect(cambiarContrasena).toHaveBeenCalledWith({ contrasenaActual: 'Segura#2026', contrasenaNueva: 'Nueva#Clave7' });
  });

  it('muestra una sección a la vez: empieza en Mis datos y cada pestaña cambia el panel', async () => {
    vi.mocked(obtenerPerfil).mockResolvedValue(PERFIL);
    renderizar();

    expect(screen.getByRole('tab', { name: 'Mis datos' })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByLabelText('Nombre')).toBeInTheDocument();
    expect(screen.queryByLabelText('Contraseña nueva')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Desactivar mi cuenta' })).not.toBeInTheDocument();

    irAPestana('Contraseña');
    expect(screen.getByRole('tabpanel', { name: 'Contraseña' })).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña nueva')).toBeInTheDocument();
    expect(screen.queryByLabelText('Nombre')).not.toBeInTheDocument();

    irAPestana('Desactivar o eliminar');
    expect(screen.getByRole('button', { name: 'Desactivar mi cuenta' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Contraseña nueva')).not.toBeInTheDocument();
  });

  it('las flechas del teclado recorren las pestañas en círculo y mueven el foco', () => {
    renderizar();
    const datos = screen.getByRole('tab', { name: 'Mis datos' });

    fireEvent.keyDown(datos, { key: 'ArrowLeft' });

    const baja = screen.getByRole('tab', { name: 'Desactivar o eliminar' });
    expect(baja).toHaveAttribute('aria-selected', 'true');
    expect(baja).toHaveFocus();
    fireEvent.keyDown(baja, { key: 'ArrowRight' });
    expect(datos).toHaveAttribute('aria-selected', 'true');
    expect(datos).toHaveFocus();
  });

  it('al cambiar de pestaña se cierra la confirmación de baja abierta', () => {
    renderizarBaja();
    abrir('Desactivar mi cuenta');
    expect(campoConfirmacion()).toBeInTheDocument();

    irAPestana('Mis datos');
    irAPestana('Desactivar o eliminar');

    expect(campoConfirmacion()).not.toBeInTheDocument();
  });

  it('ofrece desactivar y eliminar la cuenta sin pedir contraseña todavía', () => {
    renderizarBaja();

    expect(screen.getByRole('heading', { name: 'Mi cuenta' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desactivar mi cuenta' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Eliminar mi cuenta' })).toBeInTheDocument();
    expect(campoConfirmacion()).not.toBeInTheDocument();
  });

  it.each(OPERACIONES)('al elegir %s pide la contraseña sin llamar al servicio', (_op, abrirNombre, _c, servicio) => {
    renderizarBaja();

    abrir(abrirNombre);

    expect(campoConfirmacion()).toBeInTheDocument();
    expect(servicio).not.toHaveBeenCalled();
  });

  it('eliminar avisa que no se puede deshacer y desactivar no', () => {
    renderizarBaja();

    abrir('Desactivar mi cuenta');
    expect(screen.queryByText(/no se puede deshacer/i)).not.toBeInTheDocument();

    abrir('Eliminar mi cuenta');
    expect(screen.getByText(/no se puede deshacer/i)).toBeInTheDocument();
  });

  it.each(OPERACIONES)('al confirmar %s llama al servicio y lleva al inicio de sesión', async (_op, abrirNombre, confirmarNombre, servicio) => {
    servicio.mockResolvedValue(undefined);
    renderizarBaja();

    abrir(abrirNombre);
    confirmar(confirmarNombre);

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(servicio).toHaveBeenCalledWith('Segura#2026');
  });

  it('mientras la petición está en curso no deja confirmar de nuevo', async () => {
    let terminar;
    desactivarCuenta.mockReturnValue(new Promise((resolver) => { terminar = resolver; }));
    renderizarBaja();

    abrir('Desactivar mi cuenta');
    confirmar('Sí, desactivar mi cuenta');

    expect(screen.getByRole('button', { name: 'Sí, desactivar mi cuenta' })).toBeDisabled();
    terminar();
    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it.each(OPERACIONES)('con contraseña incorrecta en %s avisa y permite reintentar', async (_op, abrirNombre, confirmarNombre, servicio) => {
    servicio.mockRejectedValueOnce(new ErrorApi(403, 'La contraseña no es correcta.', {}, { codigo: 'CONFIRMACION_INVALIDA' }));
    servicio.mockResolvedValueOnce(undefined);
    renderizarBaja();

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
    renderizarBaja();

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
    renderizarBaja();

    abrir('Eliminar mi cuenta');
    confirmar('Sí, eliminar mi cuenta');

    const aviso = await screen.findByRole('alert');
    expect(aviso).toHaveTextContent('No pudimos completar la operación. Intenta de nuevo.');
    expect(aviso).not.toHaveTextContent(error.message);
  });

  it('cancelar cierra la confirmación, descarta la contraseña y no llama al servicio', () => {
    renderizarBaja();

    abrir('Desactivar mi cuenta');
    fireEvent.change(campoConfirmacion(), { target: { value: 'Segura#2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(campoConfirmacion()).not.toBeInTheDocument();
    abrir('Eliminar mi cuenta');
    expect(campoConfirmacion()).toHaveValue('');
    expect(desactivarCuenta).not.toHaveBeenCalled();
    expect(eliminarCuenta).not.toHaveBeenCalled();
  });
});
