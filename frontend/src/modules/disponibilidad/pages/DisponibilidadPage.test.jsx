import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import {
  consultarCalendario,
  eliminarPeriodo,
  listarPeriodos,
  modificarPeriodo,
  registrarPeriodo,
} from '../services/disponibilidadService.js';
import DisponibilidadPage from './DisponibilidadPage.jsx';

vi.mock('../services/disponibilidadService.js', () => ({
  consultarCalendario: vi.fn(),
  eliminarPeriodo: vi.fn(),
  listarPeriodos: vi.fn(),
  modificarPeriodo: vi.fn(),
  registrarPeriodo: vi.fn(),
}));

const ENERO = { id: 5, publicacionId: 3, fechaInicio: '2027-01-10', fechaFin: '2027-01-15' };
const MARZO = { id: 6, publicacionId: 3, fechaInicio: '2027-03-01', fechaFin: '2027-03-05' };
function calendario(estadoDelPrimerDia = 'DISPONIBLE') {
  return {
    publicacionId: 3,
    desde: '2027-01-01',
    hasta: '2027-01-04',
    tienePeriodos: true,
    dias: [
      { fecha: '2027-01-01', estado: estadoDelPrimerDia },
      { fecha: '2027-01-02', estado: 'RESERVADO', reservaId: 77 },
      { fecha: '2027-01-03', estado: 'RESERVADO' },
    ],
  };
}

const RANGO_ENERO = '10 de enero de 2027 al 15 de enero de 2027';

function renderizar() {
  render(
    <MemoryRouter initialEntries={['/anfitrion/publicaciones/3/disponibilidad']}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/anfitrion/publicaciones/:id/disponibilidad" element={<DisponibilidadPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DisponibilidadPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(consultarCalendario).mockResolvedValue(calendario());
  });

  it('muestra los periodos registrados de la publicación', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO, MARZO]);

    renderizar();

    expect(await screen.findByText(RANGO_ENERO)).toBeInTheDocument();
    expect(screen.getByText('1 de marzo de 2027 al 5 de marzo de 2027')).toBeInTheDocument();
    expect(listarPeriodos).toHaveBeenCalledWith('3');
    expect(screen.getByRole('link', { name: 'Volver a mis publicaciones' }))
      .toHaveAttribute('href', '/anfitrion/publicaciones');
  });

  it('indica cuando todavía no hay periodos', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([]);

    renderizar();

    expect(await screen.findByText('Todavía no hay periodos. Agrega el primero para empezar a recibir reservas.'))
      .toBeInTheDocument();
  });

  it('muestra el motivo cuando la API no deja ver la disponibilidad', async () => {
    vi.mocked(listarPeriodos).mockRejectedValue(new ErrorApi(403, 'No tienes permiso para esta acción.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso para esta acción.');
    expect(screen.queryByRole('form', { name: 'Nuevo periodo' })).not.toBeInTheDocument();
  });

  it('agrega un periodo nuevo en su lugar de la lista', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([MARZO]);
    vi.mocked(registrarPeriodo).mockResolvedValue(ENERO);
    renderizar();
    const formulario = await screen.findByRole('form', { name: 'Nuevo periodo' });

    fireEvent.change(within(formulario).getByLabelText('Fecha inicial'), { target: { value: '2027-01-10' } });
    fireEvent.change(within(formulario).getByLabelText('Fecha final'), { target: { value: '2027-01-15' } });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Agregar periodo' }));

    expect(await screen.findByText(RANGO_ENERO)).toBeInTheDocument();
    expect(registrarPeriodo).toHaveBeenCalledWith('3', { fechaInicio: '2027-01-10', fechaFin: '2027-01-15' });
    const rangos = screen.getAllByRole('listitem').map((item) => item.textContent);
    expect(rangos[0]).toContain('10 de enero de 2027');
    expect(rangos[1]).toContain('1 de marzo de 2027');
  });

  it('modifica las fechas de un periodo sin reservas', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    vi.mocked(modificarPeriodo).mockResolvedValue({ ...ENERO, fechaFin: '2027-01-20' });
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Editar periodo del ${RANGO_ENERO}` }));
    const formulario = screen.getByRole('form', { name: `Editar periodo del ${RANGO_ENERO}` });
    fireEvent.change(within(formulario).getByLabelText('Fecha final'), { target: { value: '2027-01-20' } });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('10 de enero de 2027 al 20 de enero de 2027')).toBeInTheDocument();
    expect(modificarPeriodo).toHaveBeenCalledWith(5, { fechaInicio: '2027-01-10', fechaFin: '2027-01-20' });
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
  });

  it('cancelar la edición deja el periodo como estaba', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Editar periodo del ${RANGO_ENERO}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.getByText(RANGO_ENERO)).toBeInTheDocument();
    expect(modificarPeriodo).not.toHaveBeenCalled();
  });

  it('si una reserva confirmada bloquea la modificación, muestra cuál es', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    vi.mocked(modificarPeriodo).mockRejectedValue(
      new ErrorApi(409, 'El periodo tiene la reserva 12 confirmada; no se puede modificar ni eliminar.'));
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Editar periodo del ${RANGO_ENERO}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('El periodo tiene la reserva 12 confirmada; no se puede modificar ni eliminar.');
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
  });

  it('pide confirmación antes de eliminar y no borra si el anfitrión se arrepiente', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));

    expect(screen.getByText(`¿Eliminar el periodo del ${RANGO_ENERO}?`)).toBeInTheDocument();
    expect(eliminarPeriodo).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'No, conservarlo' }));

    expect(screen.queryByRole('button', { name: 'Sí, eliminar' })).not.toBeInTheDocument();
    expect(screen.getByText(RANGO_ENERO)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` })).toBeInTheDocument();
    expect(eliminarPeriodo).not.toHaveBeenCalled();
  });

  it('elimina un periodo sin reservas', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO, MARZO]);
    vi.mocked(eliminarPeriodo).mockResolvedValue(undefined);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    await waitFor(() => expect(screen.queryByText(RANGO_ENERO)).not.toBeInTheDocument());
    expect(eliminarPeriodo).toHaveBeenCalledWith(5);
    expect(screen.getByText('1 de marzo de 2027 al 5 de marzo de 2027')).toBeInTheDocument();
  });

  it('si una reserva confirmada bloquea la eliminación, conserva el periodo e indica la reserva', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    vi.mocked(eliminarPeriodo).mockRejectedValue(
      new ErrorApi(409, 'El periodo tiene la reserva 12 confirmada; no se puede modificar ni eliminar.'));
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('reserva 12');
    expect(screen.getByText(RANGO_ENERO)).toBeInTheDocument();
  });

  it('avisa con un mensaje genérico si no puede eliminar por falta de conexión', async () => {
    vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
    vi.mocked(eliminarPeriodo).mockRejectedValue(new Error('caída'));
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));
    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos eliminar el periodo. Intenta de nuevo.');
  });

  it('avisa con un mensaje genérico si no puede cargar por falta de conexión', async () => {
    vi.mocked(listarPeriodos).mockRejectedValue(new Error('caída'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la disponibilidad. Intenta de nuevo.');
  });

  describe('calendario', () => {
    it('muestra el calendario de la publicación con el número de reserva que entrega la API', async () => {
      vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);

      renderizar();

      expect(await screen.findByRole('heading', { name: 'Calendario' })).toBeInTheDocument();
      expect(await screen.findByRole('cell', { name: /^2 de enero de 2027.*Reservado.*Reserva 77/ })).toBeInTheDocument();
      expect(screen.getByRole('cell', { name: /^3 de enero de 2027.*Reservado/ }).getAttribute('aria-label'))
        .not.toMatch(/Reserva \d/);
      expect(consultarCalendario).toHaveBeenCalledWith('3');
      expect(screen.getByText(RANGO_ENERO)).toBeInTheDocument();
    });

    it('recarga el calendario después de agregar un periodo', async () => {
      vi.mocked(listarPeriodos).mockResolvedValue([MARZO]);
      vi.mocked(registrarPeriodo).mockResolvedValue(ENERO);
      vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario()).mockResolvedValueOnce(calendario('BLOQUEADO'));
      renderizar();
      const formulario = await screen.findByRole('form', { name: 'Nuevo periodo' });

      fireEvent.change(within(formulario).getByLabelText('Fecha inicial'), { target: { value: '2027-01-10' } });
      fireEvent.change(within(formulario).getByLabelText('Fecha final'), { target: { value: '2027-01-15' } });
      fireEvent.click(within(formulario).getByRole('button', { name: 'Agregar periodo' }));

      expect(await screen.findByRole('cell', { name: /^1 de enero de 2027.*Bloqueado/ })).toBeInTheDocument();
      expect(consultarCalendario).toHaveBeenCalledTimes(2);
    });

    it('recarga el calendario después de modificar un periodo', async () => {
      vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
      vi.mocked(modificarPeriodo).mockResolvedValue({ ...ENERO, fechaFin: '2027-01-20' });
      vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario()).mockResolvedValueOnce(calendario('BLOQUEADO'));
      renderizar();

      fireEvent.click(await screen.findByRole('button', { name: `Editar periodo del ${RANGO_ENERO}` }));
      fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

      expect(await screen.findByRole('cell', { name: /^1 de enero de 2027.*Bloqueado/ })).toBeInTheDocument();
      expect(consultarCalendario).toHaveBeenCalledTimes(2);
    });

    it('recarga el calendario después de eliminar un periodo', async () => {
      vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
      vi.mocked(eliminarPeriodo).mockResolvedValue(undefined);
      vi.mocked(consultarCalendario).mockResolvedValueOnce(calendario()).mockResolvedValueOnce(calendario('BLOQUEADO'));
      renderizar();

      fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));
      fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

      expect(await screen.findByRole('cell', { name: /^1 de enero de 2027.*Bloqueado/ })).toBeInTheDocument();
      expect(consultarCalendario).toHaveBeenCalledTimes(2);
    });

    it('no recarga el calendario si la API rechaza el cambio', async () => {
      vi.mocked(listarPeriodos).mockResolvedValue([ENERO]);
      vi.mocked(eliminarPeriodo).mockRejectedValue(new ErrorApi(409, 'El periodo tiene la reserva 12 confirmada.'));
      renderizar();

      fireEvent.click(await screen.findByRole('button', { name: `Eliminar periodo del ${RANGO_ENERO}` }));
      fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('reserva 12');
      expect(consultarCalendario).toHaveBeenCalledTimes(1);
    });
  });
});
