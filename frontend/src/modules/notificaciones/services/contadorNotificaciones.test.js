import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  descontarNoLeida, fijarNoLeidas, obtenerNoLeidas, suscribirNoLeidas,
} from './contadorNotificaciones.js';

describe('contadorNotificaciones', () => {
  afterEach(() => fijarNoLeidas(0));

  it('guarda el contador y avisa a los suscriptores', () => {
    const oyente = vi.fn();
    const cancelar = suscribirNoLeidas(oyente);

    fijarNoLeidas(3);

    expect(obtenerNoLeidas()).toBe(3);
    expect(oyente).toHaveBeenCalledTimes(1);
    cancelar();
    fijarNoLeidas(4);
    expect(oyente).toHaveBeenCalledTimes(1);
  });

  it('al descontar baja en uno y nunca queda negativo', () => {
    fijarNoLeidas(1);

    descontarNoLeida();
    descontarNoLeida();

    expect(obtenerNoLeidas()).toBe(0);
  });
});
