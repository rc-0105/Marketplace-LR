import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { cancelarReserva, pagarReserva } from '../services/reservaService.js';
import PagoReserva from './PagoReserva.jsx';

vi.mock('../services/reservaService.js', () => ({ pagarReserva: vi.fn(), cancelarReserva: vi.fn() }));

function abrir() {
  render(<PagoReserva reservaId={7} total={470000} />);
}

function pagar(tarjeta) {
  if (tarjeta) fireEvent.change(screen.getByLabelText('Tarjeta de prueba'), { target: { value: tarjeta } });
  fireEvent.click(screen.getByRole('button', { name: /Pagar/ }));
}

async function pagarAprobado() {
  vi.mocked(pagarReserva).mockResolvedValue({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123' });
  abrir();
  pagar();
  await screen.findByRole('status');
}

function cancelar(motivo) {
  fireEvent.change(screen.getByLabelText('Motivo de la cancelación'), { target: { value: motivo } });
  fireEvent.click(screen.getByRole('button', { name: /Cancelar reserva/ }));
}

describe('PagoReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('paga con la tarjeta de prueba aprobada por defecto y muestra la transacción', async () => {
    vi.mocked(pagarReserva).mockResolvedValue({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123' });
    abrir();

    pagar();

    expect(await screen.findByRole('status'))
      .toHaveTextContent('Pago aprobado: tu reserva está confirmada. Transacción pi_123.');
    expect(pagarReserva).toHaveBeenCalledWith(7, 'pm_card_visa');
    expect(screen.queryByRole('button', { name: /Pagar/ })).not.toBeInTheDocument();
  });

  it('muestra el total a pagar en el botón', () => {
    abrir();

    expect(screen.getByRole('button', { name: /Pagar/ })).toHaveTextContent(/470\.000/);
  });

  it('con la tarjeta de rechazo muestra el mensaje de la pasarela y ya no ofrece pagar', async () => {
    vi.mocked(pagarReserva).mockRejectedValue(new ErrorApi(402,
      'La pasarela rechazó el pago: la reserva no se confirmó y las fechas quedaron libres.'));
    abrir();

    pagar('pm_card_chargeDeclined');

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('La pasarela rechazó el pago: la reserva no se confirmó y las fechas quedaron libres.');
    expect(pagarReserva).toHaveBeenCalledWith(7, 'pm_card_chargeDeclined');
    expect(screen.queryByRole('button', { name: /Pagar/ })).not.toBeInTheDocument();
  });

  it('si la pasarela no respondió a tiempo muestra el aviso y deja reintentar', async () => {
    vi.mocked(pagarReserva).mockRejectedValue(new ErrorApi(504,
      'La pasarela de pagos no respondió a tiempo. Tu reserva sigue apartada hasta que venza el apartado: intenta pagar de nuevo.'));
    abrir();

    pagar();

    expect(await screen.findByRole('alert')).toHaveTextContent('La pasarela de pagos no respondió a tiempo.');
    expect(screen.getByRole('button', { name: /Pagar/ })).toBeEnabled();
  });

  it('tras un tiempo agotado deja fija la tarjeta: el reintento va con la misma para no abrir otro cobro', async () => {
    vi.mocked(pagarReserva).mockRejectedValueOnce(new ErrorApi(504, 'La pasarela de pagos no respondió a tiempo.'))
      .mockResolvedValueOnce({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_1' });
    abrir();
    pagar('pm_card_mastercard');
    await screen.findByRole('alert');

    expect(screen.getByLabelText('Tarjeta de prueba')).toBeDisabled();
    pagar();

    expect(await screen.findByRole('status')).toHaveTextContent('Transacción pi_1.');
    expect(pagarReserva).toHaveBeenNthCalledWith(2, 7, 'pm_card_mastercard');
  });

  it('ante un error inesperado muestra un aviso genérico y deja reintentar', async () => {
    vi.mocked(pagarReserva).mockRejectedValue(new ErrorApi(500, 'Error'));
    abrir();

    pagar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos procesar el pago. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: /Pagar/ })).toBeEnabled();
  });

  it('antes de pagar no ofrece cancelar la reserva', () => {
    abrir();

    expect(screen.queryByRole('button', { name: /Cancelar reserva/ })).not.toBeInTheDocument();
  });

  it('tras un pago aprobado ofrece cancelar la reserva con un motivo (SOF-75)', async () => {
    await pagarAprobado();

    expect(screen.getByRole('button', { name: /Cancelar reserva/ })).toBeInTheDocument();
    expect(screen.getByLabelText('Motivo de la cancelación')).toBeInTheDocument();
  });

  it('cancela la reserva con el motivo indicado y muestra la devolución', async () => {
    await pagarAprobado();
    vi.mocked(cancelarReserva).mockResolvedValue({
      reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: 470000,
      fecha: '2026-12-01T15:00:00Z',
    });

    cancelar('Cambio de planes');

    expect(await screen.findByText(/Reserva cancelada/)).toHaveTextContent(/470\.000/);
    expect(cancelarReserva).toHaveBeenCalledWith(7, 'Cambio de planes');
    expect(screen.queryByRole('button', { name: /Cancelar reserva/ })).not.toBeInTheDocument();
  });

  it('si la reserva ya no se puede cancelar muestra el mensaje de la API (409)', async () => {
    await pagarAprobado();
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(409, 'Esta reserva ya está cancelada.'));

    cancelar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta reserva ya está cancelada.');
    expect(screen.getByRole('button', { name: /Cancelar reserva/ })).toBeEnabled();
  });

  it('si el usuario no es el huésped ni el anfitrión muestra el mensaje de permiso (403)', async () => {
    await pagarAprobado();
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(403, 'No tienes permiso para realizar esta acción.'));

    cancelar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para realizar esta acción.');
  });

  it('ante un error inesperado al cancelar muestra un aviso genérico y deja reintentar', async () => {
    await pagarAprobado();
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(500, 'Error'));

    cancelar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cancelar la reserva. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: /Cancelar reserva/ })).toBeEnabled();
  });
});
