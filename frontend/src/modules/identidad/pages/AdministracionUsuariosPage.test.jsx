import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { guardarSesion, limpiarSesion } from '../../../shared/services/sesion.js';
import {
  listarUsuariosAdministrables, rehabilitarUsuario, restringirUsuario,
} from '../services/identidadService.js';
import AdministracionUsuariosPage from './AdministracionUsuariosPage.jsx';

vi.mock('../services/identidadService.js', () => ({
  listarUsuariosAdministrables: vi.fn(),
  rehabilitarUsuario: vi.fn(),
  restringirUsuario: vi.fn(),
}));

function usuario(datos) {
  return {
    id: 7, nombre: 'Ana Gómez', correo: 'ana@correo.com', rol: 'HUESPED', estado: 'ACTIVA', motivoRestriccion: '',
    ...datos,
  };
}

const ANA = usuario();
const BETO = usuario({
  id: 8, nombre: 'Beto Ruiz', correo: 'beto@correo.com', rol: 'ANFITRION', estado: 'RESTRINGIDA',
  motivoRestriccion: 'Fotos engañosas',
});

describe('AdministracionUsuariosPage', () => {
  beforeEach(() => vi.resetAllMocks());
  afterEach(() => limpiarSesion());

  it('en la cuenta del administrador con sesión no ofrece restringir ni rehabilitar', async () => {
    const expiraEn = new Date(Date.now() + 3600_000).toISOString();
    guardarSesion({ token: 't', expiraEn, usuario: { id: 7, nombre: 'Ana Gómez', correo: 'ana@correo.com',
      rol: 'ADMINISTRADOR', activa: true } });
    vi.mocked(listarUsuariosAdministrables).mockResolvedValue([usuario({ rol: 'ADMINISTRADOR' }), BETO]);

    render(<AdministracionUsuariosPage />);

    expect(await screen.findByText('Es tu cuenta: no puedes restringirla.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Restringir a Ana Gómez' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Motivo de la restricción para Ana Gómez')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rehabilitar a Beto Ruiz' })).toBeInTheDocument();
  });

  it('muestra las cuentas con su correo, rol, estado y el motivo de las restringidas', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValue([ANA, BETO]);

    render(<AdministracionUsuariosPage />);

    const nombres = await screen.findAllByRole('heading', { level: 2 });
    expect(nombres.map((nombre) => nombre.textContent)).toEqual(['Ana Gómez', 'Beto Ruiz']);
    expect(listarUsuariosAdministrables).toHaveBeenCalledWith('');
    expect(screen.getByText('ana@correo.com · Huésped')).toBeInTheDocument();
    expect(screen.getByText('beto@correo.com · Anfitrión')).toBeInTheDocument();
    expect(screen.getByText('Restringida')).toBeInTheDocument();
    expect(screen.getByText('Motivo: Fotos engañosas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Restringir a Ana Gómez' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rehabilitar a Beto Ruiz' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Restringir a Beto Ruiz' })).not.toBeInTheDocument();
  });

  it('a una cuenta desactivada no le ofrece restringir ni rehabilitar', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValue([usuario({ estado: 'DESACTIVADA' })]);

    render(<AdministracionUsuariosPage />);

    expect(await screen.findByText('Desactivada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Restringir|Rehabilitar/ })).not.toBeInTheDocument();
  });

  it('avisa mientras carga, cuando no hay coincidencias y cuando la carga falla', async () => {
    vi.mocked(listarUsuariosAdministrables).mockReturnValueOnce(new Promise(() => {}));
    const { unmount } = render(<AdministracionUsuariosPage />);
    expect(screen.getByText('Cargando usuarios…')).toBeInTheDocument();
    unmount();

    vi.mocked(listarUsuariosAdministrables).mockResolvedValueOnce([]);
    const segunda = render(<AdministracionUsuariosPage />);
    expect(await screen.findByText('No encontramos usuarios con ese nombre o correo.')).toBeInTheDocument();
    segunda.unmount();

    vi.mocked(listarUsuariosAdministrables).mockRejectedValueOnce(new ErrorApi(0, 'sin conexión'));
    render(<AdministracionUsuariosPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los usuarios. Intenta de nuevo.');
  });

  it('busca por el texto escrito', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValueOnce([ANA, BETO]).mockResolvedValueOnce([BETO]);
    render(<AdministracionUsuariosPage />);
    await screen.findByText('Ana Gómez');

    fireEvent.change(screen.getByLabelText('Buscar por nombre o correo'), { target: { value: 'beto' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => expect(screen.queryByText('Ana Gómez')).not.toBeInTheDocument());
    expect(listarUsuariosAdministrables).toHaveBeenLastCalledWith('beto');
    expect(screen.getByText('Beto Ruiz')).toBeInTheDocument();
  });

  it('si una búsqueda anterior responde tarde, muestra solo el resultado de la última', async () => {
    let responderPrimera;
    vi.mocked(listarUsuariosAdministrables)
      .mockReturnValueOnce(new Promise((resolver) => { responderPrimera = resolver; }))
      .mockResolvedValueOnce([BETO]);
    render(<AdministracionUsuariosPage />);

    fireEvent.change(screen.getByLabelText('Buscar por nombre o correo'), { target: { value: 'beto' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText('Beto Ruiz')).toBeInTheDocument();

    responderPrimera([ANA]);

    await waitFor(() => expect(screen.queryByText('Ana Gómez')).not.toBeInTheDocument());
    expect(screen.getByText('Beto Ruiz')).toBeInTheDocument();
  });

  it('sin motivo no restringe y señala el campo', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValue([ANA]);
    render(<AdministracionUsuariosPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Restringir a Ana Gómez' }));

    const campo = screen.getByLabelText('Motivo de la restricción para Ana Gómez');
    expect(campo).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Escribe el motivo de la restricción.')).toBeInTheDocument();
    expect(restringirUsuario).not.toHaveBeenCalled();
  });

  it('restringe con el motivo, confirma y vuelve a pedir la lista con la búsqueda vigente', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValueOnce([ANA])
      .mockResolvedValueOnce([usuario({ estado: 'RESTRINGIDA', motivoRestriccion: 'Pagos en disputa' })]);
    vi.mocked(restringirUsuario).mockResolvedValue(undefined);
    render(<AdministracionUsuariosPage />);

    fireEvent.change(await screen.findByLabelText('Motivo de la restricción para Ana Gómez'),
      { target: { value: 'Pagos en disputa' } });
    fireEvent.click(screen.getByRole('button', { name: 'Restringir a Ana Gómez' }));

    expect(await screen.findByText('La cuenta de Ana Gómez quedó restringida.')).toBeInTheDocument();
    expect(restringirUsuario).toHaveBeenCalledWith(7, 'Pagos en disputa');
    expect(listarUsuariosAdministrables).toHaveBeenCalledTimes(2);
    expect(listarUsuariosAdministrables).toHaveBeenLastCalledWith('');
    expect(await screen.findByText('Motivo: Pagos en disputa')).toBeInTheDocument();
  });

  it('rehabilita y confirma', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValueOnce([BETO])
      .mockResolvedValueOnce([{ ...BETO, estado: 'ACTIVA', motivoRestriccion: '' }]);
    vi.mocked(rehabilitarUsuario).mockResolvedValue(undefined);
    render(<AdministracionUsuariosPage />);

    fireEvent.click(await screen.findByRole('button', { name: 'Rehabilitar a Beto Ruiz' }));

    expect(await screen.findByText('La cuenta de Beto Ruiz recuperó el acceso.')).toBeInTheDocument();
    expect(rehabilitarUsuario).toHaveBeenCalledWith(8);
    expect(await screen.findByRole('button', { name: 'Restringir a Beto Ruiz' })).toBeInTheDocument();
  });

  it('muestra en la tarjeta el mensaje con que la API rechaza la acción', async () => {
    vi.mocked(listarUsuariosAdministrables).mockResolvedValue([ANA]);
    vi.mocked(restringirUsuario).mockRejectedValue(new ErrorApi(409, 'No puedes restringir tu propia cuenta.'));
    render(<AdministracionUsuariosPage />);

    fireEvent.change(await screen.findByLabelText('Motivo de la restricción para Ana Gómez'),
      { target: { value: 'Prueba' } });
    fireEvent.click(screen.getByRole('button', { name: 'Restringir a Ana Gómez' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No puedes restringir tu propia cuenta.');
    expect(listarUsuariosAdministrables).toHaveBeenCalledTimes(1);
  });
});
