import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, get } from '../../../shared/services/http.js';
import { consultarDashboard } from './dashboardService.js';

vi.mock('../../../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
}));

describe('dashboardService', () => {
  beforeEach(() => vi.resetAllMocks());

  it('sin rango pide el dashboard sin parámetros', async () => {
    vi.mocked(get).mockResolvedValue({ publicacionesActivas: 0 });

    await expect(consultarDashboard()).resolves.toEqual({ publicacionesActivas: 0 });
    expect(get).toHaveBeenCalledWith('/api/v1/admin/dashboard');
  });

  it('envía solo las fechas con valor', async () => {
    vi.mocked(get).mockResolvedValue({});

    await consultarDashboard({ desde: '2026-11-01', hasta: '  ' });
    expect(get).toHaveBeenLastCalledWith('/api/v1/admin/dashboard?desde=2026-11-01');

    await consultarDashboard({ desde: '2026-11-01', hasta: '2026-11-30' });
    expect(get).toHaveBeenLastCalledWith('/api/v1/admin/dashboard?desde=2026-11-01&hasta=2026-11-30');
  });

  it('propaga el error de la API', async () => {
    vi.mocked(get).mockRejectedValue(new ErrorApi(400, 'La fecha desde no puede ser posterior a la fecha hasta.'));

    await expect(consultarDashboard({ desde: '2026-11-30', hasta: '2026-11-01' }))
      .rejects.toMatchObject({ estado: 400 });
  });
});
