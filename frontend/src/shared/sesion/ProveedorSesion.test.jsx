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

const iniciar = (ms, token = 'abc') => guardarSesion({ token, usuario: { rol: 'HUESPED' }, expiraEn: new Date(Date.now() + ms).toISOString() });
const DIAS_40 = 40 * 24 * 60 * 60 * 1000;

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

  it('no limpia una sesión que vence en más de 2^31-1 ms al pasar un instante', () => {
    renderizar();
    act(() => iniciar(DIAS_40));

    act(() => vi.advanceTimersByTime(1_000));

    expect(obtenerSesion()).not.toBeNull();
    expect(screen.getByText('/huesped')).toBeInTheDocument();
  });

  it('con un vencimiento lejano reprograma el temporizador y limpia al llegar al instante real', () => {
    renderizar();
    act(() => iniciar(DIAS_40));

    act(() => vi.advanceTimersByTime(DIAS_40 - 1_000));
    expect(obtenerSesion()).not.toBeNull();

    act(() => vi.advanceTimersByTime(2_000));

    expect(obtenerSesion()).toBeNull();
    expect(screen.getByText('/inicio-sesion expirada')).toBeInTheDocument();
  });

  it('al guardar una sesión con vencimiento posterior reprograma: el plazo viejo pasa sin limpiar y el nuevo limpia', () => {
    renderizar();
    act(() => iniciar(5_000));
    act(() => iniciar(20_000, 'nueva'));

    act(() => vi.advanceTimersByTime(6_000));
    expect(obtenerSesion()?.token).toBe('nueva');
    expect(screen.getByText('/huesped')).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(15_000));

    expect(obtenerSesion()).toBeNull();
    expect(screen.getByText('/inicio-sesion expirada')).toBeInTheDocument();
  });

  it('cerrar sesión cancela el temporizador: no hay navegación por expiración después', () => {
    renderizar();
    act(() => iniciar(5_000));

    act(() => limpiarSesion());
    act(() => vi.advanceTimersByTime(10_000));

    expect(vi.getTimerCount()).toBe(0);
    expect(screen.getByText('/huesped')).toBeInTheDocument();
  });

  it('un temporizador viejo no limpia una sesión nueva aunque se dispare', () => {
    renderizar();
    act(() => iniciar(5_000));
    // Se reemplaza la sesión sin avisar al proveedor: el temporizador viejo sigue vivo.
    sessionStorage.setItem('marketplace.sesion', JSON.stringify({
      token: 'nueva', usuario: { rol: 'HUESPED' }, expiraEn: new Date(Date.now() + 60_000).toISOString(),
    }));

    act(() => vi.advanceTimersByTime(5_001));

    expect(obtenerSesion()?.token).toBe('nueva');
    expect(screen.getByText('/huesped')).toBeInTheDocument();
  });

  it('cancela el temporizador y la suscripción al desmontarse', () => {
    const { unmount } = renderizar();
    act(() => iniciar(5_000));
    unmount();

    act(() => vi.advanceTimersByTime(10_000));

    expect(vi.getTimerCount()).toBe(0);
  });
});
