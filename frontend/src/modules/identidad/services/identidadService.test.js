import { describe, expect, it, vi } from 'vitest';
import { post } from '../../../shared/services/http.js';
import { registrarUsuario } from './identidadService.js';

vi.mock('../../../shared/services/http.js', () => ({ post: vi.fn() }));

describe('identidadService', () => {
  it('registra al usuario en la API y devuelve el modelo del frontend', async () => {
    vi.mocked(post).mockResolvedValue({
      id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', estado: 'ACTIVA',
    });
    const datos = { nombre: 'Ana', correo: 'ana@correo.com', contrasena: 'Segura#2026', rol: 'HUESPED' };

    const usuario = await registrarUsuario(datos);

    expect(post).toHaveBeenCalledWith('/api/v1/auth/registro', datos);
    expect(usuario).toEqual({ id: 7, nombre: 'Ana', correo: 'ana@correo.com', rol: 'HUESPED', activa: true });
  });
});
