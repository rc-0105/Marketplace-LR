import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, post } from './http.js';
import { guardarSesion, limpiarSesion, obtenerSesion, suscribirExpiracion } from './sesion.js';

function respuesta(estado, cuerpo) {
  return Promise.resolve(new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado }));
}

describe('http', () => {
  beforeEach(() => {
    limpiarSesion();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('envía el cuerpo como JSON y devuelve la respuesta', async () => {
    const fetchFalso = vi.fn(() => respuesta(201, { id: 1 }));
    vi.stubGlobal('fetch', fetchFalso);

    await expect(post('/api/v1/recurso', { a: 1 })).resolves.toEqual({ id: 1 });
    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/recurso', expect.objectContaining({
      method: 'POST',
      body: '{"a":1}',
      headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
    }));
  });

  it('convierte un error de la API en ErrorApi con el detalle y los errores por campo', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(400, { detail: 'Datos inválidos.', errores: { correo: 'Mal' } })));

    const error = await post('/api/v1/recurso', {}).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    expect(error.estado).toBe(400);
    expect(error.message).toBe('Datos inválidos.');
    expect(error.errores).toEqual({ correo: 'Mal' });
  });

  it('usa un mensaje genérico cuando el error no trae detalle', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(500)));

    await expect(post('/api/v1/recurso', {})).rejects.toMatchObject({
      estado: 500,
      message: 'Ocurrió un error inesperado. Intenta de nuevo.',
      errores: {},
    });
  });

  it('informa cuando no hay conexión con el servidor', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));

    await expect(post('/api/v1/recurso', {})).rejects.toMatchObject({
      estado: 0,
      message: 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.',
    });
  });

  describe('con sesión', () => {
    const iniciarSesion = () => guardarSesion({
      token: 'abc.def',
      usuario: { id: 1, rol: 'HUESPED' },
      expiraEn: new Date(Date.now() + 60_000).toISOString(),
    });

    it('envía el token como Bearer cuando hay sesión y no lo envía cuando no la hay', async () => {
      const fetchFalso = vi.fn(() => respuesta(200, {}));
      vi.stubGlobal('fetch', fetchFalso);

      await post('/api/v1/recurso', {});
      iniciarSesion();
      await post('/api/v1/recurso', {});

      expect(fetchFalso.mock.calls[0][1].headers).not.toHaveProperty('Authorization');
      expect(fetchFalso.mock.calls[1][1].headers).toHaveProperty('Authorization', 'Bearer abc.def');
    });

    it('limpia la sesión y avisa la expiración ante un 401 con token fuera de /api/v1/auth/', async () => {
      iniciarSesion();
      const expiracion = vi.fn();
      suscribirExpiracion(expiracion);
      vi.stubGlobal('fetch', vi.fn(() => respuesta(401, { detail: 'Necesitas iniciar sesión.' })));

      await expect(post('/api/v1/recurso', {})).rejects.toMatchObject({ estado: 401 });

      expect(obtenerSesion()).toBeNull();
      expect(expiracion).toHaveBeenCalledTimes(1);
    });

    it('no limpia una sesión nueva ante el 401 tardío de una petición enviada con un token anterior', async () => {
      iniciarSesion();
      let resolver;
      vi.stubGlobal('fetch', vi.fn(() => new Promise((res) => { resolver = res; })));
      const expiracion = vi.fn();
      suscribirExpiracion(expiracion);

      const pendiente = post('/api/v1/recurso', {}).catch((e) => e);
      guardarSesion({ token: 'nuevo.token', usuario: { id: 1, rol: 'HUESPED' }, expiraEn: new Date(Date.now() + 60_000).toISOString() });
      resolver(new Response('{}', { status: 401 }));
      await pendiente;

      expect(obtenerSesion()?.token).toBe('nuevo.token');
      expect(expiracion).not.toHaveBeenCalled();
    });

    it('conserva la sesión ante un 401 de /api/v1/auth/', async () => {
      iniciarSesion();
      vi.stubGlobal('fetch', vi.fn(() => respuesta(401, {})));

      await expect(post('/api/v1/auth/login', {})).rejects.toMatchObject({ estado: 401 });

      expect(obtenerSesion()).not.toBeNull();
    });

    it('no avisa la expiración ante un 401 sin token enviado', async () => {
      const expiracion = vi.fn();
      suscribirExpiracion(expiracion);
      vi.stubGlobal('fetch', vi.fn(() => respuesta(401, {})));

      await expect(post('/api/v1/recurso', {})).rejects.toMatchObject({ estado: 401 });

      expect(expiracion).not.toHaveBeenCalled();
    });

    it.each([[503], [500], [0]])('conserva la sesión ante el estado %i y sigue funcionando después', async (estado) => {
      iniciarSesion();
      const fetchFalso = vi.fn()
        .mockImplementationOnce(() => (estado === 0 ? Promise.reject(new TypeError('red')) : respuesta(estado, {})))
        .mockImplementationOnce(() => respuesta(200, { ok: true }));
      vi.stubGlobal('fetch', fetchFalso);

      await expect(post('/api/v1/recurso', {})).rejects.toBeInstanceOf(ErrorApi);
      expect(obtenerSesion()).not.toBeNull();
      await expect(post('/api/v1/recurso', {})).resolves.toEqual({ ok: true });
    });
  });
});
