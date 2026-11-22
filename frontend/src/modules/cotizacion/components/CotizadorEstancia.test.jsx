import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { formatearPlazo } from '../../reservas/models/reserva.js';
import { crearReserva } from '../../reservas/services/reservaService.js';
import { cotizarEstancia } from '../services/cotizacionService.js';
import CotizadorEstancia from './CotizadorEstancia.jsx';

vi.mock('../services/cotizacionService.js', () => ({ cotizarEstancia: vi.fn() }));
vi.mock('../../reservas/services/reservaService.js', () => ({ crearReserva: vi.fn() }));

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

function PortalDePrueba() {
  return <p>Portal de pagos de la reserva</p>;
}

function abrir(props = { serviciosAdicionales: ADICIONALES }) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/alojamiento']}>
      <Routes>
        <Route path="/alojamiento" element={<CotizadorEstancia publicacionId={5} {...props} />} />
        <Route path="/huesped/reservas/1/pago" element={<PortalDePrueba />} />
      </Routes>
    </MemoryRouter>,
  );
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
    abrir({ llegadaInicial: '2026-12-10', salidaInicial: '2026-12-14' });

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
    abrir({});

    expect(screen.queryByText('Servicios adicionales')).not.toBeInTheDocument();
  });

  it('confirma la reserva con la publicación, las fechas y los adicionales ya cotizados', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValueOnce(SIN_SERVICIOS).mockResolvedValueOnce(CON_DESAYUNO);
    vi.mocked(crearReserva).mockResolvedValue({ id: 1, estado: 'PENDIENTE', total: 470000 });
    abrir();
    cotizar();
    await desglose();
    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));
    await waitFor(() => expect(within(screen.getByLabelText('Desglose de la cotización')).getByText('Desayuno'))
      .toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    await waitFor(() => expect(crearReserva).toHaveBeenCalledWith({
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionalesIds: [1],
    }, expect.any(String)));
    expect(await screen.findByText('Portal de pagos de la reserva')).toBeInTheDocument();
  });

  it('al reintentar la confirmación tras un error reusa la misma clave de idempotencia (SOF-77)', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValueOnce(new ErrorApi(0, 'Sin conexión'))
      .mockResolvedValueOnce({ id: 1, estado: 'PENDIENTE', total: 470000 });
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    await waitFor(() => expect(crearReserva).toHaveBeenCalledTimes(2));
    const [[, primera], [, segunda]] = vi.mocked(crearReserva).mock.calls;
    expect(primera).toEqual(expect.any(String));
    expect(segunda).toBe(primera);
  });

  it('una nueva cotización confirma con otra clave de idempotencia (SOF-77)', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValueOnce(SIN_SERVICIOS).mockResolvedValueOnce(CON_DESAYUNO);
    vi.mocked(crearReserva).mockRejectedValue(new ErrorApi(500, 'Error'));
    abrir();
    cotizar();
    await desglose();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));
    await waitFor(() => expect(within(screen.getByLabelText('Desglose de la cotización')).getByText('Desayuno'))
      .toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    await waitFor(() => expect(crearReserva).toHaveBeenCalledTimes(2));
    const [[, primera], [, segunda]] = vi.mocked(crearReserva).mock.calls;
    expect(segunda).toEqual(expect.any(String));
    expect(segunda).not.toBe(primera);
  });

  it('con la reserva creada redirige al portal de pagos de esa reserva', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockResolvedValue({ id: 1, estado: 'PENDIENTE', total: 470000 });
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByText('Portal de pagos de la reserva')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
  });

  it('si la reserva queda pendiente de aprobación avisa "Solicitud enviada" y no va al portal de pagos (SOF-79)', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockResolvedValue({
      id: 1, estado: 'PENDIENTE_APROBACION', total: 470000, venceEn: '2026-11-24T15:00:00Z',
    });
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    const aviso = await screen.findByRole('status');
    expect(aviso).toHaveTextContent('Solicitud enviada');
    expect(aviso).toHaveTextContent(formatearPlazo('2026-11-24T15:00:00Z'));
    expect(aviso).not.toHaveTextContent('24 horas');
    expect(screen.getByRole('link', { name: 'Ver mi solicitud' })).toHaveAttribute('href', '/huesped/reservas/1');
    expect(screen.queryByText('Portal de pagos de la reserva')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
  });

  it('tras una solicitud enviada, editar una fecha descarta el aviso', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockResolvedValue({ id: 1, estado: 'PENDIENTE_APROBACION', total: 470000 });
    abrir();
    cotizar();
    await desglose();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));
    await screen.findByText(/Solicitud enviada/);

    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2026-12-15' } });

    expect(screen.queryByText(/Solicitud enviada/)).not.toBeInTheDocument();
  });

  it('si esas fechas ya las reservó y pagó, avisa que ya las reservó y no va al portal de pagos', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockResolvedValue({ id: 1, estado: 'CONFIRMADA', total: 470000 });
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Ya reservaste estas fechas');
    expect(screen.getByRole('link', { name: 'Ver mi reserva' })).toHaveAttribute('href', '/huesped/reservas/1');
    expect(screen.queryByText('Portal de pagos de la reserva')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
  });

  it('si la reserva no se crea no redirige y deja reintentar la confirmación', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValue(new ErrorApi(409, 'Esas fechas ya están reservadas.'));
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Esas fechas ya están reservadas.');
    expect(screen.queryByText('Portal de pagos de la reserva')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar reserva' })).toBeEnabled();
  });

  it('si otro huésped ya reservó esas fechas, muestra el mensaje de conflicto de la API', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValue(
      new ErrorApi(409, 'Esas fechas ya no están disponibles: alguien más reservó primero.'));
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esas fechas ya no están disponibles: alguien más reservó primero.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('si la cotización ya no es vigente, muestra el mensaje de la API y pide recalcular', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValue(
      new ErrorApi(400, 'Esta publicación requiere aprobación del anfitrión; todavía no está soportado.'));
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Esta publicación requiere aprobación del anfitrión; todavía no está soportado.');
  });

  it('ante un error inesperado al reservar muestra un aviso genérico', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValue(new ErrorApi(500, 'Error'));
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos confirmar la reserva');
  });
  it('al editar una fecha después de cotizar descarta la cotización y oculta Confirmar reserva', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    abrir();
    cotizar();
    await desglose();
    expect(screen.getByRole('button', { name: 'Confirmar reserva' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2026-12-20' } });

    expect(screen.queryByLabelText('Desglose de la cotización')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
    expect(crearReserva).not.toHaveBeenCalled();
  });

  it('al editar una fecha limpia el error de la reserva anterior', async () => {
    vi.mocked(cotizarEstancia).mockResolvedValue(SIN_SERVICIOS);
    vi.mocked(crearReserva).mockRejectedValue(new ErrorApi(409, 'Esas fechas ya están reservadas.'));
    abrir();
    cotizar();
    await desglose();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));
    await screen.findByRole('alert');

    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2026-12-11' } });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('al cambiar un servicio no deja confirmar la cotización anterior mientras recalcula y luego envía los nuevos', async () => {
    let resolverRecalculo;
    vi.mocked(cotizarEstancia).mockResolvedValueOnce(SIN_SERVICIOS)
      .mockReturnValueOnce(new Promise((resolver) => { resolverRecalculo = resolver; }));
    vi.mocked(crearReserva).mockResolvedValue({ id: 1, estado: 'PENDIENTE', total: 470000 });
    abrir();
    cotizar();
    await desglose();

    fireEvent.click(screen.getByRole('checkbox', { name: /Desayuno/ }));

    expect(screen.queryByLabelText('Desglose de la cotización')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
    resolverRecalculo(CON_DESAYUNO);
    await desglose();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reserva' }));
    await waitFor(() => expect(crearReserva).toHaveBeenCalledWith({
      publicacionId: 5, llegada: '2026-12-10', salida: '2026-12-14', serviciosAdicionalesIds: [1],
    }, expect.any(String)));
  });

  it('una respuesta tardía de una cotización anterior no reaparece si ya se editaron las fechas', async () => {
    let resolverTardia;
    vi.mocked(cotizarEstancia).mockReturnValueOnce(new Promise((resolver) => { resolverTardia = resolver; }));
    abrir();
    cotizar();

    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2026-12-20' } });
    resolverTardia(SIN_SERVICIOS);

    await waitFor(() => expect(screen.getByRole('button', { name: 'Cotizar' })).toBeEnabled());
    expect(screen.queryByLabelText('Desglose de la cotización')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar reserva' })).not.toBeInTheDocument();
  });
});
