import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from '../../../shared/services/http.js';
import { buscarAlojamientos } from './busquedaService.js';

vi.mock('../../../shared/services/http.js', () => ({ get: vi.fn() }));

describe('buscarAlojamientos', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía la ciudad y las fechas como parámetros codificados', async () => {
    vi.mocked(get).mockResolvedValue([]);

    await buscarAlojamientos({ ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25' });

    expect(get).toHaveBeenCalledWith('/api/v1/busqueda?ciudad=Santa+Marta&llegada=2026-12-20&salida=2026-12-25');
  });

  it('convierte cada resultado al modelo del frontend', async () => {
    vi.mocked(get).mockResolvedValue([
      { id: 1, titulo: 'Casa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: '250000.00' },
    ]);

    const resultados = await buscarAlojamientos({ ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25' });

    expect(resultados).toEqual([
      { id: 1, titulo: 'Casa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000, imagenPrincipal: null },
    ]);
  });
});
