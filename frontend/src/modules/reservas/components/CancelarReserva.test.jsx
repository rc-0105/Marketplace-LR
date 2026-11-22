import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { anticiparCancelacion, cancelarReserva } from '../services/reservaService.js';
import CancelarReserva from './CancelarReserva.jsx';

vi.mock('../services/reservaService.js', () => ({ anticiparCancelacion: vi.fn(), cancelarReserva: vi.fn() }));

function abrir(onCancelada = vi.fn()) {
  render(<CancelarReserva reservaId={7} onCancelada={onCancelada} />);
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar reserva' }));
  return onCancelada;
}

async function confirmar(motivo) {
  fireEvent.change(screen.getByLabelText('Motivo de la cancelación'), { target: { value: motivo } });
  fireEvent.click(screen.getByRole('button', { name: 'Ver devolución' }));
  expect(await screen.findByText(/Devolución estimada/)).toHaveTextContent(/50%/);
  expect(cancelarReserva).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));
}

describe('CancelarReserva', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    anticiparCancelacion.mockResolvedValue({ politica: 'Flexible', porcentaje: 50, base: 940000, valorDevolucion: 470000 });
  });

  it('informa la revisión manual sin anunciar una devolución exitosa', async () => {
    cancelarReserva.mockResolvedValue({ resultadoReembolso: 'REVISION_MANUAL' });
    abrir();
    await confirmar('Cambio');
    expect(await screen.findByRole('status')).toHaveTextContent(/revisión manual/);
  });

  it('primero muestra solo el botón Cancelar reserva', () => {
    render(<CancelarReserva reservaId={7} />);

    expect(screen.getByRole('button', { name: 'Cancelar reserva' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Motivo de la cancelación')).not.toBeInTheDocument();
  });

  it('al pulsarlo pide el motivo y no deja confirmar sin él', () => {
    abrir();

    expect(screen.getByLabelText('Motivo de la cancelación')).toHaveAttribute('maxLength', '500');
    expect(screen.getByRole('button', { name: 'Ver devolución' })).toBeDisabled();
  });

  it('No cancelar cierra el formulario sin llamar a la API', () => {
    abrir();

    fireEvent.click(screen.getByRole('button', { name: 'No cancelar' }));

    expect(screen.getByRole('button', { name: 'Cancelar reserva' })).toBeInTheDocument();
    expect(cancelarReserva).not.toHaveBeenCalled();
  });

  it('al cancelar una solicitud aún sin pago (devolución 0) no promete devolución (SOF-79)', async () => {
    vi.mocked(cancelarReserva).mockResolvedValue(
      { reservaId: 7, estado: 'CANCELADA', motivo: 'Ya no viajo', valorDevolucion: 0, resultadoReembolso: 'NO_APLICA' });
    abrir();

    confirmar('Ya no viajo');

    const aviso = await screen.findByRole('status');
    expect(aviso).toHaveTextContent('no corresponde devolución');
    expect(aviso).not.toHaveTextContent('simulada');
  });

  it('cancela con el motivo, muestra la devolución y avisa a la página', async () => {
    const resultado = { reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: 470000 };
    vi.mocked(cancelarReserva).mockResolvedValue(resultado);
    const onCancelada = abrir();

    await confirmar('Cambio de planes');

    expect(await screen.findByText(/Reserva cancelada/)).toHaveTextContent(/470\.000/);
    expect(cancelarReserva).toHaveBeenCalledWith(7, 'Cambio de planes');
    expect(onCancelada).toHaveBeenCalledWith(resultado);
    expect(screen.queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
  });

  it('si la API la rechaza (409) muestra su mensaje y deja reintentar', async () => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(409, 'Esta reserva ya está cancelada.'));
    abrir();

    await confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta reserva ya está cancelada.');
    expect(screen.getByRole('button', { name: 'Confirmar cancelación' })).toBeEnabled();
  });

  it('si no tiene permiso muestra el mensaje de la API (403)', async () => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(403, 'No tienes permiso para realizar esta acción.'));
    abrir();

    await confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para realizar esta acción.');
  });

  it.each([500, 503])('ante un %i muestra el aviso genérico, no el mensaje técnico', async (estado) => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(estado, 'upstream connect error'));
    abrir();

    await confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cancelar la reserva. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Confirmar cancelación' })).toBeEnabled();
  });
});
