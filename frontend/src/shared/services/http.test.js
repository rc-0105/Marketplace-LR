import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, post } from './http.js';

function respuesta(estado, cuerpo) {
  return Promise.resolve(new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado }));
}

describe('http', () => {
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
});
