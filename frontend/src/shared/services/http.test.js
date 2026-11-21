import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, descargar, eliminar, get, post, put, subirArchivo } from './http.js';
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

  it('agrega a la solicitud las cabeceras extra que recibe post, sin perder las propias', async () => {
    const fetchFalso = vi.fn(() => respuesta(201, { id: 1 }));
    vi.stubGlobal('fetch', fetchFalso);

    await post('/api/v1/recurso', { a: 1 }, { 'Idempotency-Key': 'clave-1' });

    const [, opciones] = fetchFalso.mock.calls[0];
    expect(opciones.headers['Idempotency-Key']).toBe('clave-1');
    expect(opciones.headers['Content-Type']).toBe('application/json');
    expect(opciones.headers.Accept).toBe('application/json');
  });

  it('un archivo viaja como multipart: no fija el Content-Type, que lleva el separador del navegador', async () => {
    const fetchFalso = vi.fn(() => respuesta(201, { url: '/api/v1/imagenes/abc' }));
    vi.stubGlobal('fetch', fetchFalso);
    const datos = new FormData();
    datos.append('archivo', new File(['x'], 'foto.png', { type: 'image/png' }));

    await expect(subirArchivo('/api/v1/recurso/archivo', datos)).resolves.toEqual({ url: '/api/v1/imagenes/abc' });

    const [, opciones] = fetchFalso.mock.calls[0];
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBe(datos);
    expect(opciones.headers['Content-Type']).toBeUndefined();
    expect(opciones.headers.Accept).toBe('application/json');
  });

  it('consulta con GET sin cuerpo y devuelve la respuesta', async () => {
    const fetchFalso = vi.fn(() => respuesta(200, [{ id: 1 }]));
    vi.stubGlobal('fetch', fetchFalso);

    await expect(get('/api/v1/recurso')).resolves.toEqual([{ id: 1 }]);
    const [, opciones] = fetchFalso.mock.calls[0];
    expect(opciones.method).toBe('GET');
    expect(opciones.body).toBeUndefined();
  });

  it('reemplaza con PUT enviando el cuerpo como JSON', async () => {
    const fetchFalso = vi.fn(() => respuesta(200, { id: 1 }));
    vi.stubGlobal('fetch', fetchFalso);

    await expect(put('/api/v1/recurso/1', { a: 2 })).resolves.toEqual({ id: 1 });
    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/recurso/1', expect.objectContaining({
      method: 'PUT',
      body: '{"a":2}',
    }));
  });

  it('elimina con DELETE sin cuerpo y devuelve null ante un 204', async () => {
    const fetchFalso = vi.fn(() => respuesta(204));
    vi.stubGlobal('fetch', fetchFalso);

    await expect(eliminar('/api/v1/recurso/1')).resolves.toBeNull();
    const [ruta, opciones] = fetchFalso.mock.calls[0];
    expect(ruta).toBe('/api/v1/recurso/1');
    expect(opciones.method).toBe('DELETE');
    expect(opciones.body).toBeUndefined();
  });

  it('convierte un error de la API en ErrorApi con el detalle y los errores por campo', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(400, { detail: 'Datos inválidos.', errores: { correo: 'Mal' } })));

    const error = await post('/api/v1/recurso', {}).catch((e) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    expect(error.estado).toBe(400);
    expect(error.message).toBe('Datos inválidos.');
    expect(error.errores).toEqual({ correo: 'Mal' });
  });

  it('conserva el código y el motivo que trae el error de la API', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(403, {
      detail: 'Tu cuenta está restringida.', codigo: 'CUENTA_RESTRINGIDA', motivo: 'Pagos en disputa',
    })));

    const error = await post('/api/v1/auth/login', {}).catch((e) => e);

    expect(error).toMatchObject({
      estado: 403, message: 'Tu cuenta está restringida.', codigo: 'CUENTA_RESTRINGIDA', motivo: 'Pagos en disputa', errores: {},
    });
  });

  it('deja el código y el motivo en null cuando el error no los trae', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(400, { detail: 'Datos inválidos.' })));

    const error = await post('/api/v1/recurso', {}).catch((e) => e);

    expect(error.codigo).toBeNull();
    expect(error.motivo).toBeNull();
    expect(error.message).toBe('Datos inválidos.');
  });

  it('usa un mensaje genérico cuando el error no trae detalle', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(500)));

    await expect(post('/api/v1/recurso', {})).rejects.toMatchObject({
      estado: 500,
      message: 'Ocurrió un error inesperado. Intenta de nuevo.',
      errores: {},
    });
  });

  it('descarga un archivo con el nombre que envía la API, sin pedir JSON', async () => {
    const fetchFalso = vi.fn(() => Promise.resolve(new Response('PDF', {
      status: 200,
      headers: { 'Content-Disposition': 'attachment; filename="reporte-reservas-historico.pdf"' },
    })));
    vi.stubGlobal('fetch', fetchFalso);

    const archivo = await descargar('/api/v1/admin/reportes/reservas?formato=pdf');

    expect(archivo.nombre).toBe('reporte-reservas-historico.pdf');
    // El Blob de jsdom no implementa text(): basta con que traiga los 3 bytes del cuerpo.
    expect(archivo.contenido.size).toBe(3);
    expect(fetchFalso).toHaveBeenCalledWith('/api/v1/admin/reportes/reservas?formato=pdf', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({ Accept: '*/*' }),
    }));
  });

  it('una descarga sin nombre en la respuesta usa "archivo"', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('x', { status: 200 }))));

    await expect(descargar('/api/v1/recurso')).resolves.toMatchObject({ nombre: 'archivo' });
  });

  it('una descarga rechazada se convierte en ErrorApi con el detalle de la API', async () => {
    vi.stubGlobal('fetch', vi.fn(() => respuesta(400, { detail: 'Formato de exportación desconocido. Use pdf o xlsx.' })));

    await expect(descargar('/api/v1/recurso')).rejects.toMatchObject({
      estado: 400, message: 'Formato de exportación desconocido. Use pdf o xlsx.',
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

    it('una descarga envía el token y un 401 limpia la sesión igual que las demás peticiones', async () => {
      iniciarSesion();
      const fetchFalso = vi.fn(() => respuesta(401, { detail: 'Necesitas iniciar sesión.' }));
      vi.stubGlobal('fetch', fetchFalso);

      await expect(descargar('/api/v1/recurso')).rejects.toMatchObject({ estado: 401 });

      expect(fetchFalso.mock.calls[0][1].headers).toHaveProperty('Authorization', 'Bearer abc.def');
      expect(obtenerSesion()).toBeNull();
    });

    it('conserva la sesión ante un 401 de /api/v1/auth/', async () => {
      iniciarSesion();
      vi.stubGlobal('fetch', vi.fn(() => respuesta(401, {})));

      await expect(post('/api/v1/auth/login', {})).rejects.toMatchObject({ estado: 401 });

      expect(obtenerSesion()).not.toBeNull();
    });

    it.each([
      ['/api/v1/auth/login', 'CUENTA_DESACTIVADA'],
      ['/api/v1/recurso', 'CUENTA_RESTRINGIDA'],
      ['/api/v1/perfil/reactivar', 'CUENTA_ACTIVA'],
    ])('conserva la sesión y no avisa la expiración ante un 403 de %s', async (ruta, codigo) => {
      iniciarSesion();
      const expiracion = vi.fn();
      suscribirExpiracion(expiracion);
      vi.stubGlobal('fetch', vi.fn(() => respuesta(403, { detail: 'No.', codigo })));

      await expect(post(ruta, {})).rejects.toMatchObject({ estado: 403, codigo });

      expect(obtenerSesion()).not.toBeNull();
      expect(expiracion).not.toHaveBeenCalled();
    });

    it('no limpia nada ante el 401 de /api/v1/perfil/reactivar sin sesión previa', async () => {
      const expiracion = vi.fn();
      suscribirExpiracion(expiracion);
      vi.stubGlobal('fetch', vi.fn(() => respuesta(401, { detail: 'Correo o contraseña incorrectos.' })));

      await expect(post('/api/v1/perfil/reactivar', {})).rejects.toMatchObject({ estado: 401 });

      expect(obtenerSesion()).toBeNull();
      expect(expiracion).not.toHaveBeenCalled();
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
