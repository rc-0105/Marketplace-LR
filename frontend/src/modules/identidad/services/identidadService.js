import { post } from '../../../shared/services/http.js';
import { usuarioDesdeApi } from '../models/usuario.js';

/**
 * @param {import('../models/usuario.js').DatosRegistro} datos
 * @returns {Promise<import('../models/usuario.js').Usuario>}
 */
export async function registrarUsuario(datos) {
  return usuarioDesdeApi(await post('/api/v1/auth/registro', datos));
}
