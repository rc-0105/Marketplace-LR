import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

function renderizar(ruta = '/') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <BusquedaPage />
    </MemoryRouter>,
  );
}

function llenar({ ciudad = 'Santa Marta', llegada = '2026-12-20', salida = '2026-12-25' } = {}) {
  fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: ciudad } });
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: llegada } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: salida } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
}

describe('BusquedaPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    sessionStorage.clear();
    vi.mocked(listarTiposAlojamiento).mockResolvedValue([{ id: 1, nombre: 'Casa' }, { id: 2, nombre: 'Apartamento' }]);
    vi.mocked(listarServicios).mockResolvedValue([{ id: 10, nombre: 'Wifi' }, { id: 20, nombre: 'Piscina' }]);
  });

  it('busca con la ciudad y las fechas y muestra cada alojamiento con su precio por noche', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000,
        imagenPrincipal: 'https://imagenes.com/1.jpg' },
    ]);
    renderizar();

    llenar();

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS,
    });
    expect(screen.getByText('Casa · Santa Marta · Hasta 4 huéspedes')).toBeInTheDocument();
    expect(screen.getByText(/250\.000.*por noche/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Foto de Casa en la playa' })).toBeInTheDocument();
  });

  it('cada alojamiento enlaza a su detalle con las fechas con que se buscó, aunque se editen después', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000,
        imagenPrincipal: null },
    ]);
    renderizar();
    llenar();
    const enlace = await screen.findByRole('link', { name: 'Casa en la playa' });

    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2027-01-30' } });

    expect(enlace).toHaveAttribute('href', '/huesped/busqueda/1?llegada=2026-12-20&salida=2026-12-25');
  });

  it('si no hay resultados muestra el estado vacío con la sugerencia de ampliar fechas, sin error', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();

    llenar({ ciudad: 'Leticia' });

    expect(await screen.findByText(/Prueba ampliando el rango de fechas/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('si la API rechaza las fechas muestra su mensaje', async () => {
    vi.mocked(buscarAlojamientos).mockRejectedValue(
      new ErrorApi(400, 'La fecha de salida debe ser posterior a la de llegada'));
    renderizar();

    llenar({ llegada: '2026-12-25', salida: '2026-12-20' });

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha de salida debe ser posterior a la de llegada');
  });

  it('no busca si falta la ciudad o alguna fecha y señala qué falta', async () => {
    renderizar();

    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(await screen.findByText('Indica la ciudad.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de llegada.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de salida.')).toBeInTheDocument();
    await waitFor(() => expect(buscarAlojamientos).not.toHaveBeenCalled());
  });

  it('envía los filtros combinados junto con la ciudad y las fechas', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
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
    renderizar();
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
    renderizar();
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
    renderizar();

    llenar();

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalled());
    expect(screen.queryByText('Servicios')).not.toBeInTheDocument();
  });

  it('ofrece un enlace Ver calendario por resultado hacia el calendario de esa publicación', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([
      { id: 10, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000 },
      { id: 11, titulo: 'Cabaña', ciudad: 'Santa Marta', tipo: 'Cabaña', capacidad: 2, precioNoche: 120000 },
    ]);
    renderizar();

    llenar();

    await screen.findByRole('heading', { name: 'Casa en la playa' });
    const enlaces = screen.getAllByRole('link', { name: 'Ver calendario' });
    expect(enlaces.map((enlace) => enlace.getAttribute('href'))).toEqual([
      '/huesped/publicaciones/10/calendario',
      '/huesped/publicaciones/11/calendario',
    ]);
  });

  it('sin resultados no muestra enlaces', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();

    llenar({ ciudad: 'Leticia' });

    await screen.findByText(/No encontramos alojamientos/);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('arranca la búsqueda sola con los criterios que trae la dirección y los deja en el formulario', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar('/huesped/busqueda?ciudad=Santa%20Marta&llegada=2026-12-20&salida=2026-12-25&capacidad=2');

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, capacidad: '2',
    }));
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Santa Marta');
    expect(screen.getByLabelText('Huéspedes (mínimo)')).toHaveValue(2);
  });

  it('con solo la ciudad en la dirección la deja en el formulario pero no busca sin fechas', async () => {
    renderizar('/huesped/busqueda?ciudad=Bogot%C3%A1');
    await screen.findByLabelText('Wifi');

    expect(screen.getByLabelText('Ciudad')).toHaveValue('Bogotá');
    expect(buscarAlojamientos).not.toHaveBeenCalled();
  });

  it('con una búsqueda hecha, el tipo y los servicios se aplican al instante', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
    await screen.findByLabelText('Wifi');
    llenar();
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByLabelText('Wifi'));
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenLastCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, servicios: [10],
    }));

    fireEvent.change(screen.getByLabelText('Tipo de alojamiento'), { target: { value: 'Casa' } });
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenLastCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, servicios: [10], tipo: 'Casa',
    }));
  });

  it('el precio se aplica al salir del campo y no con cada tecla', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
    await screen.findByLabelText('Wifi');
    llenar();
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Precio máximo por noche'), { target: { value: '300000' } });
    expect(buscarAlojamientos).toHaveBeenCalledTimes(1);

    fireEvent.blur(screen.getByLabelText('Precio máximo por noche'));
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenLastCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, precioMax: '300000',
    }));
  });

  it('antes de buscar, cambiar un filtro no lanza ninguna búsqueda', async () => {
    renderizar();
    await screen.findByLabelText('Wifi');

    fireEvent.click(screen.getByLabelText('Wifi'));
    fireEvent.change(screen.getByLabelText('Tipo de alojamiento'), { target: { value: 'Casa' } });

    expect(buscarAlojamientos).not.toHaveBeenCalled();
    expect(screen.getByText(/Elige una ciudad y las fechas/)).toBeInTheDocument();
  });

  it('al volver a la búsqueda recupera ciudad, fechas y filtros de la última y la repite', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
    await screen.findByLabelText('Wifi');
    fireEvent.click(screen.getByLabelText('Piscina'));
    llenar();
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledTimes(1));
    cleanup();
    vi.mocked(buscarAlojamientos).mockClear();

    renderizar('/huesped/busqueda');

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS, servicios: [20],
    }));
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Santa Marta');
    expect(screen.getByLabelText('Llegada')).toHaveValue('2026-12-20');
    expect(screen.getByLabelText('Salida')).toHaveValue('2026-12-25');
    expect(await screen.findByLabelText('Piscina')).toBeChecked();
  });

  it('con otra ciudad en la dirección conserva las fechas de la última búsqueda y descarta los filtros', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
    await screen.findByLabelText('Wifi');
    fireEvent.click(screen.getByLabelText('Piscina'));
    llenar();
    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledTimes(1));
    cleanup();
    vi.mocked(buscarAlojamientos).mockClear();

    renderizar('/huesped/busqueda?ciudad=Cartagena');

    await waitFor(() => expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Cartagena', llegada: '2026-12-20', salida: '2026-12-25', ...SIN_FILTROS,
    }));
    expect(await screen.findByLabelText('Piscina')).not.toBeChecked();
  });

  it('la búsqueda fallida no se recuerda, para no repetir al volver unas fechas que la API rechazó', async () => {
    vi.mocked(buscarAlojamientos).mockRejectedValue(new ErrorApi(400, 'La fecha de salida debe ser posterior'));
    renderizar();
    await screen.findByLabelText('Wifi');
    llenar({ llegada: '2026-12-25', salida: '2026-12-20' });
    await screen.findByRole('alert');
    cleanup();
    vi.mocked(buscarAlojamientos).mockClear();

    renderizar('/huesped/busqueda');
    await screen.findByLabelText('Wifi');

    expect(screen.getByLabelText('Ciudad')).toHaveValue('');
    expect(buscarAlojamientos).not.toHaveBeenCalled();
  });
});