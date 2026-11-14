import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listarServicios, listarTiposAlojamiento } from '../../alojamientos/services/alojamientosService.js';
import { buscarAlojamientos } from '../services/busquedaService.js';
import BusquedaPage from './BusquedaPage.jsx';

vi.mock('../services/busquedaService.js', () => ({ buscarAlojamientos: vi.fn() }));
vi.mock('../../alojamientos/services/alojamientosService.js', () => ({
  listarServicios: vi.fn(),
  listarTiposAlojamiento: vi.fn(),
}));

const SIN_FILTROS = { capacidad: '', tipo: '', precioMin: '', precioMax: '', servicios: [] };

function llenar({ ciudad = 'Santa Marta', llegada = '2026-12-20', salida = '2026-12-25' } = {}) {
  fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: ciudad } });
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: llegada } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: salida } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
}

describe('BusquedaPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listarTiposAlojamiento).mockResolvedValue([{ id: 1, nombre: 'Casa' }, { id: 2, nombre: 'Apartamento' }]);
    vi.mocked(listarServicios).mockResolvedValue([{ id: 10, nombre: 'Wifi' }, { id: 20, nombre: 'Piscina' }]);
  });

  it('busca con la ciudad y las fechas y muestra cada alojamiento con su precio por noche', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000,
        imagenPrincipal: 'https://imagenes.com/1.jpg' },
    ]);
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);

    llenar();

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS,
    });
    expect(screen.getByText('Casa · Santa Marta · Hasta 4 huéspedes')).toBeInTheDocument();
    expect(screen.getByText(/250\.000.*por noche/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Foto de Casa en la playa' })).toBeInTheDocument();
  });

  it('si no hay resultados muestra el estado vacío con la sugerencia de ampliar fechas, sin error', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);

    llenar({ ciudad: 'Leticia' });

    expect(await screen.findByText(/Prueba ampliando el rango de fechas/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('si la API rechaza las fechas muestra su mensaje', async () => {
    vi.mocked(buscarAlojamientos).mockRejectedValue(
      new ErrorApi(400, 'La fecha de salida debe ser posterior a la de llegada'));
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);

    llenar({ llegada: '2026-12-25', salida: '2026-12-20' });

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha de salida debe ser posterior a la de llegada');
  });

  it('no busca si falta la ciudad o alguna fecha y señala qué falta', async () => {
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);

    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(await screen.findByText('Indica la ciudad.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de llegada.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de salida.')).toBeInTheDocument();
    await waitFor(() => expect(buscarAlojamientos).not.toHaveBeenCalled());
  });

  it('envía los filtros combinados junto con la ciudad y las fechas', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);
    await screen.findByLabelText('Wifi');

    fireEvent.change(screen.getByLabelText('Huéspedes (mínimo)'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('Tipo de alojamiento'), { target: { value: 'Casa' } });
    fireEvent.change(screen.getByLabelText('Precio mínimo por noche'), { target: { value: '100000' } });
    fireEvent.change(screen.getByLabelText('Precio máximo por noche'), { target: { value: '500000' } });
    fireEvent.click(screen.getByLabelText('Wifi'));
    fireEvent.click(screen.getByLabelText('Piscina'));
    llenar();

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25',
      capacidad: '4', tipo: 'Casa', precioMin: '100000', precioMax: '500000', servicios: [10, 20],
    }));
  });

  it('al quitar un servicio y buscar de nuevo conserva ciudad, fechas y los demás filtros', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);
    await screen.findByLabelText('Wifi');
    fireEvent.click(screen.getByLabelText('Wifi'));
    fireEvent.click(screen.getByLabelText('Piscina'));
    llenar();
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByLabelText('Wifi'));
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenLastCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, servicios: [20],
    }));
  });

  it('limpiar filtros los quita pero deja la ciudad y las fechas', async () => {
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);
    await screen.findByLabelText('Wifi');
    fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: 'Santa Marta' } });
    fireEvent.change(screen.getByLabelText('Huéspedes (mínimo)'), { target: { value: '4' } });
    fireEvent.click(screen.getByLabelText('Wifi'));

    fireEvent.click(screen.getByRole('button', { name: 'Limpiar filtros' }));

    expect(screen.getByLabelText('Ciudad')).toHaveValue('Santa Marta');
    expect(screen.getByLabelText('Huéspedes (mínimo)')).toHaveValue(null);
    expect(screen.getByLabelText('Wifi')).not.toBeChecked();
  });

  it('si los catálogos no cargan igual permite buscar por ciudad y fechas', async () => {
    vi.mocked(listarTiposAlojamiento).mockRejectedValue(new Error('caído'));
    vi.mocked(listarServicios).mockRejectedValue(new Error('caído'));
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    render(<MemoryRouter><BusquedaPage /></MemoryRouter>);

    llenar();

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalled());
    expect(screen.queryByText('Servicios')).not.toBeInTheDocument();
  });
});
