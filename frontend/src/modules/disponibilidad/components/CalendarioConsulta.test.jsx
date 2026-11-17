import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarCalendario } from '../services/disponibilidadService.js';
import CalendarioConsulta from './CalendarioConsulta.jsx';

vi.mock('../services/disponibilidadService.js', () => ({ consultarCalendario: vi.fn() }));

function calendario(estado = 'DISPONIBLE') {
  return {
    publicacionId: 3,
    desde: '2026-12-30',
    hasta: '2027-01-03',
    tienePeriodos: true,
    dias: ['2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02'].map((fecha) => ({ fecha, estado })),
  };
}

describe('CalendarioConsulta', () => {
  beforeEach(() => vi.resetAllMocks());

  it('vuelve a pedir el calendario cuando cambia la señal de recarga y conserva el mes mostrado', async () => {
    vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario()).mockResolvedValueOnce(calendario('BLOQUEADO'));
    const { rerender } = render(<CalendarioConsulta publicacionId="3" recarga={0} />);
    await screen.findByText('diciembre de 2026');
    fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
    expect(screen.getByRole('cell', { name: /^1 de enero de 2027.*Disponible/ })).toBeInTheDocument();

    rerender(<CalendarioConsulta publicacionId="3" recarga={1} />);

    expect(await screen.findByRole('cell', { name: /^1 de enero de 2027.*Bloqueado/ })).toBeInTheDocument();
    expect(consultarCalendario).toHaveBeenCalledTimes(2);
    expect(consultarCalendario).toHaveBeenCalledWith('3');
  });

  it('si la recarga falla, muestra el error con la opción de reintentar', async () => {
    vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario())
      .mockRejectedValueOnce(new ErrorApi(500, 'Fallo interno'));
    const { rerender } = render(<CalendarioConsulta publicacionId="3" recarga={0} />);
    await screen.findByRole('table');

    rerender(<CalendarioConsulta publicacionId="3" recarga={1} />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario());
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
  });
});
