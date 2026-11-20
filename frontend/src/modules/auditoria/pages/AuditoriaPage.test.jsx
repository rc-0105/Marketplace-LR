import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listar } from '../services/auditoriaService.js';
import AuditoriaPage from './AuditoriaPage.jsx';

vi.mock('../services/auditoriaService.js', () => ({ listar: vi.fn() }));

const EVENTO = {
  id: 1, fecha: '2026-11-16T03:30:00Z', usuario: '2', accion: 'RESERVA', entidad: 'Reserva', entidadId: '55',
  resultado: 'EXITO', datos: { operacion: 'CREAR' }, correlacion: 'c-1',
};
const SIN_USUARIO = { ...EVENTO, id: 6, usuario: null, entidadId: null, resultado: 'FALLO', accion: 'AUTENTICACION' };

function pagina(contenido, extra = {}) {
  return { contenido, pagina: 0, tamano: 50, totalElementos: contenido.length, totalPaginas: contenido.length ? 1 : 0, ...extra };
}

function renderizar(ruta = '/') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <AuditoriaPage />
    </MemoryRouter>,
  );
}

describe('AuditoriaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra el estado de carga y luego la tabla de la página 0 con tamaño 50', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO]));

    renderizar();

    expect(screen.getByText('Cargando eventos…')).toBeInTheDocument();
    const tabla = await screen.findByRole('table', { name: 'Eventos de auditoría' });
    expect(listar).toHaveBeenCalledWith({}, 0, 50);
    expect(screen.queryByText('Cargando eventos…')).not.toBeInTheDocument();
    const encabezados = within(tabla).getAllByRole('columnheader').map((columna) => columna.textContent);
    expect(encabezados).toEqual(['Fecha (Bogotá)', 'Usuario', 'Evento', 'Entidad', 'Id de la entidad', 'Resultado']);
    const celdas = within(within(tabla).getAllByRole('row')[1]).getAllByRole('cell').map((celda) => celda.textContent);
    expect(celdas).toEqual(['15/11/2026 22:30:00', '2', 'RESERVA', 'Reserva', '55', 'Éxito']);
  });

  it('muestra los campos nulos y el fallo en lenguaje claro', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([SIN_USUARIO]));

    renderizar();

    const fila = (await screen.findAllByRole('row'))[1];
    expect(within(fila).getAllByRole('cell').map((celda) => celda.textContent))
      .toEqual(['15/11/2026 22:30:00', 'Sin usuario', 'AUTENTICACION', 'Reserva', 'Sin id', 'Fallo']);
  });

  it('con la lista vacía muestra "sin resultados" y no la tabla', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([]));

    renderizar();

    expect(await screen.findByText('Sin resultados: no hay eventos de auditoría con esos criterios.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('ante un 400 muestra el mensaje de la API sin romper la página', async () => {
    vi.mocked(listar).mockRejectedValue(new ErrorApi(400, 'La fecha inicial no puede ser posterior a la final.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha inicial no puede ser posterior a la final.');
    expect(screen.getByRole('heading', { name: 'Auditoría' })).toBeInTheDocument();
    expect(screen.queryByText('Cargando eventos…')).not.toBeInTheDocument();
  });

  it.each([new ErrorApi(500, 'Error interno'), new Error('red')])(
    'ante un fallo del servidor o de red muestra un mensaje genérico', async (error) => {
      vi.mocked(listar).mockRejectedValue(error);

      renderizar();

      expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los eventos de auditoría. Intenta de nuevo.');
    },
  );

  it('al aplicar usuario y evento consulta con esos filtros y vuelve a la página 0', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO]));
    renderizar('/?pagina=2');
    await screen.findByRole('table');
    expect(listar).toHaveBeenLastCalledWith({}, 2, 50);

    fireEvent.change(screen.getByLabelText('Usuario'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Evento'), { target: { value: 'RESERVA' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    await screen.findByRole('table');
    expect(listar).toHaveBeenLastCalledWith({ usuario: '2', evento: 'RESERVA' }, 0, 50);
  });

  it('lee los filtros y la página de la dirección', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO], { pagina: 1, totalPaginas: 3 }));

    renderizar('/?entidad=Reserva&entidadId=55&desde=2026-11-01&hasta=2026-11-30&pagina=1&otro=x');

    await screen.findByRole('table');
    expect(listar).toHaveBeenCalledWith(
      { entidad: 'Reserva', entidadId: '55', desde: '2026-11-01', hasta: '2026-11-30' }, 1, 50);
    expect(screen.getByLabelText('Id de la entidad')).toHaveValue('55');
  });

  it.each(['abc', '-3', '1.5'])('una página inválida (%s) en la dirección se trata como la 0', async (invalida) => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO]));

    renderizar(`/?pagina=${invalida}`);

    await screen.findByRole('table');
    expect(listar).toHaveBeenCalledWith({}, 0, 50);
  });

  it('con fechas incoherentes no llama a la API', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO]));
    renderizar();
    await screen.findByRole('table');
    vi.mocked(listar).mockClear();

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-20' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-11-10' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(screen.getByText('La fecha inicial no puede ser posterior a la final.')).toBeInTheDocument();
    expect(listar).not.toHaveBeenCalled();
  });

  describe('paginación', () => {
    function navegacion() {
      return screen.getByRole('navigation', { name: 'Paginación' });
    }

    it('en la página 0 de 3 deshabilita "Anterior" y "Siguiente" pide la página 1', async () => {
      vi.mocked(listar).mockResolvedValue(pagina([EVENTO], { totalPaginas: 3 }));
      renderizar();
      await screen.findByRole('table');

      expect(within(navegacion()).getByText('Página 1 de 3')).toBeInTheDocument();
      expect(within(navegacion()).getByRole('button', { name: 'Anterior' })).toBeDisabled();
      fireEvent.click(within(navegacion()).getByRole('button', { name: 'Siguiente' }));

      await screen.findByRole('table');
      expect(listar).toHaveBeenLastCalledWith({}, 1, 50);
    });

    it('en la última página deshabilita "Siguiente" y "Anterior" conserva los filtros', async () => {
      vi.mocked(listar).mockResolvedValue(pagina([EVENTO], { pagina: 2, totalPaginas: 3 }));
      renderizar('/?usuario=2&pagina=2');
      await screen.findByRole('table');

      expect(within(navegacion()).getByRole('button', { name: 'Siguiente' })).toBeDisabled();
      fireEvent.click(within(navegacion()).getByRole('button', { name: 'Anterior' }));

      await screen.findByRole('table');
      expect(listar).toHaveBeenLastCalledWith({ usuario: '2' }, 1, 50);
    });

    it('con la lista vacía ambos controles quedan deshabilitados', async () => {
      vi.mocked(listar).mockResolvedValue(pagina([]));
      renderizar();
      await screen.findByText(/^Sin resultados/);

      expect(within(navegacion()).getByRole('button', { name: 'Anterior' })).toBeDisabled();
      expect(within(navegacion()).getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    });
  });
});
