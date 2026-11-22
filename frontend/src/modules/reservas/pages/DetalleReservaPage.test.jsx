import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { formatearFecha, formatearPesos } from '../models/reserva.js';

// Intl separa el signo del número con un espacio duro; Testing Library normaliza el DOM pero no lo esperado.
const pesos = (valor) => formatearPesos(valor).replace(/\s/g, ' ');
import { cancelarReserva, consultarReserva } from '../services/reservaService.js';
import DetalleReservaPage from './DetalleReservaPage.jsx';

vi.mock('../services/reservaService.js', () => ({ consultarReserva: vi.fn(), cancelarReserva: vi.fn(), anticiparCancelacion: async () => ({ porcentaje: 100, base: 485000, valorDevolucion: 485000 }) }));

function renderizar(id = '3') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      initialEntries={[`/huesped/reservas/${id}`]}>
      <Routes>
        <Route path="/huesped/reservas/:id" element={<DetalleReservaPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function detalle(datos) {
  return {
    id: 3, alojamiento: { id: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null },
    llegada: '2026-12-10', salida: '2026-12-14', estado: 'CONFIRMADA', venceEn: '2026-11-20T15:00:00Z',
    noches: 4, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 20000,
    serviciosAdicionales: [{ id: 1, nombre: 'Desayuno' }, { id: 2, nombre: null }],
    totalServiciosAdicionales: 35000, total: 485000, estadoPago: 'APROBADO', transaccionId: 'sim_abc', ...datos,
  };
}

function filaDe(desglose, etiqueta) {
  return within(desglose).getByText(etiqueta).closest('div');
}

describe('DetalleReservaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra el alojamiento, las fechas y el estado actual de la reserva', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar('3');

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(consultarReserva).toHaveBeenCalledWith('3');
    expect(screen.getByText('Santa Marta')).toBeInTheDocument();
    expect(screen.getByText('Confirmada')).toBeInTheDocument();
    expect(screen.getByText(`${formatearFecha('2026-12-10')} – ${formatearFecha('2026-12-14')} · 4 noches`))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a mis reservas' })).toHaveAttribute('href', '/huesped/reservas');
  });

  it('muestra el desglose del pago que quedó fijo al reservar', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    const desglose = await screen.findByLabelText('Desglose del pago');
    expect(filaDe(desglose, 'Noches')).toHaveTextContent('4');
    expect(filaDe(desglose, 'Precio base')).toHaveTextContent(pesos(400000));
    expect(filaDe(desglose, 'Tarifa de limpieza')).toHaveTextContent(pesos(30000));
    expect(filaDe(desglose, 'Tarifa de servicio')).toHaveTextContent(pesos(40000));
    expect(filaDe(desglose, 'Servicios adicionales')).toHaveTextContent(pesos(35000));
    expect(filaDe(desglose, 'Descuentos')).toHaveTextContent(pesos(20000));
    expect(filaDe(desglose, 'Total')).toHaveTextContent(pesos(485000));
  });

  it('lista los servicios adicionales por nombre y avisa si uno ya no está disponible', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    const servicios = await screen.findByRole('list', { name: 'Servicios adicionales incluidos' });
    expect(within(servicios).getAllByRole('listitem').map((item) => item.textContent))
      .toEqual(['Desayuno', 'Servicio no disponible']);
  });

  it('muestra el pago como aceptado y nunca el identificador de la transacción', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    expect(await screen.findByText('Aceptado')).toBeInTheDocument();
    expect(screen.queryByText('sim_abc')).not.toBeInTheDocument();
    expect(screen.queryByText('Transacción')).not.toBeInTheDocument();
  });

  it('sin servicios adicionales ni transacción no muestra esas partes', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({
      estado: 'VENCIDA', estadoPago: 'SIN_PAGO', transaccionId: null, serviciosAdicionales: [],
      totalServiciosAdicionales: 0,
    }));

    renderizar();

    expect(await screen.findByText('Vencida')).toBeInTheDocument();
    expect(screen.getByText('Sin pago')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Servicios adicionales incluidos' })).not.toBeInTheDocument();
    expect(screen.queryByText('Transacción')).not.toBeInTheDocument();
  });

  it('con la reserva COMPLETADA ofrece reseñar la estancia (SOF-80)', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({ estado: 'COMPLETADA' }));

    renderizar();

    expect(await screen.findByText('Completada')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Reseñar la estancia' })).toBeInTheDocument();
  });

  it.each(['PENDIENTE', 'CONFIRMADA', 'CANCELADA', 'VENCIDA'])(
    'con la reserva %s no ofrece reseñar: la estancia no terminó o no ocurrió', async (estado) => {
      vi.mocked(consultarReserva).mockResolvedValue(detalle({ estado }));

      renderizar();

      expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
      expect(screen.queryByRole('form', { name: 'Reseñar la estancia' })).not.toBeInTheDocument();
    },
  );

  it('si el alojamiento ya no existe lo indica', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({ alojamiento: null }));

    renderizar();

    expect(await screen.findByRole('heading', { name: 'Alojamiento no disponible' })).toBeInTheDocument();
  });

  it('si la API la rechaza muestra su mensaje, y si falla otra cosa un mensaje general', async () => {
    vi.mocked(consultarReserva).mockRejectedValueOnce(new ErrorApi(404, 'No encontramos esa reserva.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No encontramos esa reserva.');
    expect(screen.getByRole('link', { name: 'Volver a mis reservas' })).toBeInTheDocument();
  });

  it('si falla sin respuesta de la API muestra un mensaje general', async () => {
    vi.mocked(consultarReserva).mockRejectedValueOnce(new Error('caída'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la reserva. Intenta de nuevo.');
  });

  it('una reserva pendiente ofrece ir al portal de pagos y una confirmada no', async () => {
    vi.mocked(consultarReserva).mockResolvedValueOnce(detalle({ estado: 'PENDIENTE', estadoPago: 'SIN_PAGO' }));
    renderizar();

    expect(await screen.findByRole('link', { name: 'Ir al portal de pagos' }))
      .toHaveAttribute('href', '/huesped/reservas/3/pago');
  });

  it('una reserva confirmada no ofrece ir al portal de pagos', async () => {
    vi.mocked(consultarReserva).mockResolvedValueOnce(detalle({}));
    renderizar();

    await screen.findByRole('heading', { name: 'Casa en la playa' });
    expect(screen.queryByRole('link', { name: 'Ir al portal de pagos' })).not.toBeInTheDocument();
  });

  it('una reserva confirmada ofrece cancelarla y, al cancelar, la muestra cancelada (SOF-75)', async () => {
    vi.mocked(consultarReserva).mockResolvedValueOnce(detalle())
      .mockResolvedValueOnce(detalle({ estado: 'CANCELADA', estadoPago: 'REEMBOLSADO' }));
    vi.mocked(cancelarReserva).mockResolvedValue({ reservaId: 3, estado: 'CANCELADA', valorDevolucion: 485000 });
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar reserva' }));
    fireEvent.change(screen.getByLabelText('Motivo de la cancelación'), { target: { value: 'Cambio de planes' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ver devolución' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar cancelación' }));

    expect(await screen.findByText(/Reserva cancelada/)).toHaveTextContent(/485\.000/);
    expect(cancelarReserva).toHaveBeenCalledWith(3, 'Cambio de planes');
    expect(await screen.findByText('Reembolsado')).toBeInTheDocument();
    expect(screen.getByText('Cancelada')).toBeInTheDocument();
  });

  it.each(['PENDIENTE', 'CANCELADA', 'VENCIDA', 'COMPLETADA'])('una reserva %s no ofrece cancelarla', async (estado) => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({ estado }));

    renderizar();

    await screen.findByRole('heading', { name: 'Casa en la playa' });
    expect(screen.queryByRole('button', { name: 'Cancelar reserva' })).not.toBeInTheDocument();
  });
});
