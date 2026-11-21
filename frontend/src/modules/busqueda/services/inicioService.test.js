import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from '../../../shared/services/http.js';
import { consultarDestacados } from './inicioService.js';

vi.mock('../../../shared/services/http.js', () => ({ get: vi.fn() }));

describe('consultarDestacados', () => {
  beforeEach(() => vi.resetAllMocks());

  it('consulta la ruta pública y convierte cada alojamiento', async () => {
    vi.mocked(get).mockResolvedValue([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4,
        precioNoche: '250000', imagenPrincipal: 'https://imagenes.com/1.jpg' },
    ]);

    const resultado = await consultarDestacados();

    expect(get).toHaveBeenCalledWith('/api/v1/inicio/destacados');
    expect(resultado).toEqual([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4,
        precioNoche: 250000, imagenPrincipal: 'https://imagenes.com/1.jpg' },
    ]);
  });

  it('envía el límite cuando se pide', async () => {
    vi.mocked(get).mockResolvedValue([]);

    await consultarDestacados(6);

    expect(get).toHaveBeenCalledWith('/api/v1/inicio/destacados?limite=6');
  });
});
