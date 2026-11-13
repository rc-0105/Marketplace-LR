import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { buscarAlojamientos } from '../services/busquedaService.js';
import BusquedaPage from './BusquedaPage.jsx';

vi.mock('../services/busquedaService.js', () => ({ buscarAlojamientos: vi.fn() }));

function llenar({ ciudad = 'Santa Marta', llegada = '2026-12-20', salida = '2026-12-25' } = {}) {
  fireEvent.change(screen.getByLabelText('Ciudad'), { target: { value: ciudad } });
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: llegada } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: salida } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
}

describe('BusquedaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('busca con la ciudad y las fechas y muestra cada alojamiento con su precio por noche', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([
      { id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4, precioNoche: 250000,
        imagenPrincipal: 'https://imagenes.com/1.jpg' },
    ]);
    render(<BusquedaPage />);

    llenar();

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(buscarAlojamientos).toHaveBeenCalledWith({ ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25' });
    expect(screen.getByText('Casa · Santa Marta · Hasta 4 huéspedes')).toBeInTheDocument();
    expect(screen.getByText(/250\.000.*por noche/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Foto de Casa en la playa' })).toBeInTheDocument();
  });

  it('si no hay resultados muestra el estado vacío con la sugerencia de ampliar fechas, sin error', async () => {
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    render(<BusquedaPage />);

    llenar({ ciudad: 'Leticia' });

    expect(await screen.findByText(/Prueba ampliando el rango de fechas/)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('si la API rechaza las fechas muestra su mensaje', async () => {
    vi.mocked(buscarAlojamientos).mockRejectedValue(
      new ErrorApi(400, 'La fecha de salida debe ser posterior a la de llegada'));
    render(<BusquedaPage />);

    llenar({ llegada: '2026-12-25', salida: '2026-12-20' });

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha de salida debe ser posterior a la de llegada');
  });

  it('no busca si falta la ciudad o alguna fecha y señala qué falta', async () => {
    render(<BusquedaPage />);

    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(await screen.findByText('Indica la ciudad.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de llegada.')).toBeInTheDocument();
    expect(screen.getByText('Indica la fecha de salida.')).toBeInTheDocument();
    await waitFor(() => expect(buscarAlojamientos).not.toHaveBeenCalled());
  });
});
