import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarReserva, pagarReserva } from '../services/reservaService.js';
import PortalPagoPage from './PortalPagoPage.jsx';

vi.mock('../services/reservaService.js', () => ({
  consultarReserva: vi.fn(), pagarReserva: vi.fn(), cancelarReserva: vi.fn(),
}));

function renderizar(id = '3') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      initialEntries={[`/huesped/reservas/${id}/pago`]}>
      <Routes>
        <Route path="/huesped/reservas/:id/pago" element={<PortalPagoPage />} />
        <Route path="/huesped/reservas/:id" element={<p>Detalle de la reserva</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function reserva(datos) {
  return {
    id: 3, alojamiento: { id: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null },
    llegada: '2026-12-10', salida: '2026-12-14', estado: 'PENDIENTE', venceEn: '2026-11-21T20:30:00Z',
    noches: 4, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 0,
    serviciosAdicionales: [], totalServiciosAdicionales: 0, total: 470000, estadoPago: 'SIN_PAGO',
    transaccionId: null, ...datos,
  };
}

describe('PortalPagoPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra el resumen de la reserva pendiente y las opciones de pago', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(reserva({}));

    renderizar();

    expect(await screen.findByRole('heading', { name: 'Portal de pagos' })).toBeInTheDocument();
    expect(consultarReserva).toHaveBeenCalledWith('3');
    expect(screen.getByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(within(screen.getByLabelText('Resumen del pago')).getByText('Total a pagar').nextSibling)
      .toHaveTextContent(/470\.000/);
    expect(screen.getAllByRole('radio')).toHaveLength(4);
    expect(screen.getByRole('button', { name: /Pagar/ })).toHaveTextContent(/470\.000/);
    // 20:30 UTC son las 3:30 p. m. en Colombia.
    expect(screen.getByText(/apartadas hasta las 3:30/)).toBeInTheDocument();
  });

  it('paga con el método elegido y confirma la reserva', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(reserva({}));
    vi.mocked(pagarReserva).mockResolvedValue({ id: 3, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'sim_1' });
    renderizar();

    fireEvent.click(await screen.findByRole('radio', { name: /Mastercard terminada en 4444/ }));
    fireEvent.click(screen.getByRole('button', { name: /Pagar/ }));

    expect(await screen.findByText(/Pago aceptado: tu reserva está confirmada/)).toBeInTheDocument();
    expect(pagarReserva).toHaveBeenCalledWith(3, 'pm_card_mastercard', expect.any(String));
  });

  it('si la reserva ya no está pendiente no ofrece pagarla y enlaza a su detalle', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(reserva({ estado: 'CONFIRMADA', estadoPago: 'APROBADO' }));

    renderizar();

    expect(await screen.findByRole('status')).toHaveTextContent('Esta reserva ya no está pendiente de pago');
    expect(screen.queryByRole('button', { name: /Pagar/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Ver mi reserva' }));
    expect(screen.getByText('Detalle de la reserva')).toBeInTheDocument();
  });

  it('si la reserva no se puede consultar muestra el mensaje de la API', async () => {
    vi.mocked(consultarReserva).mockRejectedValue(new ErrorApi(404, 'No encontramos esa reserva.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No encontramos esa reserva.');
    expect(screen.queryByRole('button', { name: /Pagar/ })).not.toBeInTheDocument();
  });
});
