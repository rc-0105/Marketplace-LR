import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { pagarReserva } from '../services/reservaService.js';
import PagoReserva from './PagoReserva.jsx';

vi.mock('../services/reservaService.js', () => ({ pagarReserva: vi.fn() }));

function abrir() {
  render(<PagoReserva reservaId={7} total={470000} />);
}

function metodo(tarjeta) {
  return screen.getAllByRole('radio').find((opcion) => opcion.value === tarjeta);
}

function pagar(tarjeta) {
  if (tarjeta) fireEvent.click(metodo(tarjeta));
  fireEvent.click(screen.getByRole('button', { name: /Pagar/ }));
}

describe('PagoReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('paga con la tarjeta de prueba aprobada por defecto y muestra el pago aceptado sin la transacción', async () => {
    vi.mocked(pagarReserva).mockResolvedValue({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_123' });
    const onAprobado = vi.fn();
    render(<PagoReserva reservaId={7} total={470000} onAprobado={onAprobado} />);

    pagar();

    expect(await screen.findByRole('status')).toHaveTextContent('Pago aceptado: tu reserva está confirmada.');
    expect(screen.queryByText(/pi_123/)).not.toBeInTheDocument();
    expect(onAprobado).toHaveBeenCalled();
    expect(pagarReserva).toHaveBeenCalledWith(7, 'pm_card_visa', expect.any(String));
    expect(screen.queryByRole('button', { name: /Pagar/ })).not.toBeInTheDocument();
  });

  it('ofrece las cuatro tarjetas de prueba como métodos de pago, con la Visa aprobada elegida', () => {
    abrir();

    const opciones = screen.getAllByRole('radio');
    expect(opciones.map((opcion) => opcion.value)).toEqual([
      'pm_card_visa', 'pm_card_mastercard', 'pm_card_chargeDeclined', 'pm_card_chargeDeclinedInsufficientFunds',
    ]);
    expect(screen.getByRole('radio', { name: /Visa terminada en 4242/ })).toBeChecked();
    expect(screen.getByRole('radio', { name: /Visa terminada en 0002.*rechazada/ })).not.toBeChecked();
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
    expect(pagarReserva).toHaveBeenCalledWith(7, 'pm_card_chargeDeclined', expect.any(String));
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

  it.each([504, 502])('tras un %i reintenta con la misma tarjeta y clave', async (estado) => {
    vi.mocked(pagarReserva).mockRejectedValueOnce(new ErrorApi(estado, 'Error de pasarela.'))
      .mockResolvedValueOnce({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_1' });
    abrir();
    pagar('pm_card_mastercard');
    await screen.findByRole('alert');

    expect(metodo('pm_card_visa')).toBeDisabled();
    expect(metodo('pm_card_mastercard')).toBeChecked();
    pagar();

    expect(await screen.findByRole('status')).toHaveTextContent('Pago aceptado');
    const clave = vi.mocked(pagarReserva).mock.calls[0][2];
    expect(clave).toEqual(expect.any(String));
    expect(pagarReserva).toHaveBeenNthCalledWith(2, 7, 'pm_card_mastercard', clave);
  });

  it('envía una clave nueva si cambia la tarjeta tras un error recuperable', async () => {
    vi.mocked(pagarReserva).mockRejectedValueOnce(new ErrorApi(500, 'Error'))
      .mockResolvedValueOnce({ transaccionId: 'pi_2' });
    abrir();
    pagar();
    await screen.findByRole('alert');
    pagar('pm_card_mastercard');
    await screen.findByRole('status');

    expect(pagarReserva).toHaveBeenNthCalledWith(2, 7, 'pm_card_mastercard', expect.any(String));
    const [primera, segunda] = vi.mocked(pagarReserva).mock.calls;
    expect(segunda[2]).not.toBe(primera[2]);
  });

  it('ante un error inesperado muestra un aviso genérico y deja reintentar', async () => {
    vi.mocked(pagarReserva).mockRejectedValue(new ErrorApi(500, 'Error'));
    abrir();

    pagar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos procesar el pago. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: /Pagar/ })).toBeEnabled();
  });

  it('tras el pago aceptado no ofrece cancelar: eso se hace desde el detalle de la reserva', async () => {
    vi.mocked(pagarReserva).mockResolvedValue({ id: 7, estado: 'CONFIRMADA', estadoPago: 'APROBADO', transaccionId: 'pi_1' });
    abrir();

    pagar();
    await screen.findByRole('status');

    expect(screen.queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Motivo de la cancelación')).not.toBeInTheDocument();
  });
});
