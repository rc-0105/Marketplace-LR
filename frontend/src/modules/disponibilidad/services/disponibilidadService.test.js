import { beforeEach, describe, expect, it, vi } from 'vitest';
import { eliminar, get, post, put } from '../../../shared/services/http.js';
import {
  consultarCalendario,
  eliminarPeriodo,
  listarPeriodos,
  modificarPeriodo,
  registrarPeriodo,
} from './disponibilidadService.js';

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

  describe('consultarCalendario', () => {
    const CALENDARIO = {
      publicacionId: 10, desde: '2026-12-01', hasta: '2026-12-03', tienePeriodos: true,
      dias: [{ fecha: '2026-12-01', estado: 'DISPONIBLE', reservaId: null }, { fecha: '2026-12-02', estado: 'RESERVADO', reservaId: 77 }],
    };

    it('pide el calendario sin query cuando no se indica la ventana y convierte la respuesta', async () => {
      vi.mocked(get).mockResolvedValue(CALENDARIO);

      const calendario = await consultarCalendario(10);

      expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/10/calendario');
      expect(calendario.dias[0]).toEqual({ fecha: '2026-12-01', estado: 'DISPONIBLE' });
      expect(calendario.dias[1]).toEqual({ fecha: '2026-12-02', estado: 'RESERVADO', reservaId: 77 });
    });

    it('envía desde y hasta solo cuando se indican', async () => {
      vi.mocked(get).mockResolvedValue(CALENDARIO);

      await consultarCalendario(10, { desde: '2026-12-01', hasta: '2026-12-11' });
      expect(get).toHaveBeenLastCalledWith('/api/v1/publicaciones/10/calendario?desde=2026-12-01&hasta=2026-12-11');

      await consultarCalendario(10, { desde: '2026-12-01' });
      expect(get).toHaveBeenLastCalledWith('/api/v1/publicaciones/10/calendario?desde=2026-12-01');

      await consultarCalendario(10, { hasta: '2026-12-11' });
      expect(get).toHaveBeenLastCalledWith('/api/v1/publicaciones/10/calendario?hasta=2026-12-11');
    });
  });
});
