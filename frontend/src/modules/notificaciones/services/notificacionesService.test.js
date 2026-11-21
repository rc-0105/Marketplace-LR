import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as http from '../../../shared/services/http.js';
import { listarNotificaciones, marcarLeida } from './notificacionesService.js';

vi.mock('../../../shared/services/http.js', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

describe('notificacionesService', () => {
  beforeEach(() => vi.resetAllMocks());

  it('lista las notificaciones propias con GET /api/v1/notificaciones', async () => {
    const respuesta = { noLeidas: 1, notificaciones: [{ id: 1 }] };
    http.get.mockResolvedValue(respuesta);

    await expect(listarNotificaciones()).resolves.toEqual(respuesta);
    expect(http.get).toHaveBeenCalledWith('/api/v1/notificaciones');
  });

  it('marca una notificación como leída con POST /api/v1/notificaciones/{id}/leer', async () => {
    http.post.mockResolvedValue({ id: 7, leida: true });

    await expect(marcarLeida(7)).resolves.toEqual({ id: 7, leida: true });
    expect(http.post).toHaveBeenCalledWith('/api/v1/notificaciones/7/leer');
  });
});
