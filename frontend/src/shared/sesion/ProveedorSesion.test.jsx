import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { guardarSesion, limpiarSesion, obtenerSesion } from '../services/sesion.js';
import ProveedorSesion from './ProveedorSesion.jsx';

function Ubicacion() {
  const { pathname, state } = useLocation();
  return <p>{`${pathname} ${state?.sesionExpirada ? 'expirada' : ''}`}</p>;
}

function renderizar() {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/huesped']}>
      <ProveedorSesion><Ubicacion /></ProveedorSesion>
    </MemoryRouter>,
  );
}

const iniciar = (ms) => guardarSesion({ token: 'abc', usuario: { rol: 'HUESPED' }, expiraEn: new Date(Date.now() + ms).toISOString() });

describe('ProveedorSesion', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    limpiarSesion();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('muestra a sus hijos sin agregar markup propio', () => {
    const { container } = renderizar();

    expect(container.children).toHaveLength(1);
    expect(screen.getByText('/huesped')).toBeInTheDocument();
  });

  it('navega al inicio de sesión con el aviso cuando la sesión se limpia como expirada', () => {
    renderizar();
    act(() => iniciar(60_000));

    act(() => limpiarSesion('expirada'));

    expect(screen.getByText('/inicio-sesion expirada')).toBeInTheDocument();
  });

  it('limpia la sesión y navega cuando vence el temporizador de expiraEn', () => {
    renderizar();
    act(() => iniciar(5_000));

    act(() => vi.advanceTimersByTime(5_001));

    expect(obtenerSesion()).toBeNull();
    expect(screen.getByText('/inicio-sesion expirada')).toBeInTheDocument();
  });

  it('cancela el temporizador y la suscripción al desmontarse', () => {
    const { unmount } = renderizar();
    act(() => iniciar(5_000));
    unmount();

    act(() => vi.advanceTimersByTime(10_000));

    expect(vi.getTimerCount()).toBe(0);
  });
});
