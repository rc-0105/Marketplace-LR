import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as http from '../../../shared/services/http.js';
import * as servicio from './auditoriaService.js';

vi.mock('../../../shared/services/http.js', () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  eliminar: vi.fn(),
}));

describe('listar', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía pagina 0 y tamano 50 por defecto cuando no hay filtros', async () => {
    http.get.mockResolvedValue({ contenido: [] });

    await servicio.listar({});

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/auditoria?pagina=0&tamano=50');
  });

  it('envía solo los filtros con valor, antes de la paginación', async () => {
    http.get.mockResolvedValue({ contenido: [] });

    await servicio.listar({ usuario: '2', evento: 'RESERVA', entidad: '', entidadId: '  ', desde: '', hasta: '' });

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/auditoria?usuario=2&evento=RESERVA&pagina=0&tamano=50');
  });

  it('envía la página y el tamaño pedidos y todos los filtros', async () => {
    http.get.mockResolvedValue({ contenido: [] });

    await servicio.listar(
      { usuario: '2', evento: 'RESERVA', entidad: 'Reserva', entidadId: '55', desde: '2026-11-01', hasta: '2026-11-30' },
      2, 10,
    );

    expect(http.get).toHaveBeenCalledWith(
      '/api/v1/admin/auditoria?usuario=2&evento=RESERVA&entidad=Reserva&entidadId=55'
      + '&desde=2026-11-01&hasta=2026-11-30&pagina=2&tamano=10');
  });

  it('devuelve la página tal como la entrega la API', async () => {
    const pagina = { contenido: [{ id: 1 }], pagina: 0, tamano: 50, totalElementos: 1, totalPaginas: 1 };
    http.get.mockResolvedValue(pagina);

    await expect(servicio.listar({})).resolves.toEqual(pagina);
  });

  it('propaga el error de la API', async () => {
    http.get.mockRejectedValue(new Error('rango invalido'));

    await expect(servicio.listar({})).rejects.toThrow('rango invalido');
  });
});

describe('detalle', () => {
  beforeEach(() => vi.resetAllMocks());

  it('consulta el evento por id', async () => {
    http.get.mockResolvedValue({ id: 7 });

    await expect(servicio.detalle(7)).resolves.toEqual({ id: 7 });

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/auditoria/7');
  });
});

describe('solo lectura', () => {
  beforeEach(() => vi.resetAllMocks());

  it('solo exporta consultas y nunca invoca un verbo que modifique', async () => {
    http.get.mockResolvedValue({ contenido: [] });

    expect(Object.keys(servicio).sort()).toEqual(['detalle', 'listar']);
    await servicio.listar({});
    await servicio.detalle(1);

    expect(http.get).toHaveBeenCalledTimes(2);
    expect(http.post).not.toHaveBeenCalled();
    expect(http.put).not.toHaveBeenCalled();
    expect(http.eliminar).not.toHaveBeenCalled();
  });
});
