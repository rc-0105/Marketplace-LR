import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { cancelarReserva } from '../services/reservaService.js';
import CancelarReserva from './CancelarReserva.jsx';

vi.mock('../services/reservaService.js', () => ({ cancelarReserva: vi.fn() }));

function abrir(onCancelada = vi.fn()) {
  render(<CancelarReserva reservaId={7} onCancelada={onCancelada} />);
  fireEvent.click(screen.getByRole('button', { name: 'Cancelar reserva' }));
  return onCancelada;
}

function confirmar(motivo) {
  fireEvent.change(screen.getByLabelText('Motivo de la cancelación'), { target: { value: motivo } });
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));
}

describe('CancelarReserva', () => {
  beforeEach(() => vi.resetAllMocks());

  it('primero muestra solo el botón Cancelar reserva', () => {
    render(<CancelarReserva reservaId={7} />);

    expect(screen.getByRole('button', { name: 'Cancelar reserva' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Motivo de la cancelación')).not.toBeInTheDocument();
  });

  it('al pulsarlo pide el motivo y no deja confirmar sin él', () => {
    abrir();

    expect(screen.getByLabelText('Motivo de la cancelación')).toHaveAttribute('maxLength', '500');
    expect(screen.getByRole('button', { name: 'Confirmar cancelación' })).toBeDisabled();
  });

  it('No cancelar cierra el formulario sin llamar a la API', () => {
    abrir();

    fireEvent.click(screen.getByRole('button', { name: 'No cancelar' }));

    expect(screen.getByRole('button', { name: 'Cancelar reserva' })).toBeInTheDocument();
    expect(cancelarReserva).not.toHaveBeenCalled();
  });

  it('cancela con el motivo, muestra la devolución y avisa a la página', async () => {
    const resultado = { reservaId: 7, estado: 'CANCELADA', motivo: 'Cambio de planes', valorDevolucion: 470000 };
    vi.mocked(cancelarReserva).mockResolvedValue(resultado);
    const onCancelada = abrir();

    confirmar('Cambio de planes');

    expect(await screen.findByText(/Reserva cancelada/)).toHaveTextContent(/470\.000/);
    expect(cancelarReserva).toHaveBeenCalledWith(7, 'Cambio de planes');
    expect(onCancelada).toHaveBeenCalledWith(resultado);
    expect(screen.queryByRole('button', { name: /Cancelar/ })).not.toBeInTheDocument();
  });

  it('si la API la rechaza (409) muestra su mensaje y deja reintentar', async () => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(409, 'Esta reserva ya está cancelada.'));
    abrir();

    confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta reserva ya está cancelada.');
    expect(screen.getByRole('button', { name: 'Confirmar cancelación' })).toBeEnabled();
  });

  it('si no tiene permiso muestra el mensaje de la API (403)', async () => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(403, 'No tienes permiso para realizar esta acción.'));
    abrir();

    confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para realizar esta acción.');
  });

  it.each([500, 503])('ante un %i muestra el aviso genérico, no el mensaje técnico', async (estado) => {
    vi.mocked(cancelarReserva).mockRejectedValue(new ErrorApi(estado, 'upstream connect error'));
    abrir();

    confirmar('Cambio de planes');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cancelar la reserva. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Confirmar cancelación' })).toBeEnabled();
  });
});
