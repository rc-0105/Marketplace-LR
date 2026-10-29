import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, post } from '../../../shared/services/http.js';
import { guardarSesion, obtenerSesion } from '../../../shared/services/sesion.js';
import { cerrarSesion, desactivarCuenta, eliminarCuenta, iniciarSesion, reactivarCuenta, registrarUsuario } from './identidadService.js';

vi.mock('../../../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  post: vi.fn(),
}));

const respuestaLogin = {
  token: 'jwt-abc',
  tipo: 'Bearer',
  expiraEn: '2999-01-01T00:00:00Z',
  usuario: { id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', estado: 'ACTIVA' },
};

describe('identidadService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
  });

  it('registra al usuario en la API y devuelve el modelo del frontend', async () => {
    vi.mocked(post).mockResolvedValue({
      id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', estado: 'ACTIVA',
    });
    const datos = { nombre: 'Ana', correo: 'ana@correo.com', contrasena: 'Segura#2026', rol: 'HUESPED' };

    const usuario = await registrarUsuario(datos);

    expect(post).toHaveBeenCalledWith('/api/v1/auth/registro', datos);
    expect(usuario).toEqual({ id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', activa: true });
  });

  it('inicia sesión, guarda token y usuario mapeado (estado a activa) y devuelve el usuario', async () => {
    vi.mocked(post).mockResolvedValue(respuestaLogin);
    const credenciales = { correo: 'ana@correo.com', contrasena: 'Segura#2026' };

    const usuario = await iniciarSesion(credenciales);

    const esperado = { id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', activa: true };
    expect(post).toHaveBeenCalledWith('/api/v1/auth/login', credenciales);
    expect(usuario).toEqual(esperado);
    expect(obtenerSesion()).toEqual({ token: 'jwt-abc', expiraEn: '2999-01-01T00:00:00Z', usuario: esperado });
  });

  it.each([400, 401])('ante un %i de login propaga el error sin guardar ni tocar la sesión', async (estado) => {
    const previa = { token: 'previa', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 1 } };
    guardarSesion(previa);
    const error = new ErrorApi(estado, 'Credenciales inválidas.', { correo: 'Obligatorio.' });
    vi.mocked(post).mockRejectedValue(error);

    await expect(iniciarSesion({ correo: 'x', contrasena: 'y' })).rejects.toBe(error);
    expect(error).toMatchObject({
      name: 'ErrorApi',
      estado,
      message: 'Credenciales inválidas.',
      errores: { correo: 'Obligatorio.' },
    });
    expect(obtenerSesion()).toEqual(previa);
  });

  it('reactiva la cuenta con las credenciales, guarda la sesión y devuelve el usuario', async () => {
    vi.mocked(post).mockResolvedValue(respuestaLogin);
    const credenciales = { correo: 'ana@correo.com', contrasena: 'Segura#2026' };

    const usuario = await reactivarCuenta(credenciales);

    const esperado = { id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', activa: true };
    expect(post).toHaveBeenCalledWith('/api/v1/perfil/reactivar', credenciales);
    expect(usuario).toEqual(esperado);
    expect(obtenerSesion()).toEqual({ token: 'jwt-abc', expiraEn: '2999-01-01T00:00:00Z', usuario: esperado });
  });

  it.each([401, 403, 409])('ante un %i al reactivar propaga el error sin guardar ni tocar la sesión', async (estado) => {
    const previa = { token: 'previa', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 1 } };
    guardarSesion(previa);
    const error = new ErrorApi(estado, 'No se pudo reactivar.', {}, { codigo: 'CUENTA_ACTIVA' });
    vi.mocked(post).mockRejectedValue(error);

    await expect(reactivarCuenta({ correo: 'x', contrasena: 'y' })).rejects.toBe(error);
    expect(obtenerSesion()).toEqual(previa);
  });

  it('cierra sesión llamando al logout y limpia la sesión local', async () => {
    guardarSesion({ token: 'jwt-abc', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 7 } });
    vi.mocked(post).mockResolvedValue(null);

    await cerrarSesion();

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout');
    expect(obtenerSesion()).toBeNull();
  });

  it.each([
    ['401', new ErrorApi(401, 'Sesión inválida.')],
    ['500', new ErrorApi(500, 'Error.')],
    ['503', new ErrorApi(503, 'No disponible.')],
    ['error de red', new ErrorApi(0, 'Sin conexión.')],
  ])('limpia la sesión local aunque el logout falle con %s', async (_caso, error) => {
    guardarSesion({ token: 'jwt-abc', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 7 } });
    vi.mocked(post).mockRejectedValue(error);

    await expect(cerrarSesion()).resolves.toBeUndefined();

    expect(obtenerSesion()).toBeNull();
  });

  it.each([
    ['desactiva', desactivarCuenta, '/api/v1/perfil/desactivar'],
    ['elimina', eliminarCuenta, '/api/v1/perfil/eliminar'],
  ])('%s la cuenta con la contraseña, limpia la sesión y no llama al logout', async (_caso, operacion, ruta) => {
    guardarSesion({ token: 'jwt-abc', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 7 } });
    vi.mocked(post).mockResolvedValue(null);

    await operacion('Segura#2026');

    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith(ruta, { contrasena: 'Segura#2026' });
    expect(obtenerSesion()).toBeNull();
  });

  it.each([
    ['desactivar', desactivarCuenta, 403, 'CONFIRMACION_INVALIDA'],
    ['desactivar', desactivarCuenta, 409, 'RESERVAS_FUTURAS_ACTIVAS'],
    ['eliminar', eliminarCuenta, 403, 'CONFIRMACION_INVALIDA'],
    ['eliminar', eliminarCuenta, 409, 'RESERVAS_FUTURAS_ACTIVAS'],
  ])('al %s, ante un %i (%s) propaga el error y conserva la sesión', async (_caso, operacion, estado, codigo) => {
    const previa = { token: 'previa', expiraEn: '2999-01-01T00:00:00Z', usuario: { id: 1 } };
    guardarSesion(previa);
    const error = new ErrorApi(estado, 'No se pudo.', {}, { codigo });
    vi.mocked(post).mockRejectedValue(error);

    await expect(operacion('x')).rejects.toBe(error);
    expect(obtenerSesion()).toEqual(previa);
  });
});
