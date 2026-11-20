import { beforeEach, describe, expect, it, vi } from 'vitest';
import { post } from '../../../shared/services/http.js';
import { cotizarEstancia } from './cotizacionService.js';

vi.mock('../../../shared/services/http.js', () => ({ post: vi.fn() }));

describe('cotizarEstancia', () => {
  beforeEach(() => vi.resetAllMocks());

  it('envía la publicación, las fechas y los servicios adicionales elegidos', async () => {
    vi.mocked(post).mockResolvedValue({ noches: 4, serviciosAdicionales: [] });

    await cotizarEstancia({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionales: [1] });

    expect(post).toHaveBeenCalledWith('/api/v1/cotizaciones', {
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionales: [1],
    });
  });

  it('sin servicios adicionales envía una lista vacía', async () => {
    vi.mocked(post).mockResolvedValue({ noches: 4, serviciosAdicionales: [] });

    await cotizarEstancia({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(post).toHaveBeenCalledWith('/api/v1/cotizaciones', expect.objectContaining({ serviciosAdicionales: [] }));
  });

  it('convierte los importes, que llegan como texto, a número', async () => {
    vi.mocked(post).mockResolvedValue({
      publicacionId: 5, noches: 4, precioNoche: '100000.00', precioBase: '400000.00', tarifaLimpieza: '30000.00',
      tarifaServicio: '40000.00', descuento: '0.00',
      serviciosAdicionales: [{ id: 1, nombre: 'Desayuno', costo: '15000.00' }],
      totalServiciosAdicionales: '15000.00', total: '485000.00',
    });

    const cotizacion = await cotizarEstancia({ publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14' });

    expect(cotizacion).toEqual({
      noches: 4, precioNoche: 100000, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000,
      descuento: 0, serviciosAdicionales: [{ id: 1, nombre: 'Desayuno', costo: 15000 }],
      totalServiciosAdicionales: 15000, total: 485000,
    });
  });
});
