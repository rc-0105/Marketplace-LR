import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarReporte, exportarReporte, guardarArchivo } from '../services/reportesService.js';
import ReportesPage from './ReportesPage.jsx';

vi.mock('../services/reportesService.js', () => ({
  consultarReporte: vi.fn(),
  exportarReporte: vi.fn(),
  guardarArchivo: vi.fn(),
}));

const RESERVAS = {
  tipo: 'reservas',
  titulo: 'Reporte de reservas',
  desde: '2026-11-01',
  hasta: '2026-11-30',
  encabezados: ['ID', 'Publicación', 'Ciudad', 'Total'],
  filas: [[7, 'Casa del lago', 'Bogotá', 250000.0], [8, 'Apto centro', null, 120000.0]],
};

function renderizar(ruta = '/') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <ReportesPage />
    </MemoryRouter>,
  );
}

describe('ReportesPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('sin parámetros muestra el reporte de reservas de todo el histórico con sus filas', async () => {
    vi.mocked(consultarReporte).mockResolvedValue({ ...RESERVAS, desde: null, hasta: null });

    renderizar();

    expect(screen.getByText('Cargando reporte…')).toBeInTheDocument();
    const tabla = await screen.findByRole('table', { name: 'Reporte de reservas' });
    expect(consultarReporte).toHaveBeenCalledWith('reservas', {});
    expect(within(tabla).getAllByRole('columnheader').map((celda) => celda.textContent))
      .toEqual(['ID', 'Publicación', 'Ciudad', 'Total']);
    const filas = within(tabla).getAllByRole('row').slice(1);
    expect(filas.map((fila) => within(fila).getAllByRole('cell').map((celda) => celda.textContent)))
      .toEqual([['7', 'Casa del lago', 'Bogotá', '250000'], ['8', 'Apto centro', '', '120000']]);
    expect(screen.getByText('2 filas. Los archivos exportados traen exactamente estas filas.')).toBeInTheDocument();
  });

  it('toma el tipo y el rango de la dirección', async () => {
    vi.mocked(consultarReporte).mockResolvedValue({ ...RESERVAS, tipo: 'usuarios', titulo: 'Reporte de usuarios' });

    renderizar('/?tipo=usuarios&desde=2026-11-01&hasta=2026-11-30');

    expect(await screen.findByRole('table', { name: 'Reporte de usuarios' })).toBeInTheDocument();
    expect(consultarReporte).toHaveBeenCalledWith('usuarios', { desde: '2026-11-01', hasta: '2026-11-30' });
    expect(screen.getByLabelText('Reporte')).toHaveValue('usuarios');
  });

  it('al cambiar de reporte lo consulta con el mismo rango', async () => {
    vi.mocked(consultarReporte).mockResolvedValue(RESERVAS);
    renderizar('/?desde=2026-11-01');
    await screen.findByRole('table');

    fireEvent.change(screen.getByLabelText('Reporte'), { target: { value: 'demanda' } });

    expect(consultarReporte).toHaveBeenLastCalledWith('demanda', { desde: '2026-11-01' });
  });

  it.each([['Exportar a Excel', 'xlsx'], ['Exportar a PDF', 'pdf']])(
    '%s descarga el reporte visible y confirma el archivo', async (boton, formato) => {
      vi.mocked(consultarReporte).mockResolvedValue(RESERVAS);
      const archivo = { contenido: new Blob(['x']), nombre: `reporte-reservas-2026-11-01-a-2026-11-30.${formato}` };
      vi.mocked(exportarReporte).mockResolvedValue(archivo);
      renderizar('/?desde=2026-11-01&hasta=2026-11-30');
      await screen.findByRole('table');

      fireEvent.click(screen.getByRole('button', { name: boton }));

      expect(await screen.findByText(`Descargaste ${archivo.nombre}.`)).toBeInTheDocument();
      expect(exportarReporte).toHaveBeenCalledWith('reservas', formato, { desde: '2026-11-01', hasta: '2026-11-30' });
      expect(guardarArchivo).toHaveBeenCalledWith(archivo);
    },
  );

  it('un reporte sin filas avisa que el archivo saldrá solo con encabezados y deja exportar', async () => {
    vi.mocked(consultarReporte).mockResolvedValue({ ...RESERVAS, filas: [] });

    renderizar();

    expect(await screen.findByText('No hay registros en este rango. El archivo se exporta solo con los encabezados.'))
      .toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exportar a Excel' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Exportar a PDF' })).toBeEnabled();
  });

  it('muestra el error de la API si la exportación falla', async () => {
    vi.mocked(consultarReporte).mockResolvedValue(RESERVAS);
    vi.mocked(exportarReporte).mockRejectedValue(new ErrorApi(500, 'Ocurrió un error inesperado. Intenta de nuevo.'));
    renderizar();
    await screen.findByRole('table');

    fireEvent.click(screen.getByRole('button', { name: 'Exportar a PDF' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos exportar el reporte. Intenta de nuevo.');
    expect(guardarArchivo).not.toHaveBeenCalled();
  });

  it('muestra el mensaje de la API cuando rechaza el rango', async () => {
    vi.mocked(consultarReporte).mockRejectedValue(
      new ErrorApi(400, 'La fecha desde no puede ser posterior a la fecha hasta.'),
    );

    renderizar('/?desde=2026-11-30&hasta=2026-11-01');

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha desde no puede ser posterior a la fecha hasta.');
    expect(screen.queryByRole('button', { name: 'Exportar a Excel' })).not.toBeInTheDocument();
  });
});
