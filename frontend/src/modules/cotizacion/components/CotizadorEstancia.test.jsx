import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { cotizarEstancia } from '../services/cotizacionService.js';
import CotizadorEstancia from './CotizadorEstancia.jsx';

vi.mock('../services/cotizacionService.js', () => ({ cotizarEstancia: vi.fn() }));

const ADICIONALES = [{ id: 1, nombre: 'Desayuno', costo: 15000 }];

const SIN_SERVICIOS = {
  noches: 4, precioNoche: 100000, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
  serviciosAdicionales: [], totalServiciosAdicionales: 0, total: 470000,
};

const CON_DESAYUNO = {
  ...SIN_SERVICIOS,
  serviciosAdicionales: [{ id: 1, nombre: 'Desayuno', costo: 15000 }],
  totalServiciosAdicionales: 15000,
  total: 485000,
};

function cotizar() {
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2026-12-10' } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2026-12-14' } });
  fireEvent.click(screen.getByRole('button', { name: 'Cotizar' }));
}

function abrir() {
  render(<CotizadorEstancia publicacionId={5} serviciosAdicionales={ADICIONALES} />);
}

function desglose() {
  return screen.findByLabelText('Desglose de la cotización');
}

describe('CotizadorEstancia', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra noches, precio base, limpieza, tarifa de servicio, descuentos y total', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    abrir();

    cotizar();

    const lista = await desglose();
    expect(cotizarEstancia).toHaveBeenCalledWith({
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionales: [],
    });
    expect(within(lista).getByText('Noches').nextSibling).toHaveTextContent('4');
    expect(within(lista).getByText('Precio base').nextSibling).toHaveTextContent(/400\.000/);
    expect(within(lista).getByText('Tarifa de limpieza').nextSibling).toHaveTextContent(/30\.000/);
    expect(within(lista).getByText('Tarifa de servicio').nextSibling).toHaveTextContent(/40\.000/);
    expect(within(lista).getByText('Descuentos')).toBeInTheDocument();
    expect(within(lista).getByText('Total').nextSibling).toHaveTextContent(/470\.000/);
  });

  it('al agregar un servicio adicional lo recalcula y lo muestra como línea aparte con su costo', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValueOnce(SIN_SERVICIOS).mockResolvedValueOnce(CON_DESAYUNO);
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));

    const lista = await desglose();
    await waitFor(() => expect(within(lista).getByText('Desayuno')).toBeInTheDocument());
    expect(cotizarEstancia).toHaveBeenLastCalledWith(expect.objectContaining({ serviciosAdicionales: [1] }));
    expect(within(lista).getByText('Desayuno').nextSibling).toHaveTextContent(/15\.000/);
    expect(within(lista).getByText('Total').nextSibling).toHaveTextContent(/485\.000/);
  });

  it('al quitar el servicio adicional el total vuelve al anterior', async () => {
    vi.mocked(cotizarEstancia)
      .mockResolvedValueOnce(SIN_SERVICIOS).mockResolvedValueOnce(CON_DESAYUNO).mockResolvedValueOnce(SIN_SERVICIOS);
    abrir();
    cotizar();
    await desglose();
    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));
    await waitFor(() => expect(within(screen.getByLabelText('Desglose de la cotización')).getByText('Desayuno'))
      .toBeInTheDocument());

    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));

    await waitFor(() => expect(within(screen.getByLabelText('Desglose de la cotización')).getByText('Total')
      .nextSibling).toHaveTextContent(/470\.000/));
    expect(cotizarEstancia).toHaveBeenLastCalledWith(expect.objectContaining({ serviciosAdicionales: [] }));
    expect(within(screen.getByLabelText('Desglose de la cotización')).queryByText('Desayuno'))
      .not.toBeInTheDocument();
  });

  it('con las fechas de la búsqueda cotiza sin escribirlas de nuevo', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    render(<CotizadorEstancia publicacionId={5} llegadaInicial="2026-12-10" salidaInicial="2026-12-14" />);

    expect(screen.getByLabelText('Llegada')).toHaveValue('2026-12-10');
    expect(screen.getByLabelText('Salida')).toHaveValue('2026-12-14');
    fireEvent.click(screen.getByRole('button', { name: 'Cotizar' }));

    await desglose();
    expect(cotizarEstancia).toHaveBeenCalledWith({
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionales: [],
    });
  });

  it('sin fechas pide completarlas y no cotiza', () => {
    abrir();

    fireEvent.click(screen.getByRole('button', { name: 'Cotizar' }));

    expect(screen.getByText('Indica la fecha de llegada.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de salida.')).toBeInTheDocument();
    expect(cotizarEstancia).not.toHaveBeenCalled();
  });

  it('si la API rechaza las fechas muestra su mensaje', async () => {
    vi.mocked(cotizarEstancia).mockRejectedValue(
      new ErrorApi(400, 'La fecha de salida debe ser posterior a la de llegada'));
    abrir();

    cotizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha de salida debe ser posterior a la de llegada');
    expect(screen.queryByLabelText('Desglose de la cotización')).not.toBeInTheDocument();
  });

  it('ante otro error muestra un aviso genérico', async () => {
    vi.mocked(cotizarEstancia).mockRejectedValue(new ErrorApi(500, 'Error'));
    abrir();

    cotizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos calcular la cotización');
  });

  it('sin servicios adicionales no ofrece ese bloque', () => {
    render(<CotizadorEstancia publicacionId={5} />);

    expect(screen.queryByText('Servicios adicionales')).not.toBeInTheDocument();
  });
});
