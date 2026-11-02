import { beforeEach, describe, expect, it, vi } from 'vitest';
import { eliminar, get, post, put } from '../../../shared/services/http.js';
import { eliminarPeriodo, listarPeriodos, modificarPeriodo, registrarPeriodo } from './disponibilidadService.js';

vi.mock('../../../shared/services/http.js', () => ({
  eliminar: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

const RESPUESTA = { id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15' };
const FECHAS = { fechaInicio: '2027-01-10', fechaFin: '2027-01-15' };

describe('disponibilidadService', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lista los periodos de la publicación', async () => {
    vi.mocked(get).mockResolvedValue([RESPUESTA]);

    await expect(listarPeriodos(3)).resolves.toEqual([RESPUESTA]);
    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/3/disponibilidad');
  });

  it('registra un periodo en la publicación', async () => {
    vi.mocked(post).mockResolvedValue(RESPUESTA);

    await expect(registrarPeriodo(3, FECHAS)).resolves.toEqual(RESPUESTA);
    expect(post).toHaveBeenCalledWith('/api/v1/publicaciones/3/disponibilidad', FECHAS);
  });

  it('modifica las fechas de un periodo', async () => {
    vi.mocked(put).mockResolvedValue(RESPUESTA);

    await expect(modificarPeriodo(5, FECHAS)).resolves.toEqual(RESPUESTA);
    expect(put).toHaveBeenCalledWith('/api/v1/disponibilidad/5', FECHAS);
  });

  it('elimina un periodo', async () => {
    vi.mocked(eliminar).mockResolvedValue(null);

    await expect(eliminarPeriodo(5)).resolves.toBeUndefined();
    expect(eliminar).toHaveBeenCalledWith('/api/v1/disponibilidad/5');
  });
});
