import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { actualizarServicios, listarServicios } from '../services/alojamientosService.js';
import ServiciosPublicacion from './ServiciosPublicacion.jsx';

vi.mock('../services/alojamientosService.js', () => ({ actualizarServicios: vi.fn(), listarServicios: vi.fn() }));

const CATALOGO = [{ id: 1, nombre: 'Cocina' }, { id: 2, nombre: 'Piscina' }, { id: 3, nombre: 'Wifi' }];

describe('ServiciosPublicacion', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listarServicios).mockResolvedValue(CATALOGO);
  });

  it('muestra el catálogo con los servicios actuales marcados', async () => {
    render(<ServiciosPublicacion publicacionId={5} seleccionados={[{ id: 3, nombre: 'Wifi' }]} alGuardar={vi.fn()} />);

    expect(await screen.findByRole('checkbox', { name: 'Wifi' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Cocina' })).not.toBeChecked();
  });

  it('guarda los servicios marcados y entrega la publicación actualizada', async () => {
    const actualizada = { id: 5, servicios: [{ id: 2, nombre: 'Piscina' }] };
    vi.mocked(actualizarServicios).mockResolvedValue(actualizada);
    const alGuardar = vi.fn();
    render(<ServiciosPublicacion publicacionId={5} seleccionados={[{ id: 3, nombre: 'Wifi' }]} alGuardar={alGuardar} />);

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Wifi' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Piscina' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos los servicios.');
    expect(actualizarServicios).toHaveBeenCalledWith(5, [2]);
    expect(alGuardar).toHaveBeenCalledWith(actualizada);
  });

  it('muestra el error de la API si no se pueden guardar', async () => {
    vi.mocked(actualizarServicios).mockRejectedValue(new ErrorApi(400, 'Uno de los servicios no existe en el catálogo.'));
    render(<ServiciosPublicacion publicacionId={5} seleccionados={[]} alGuardar={vi.fn()} />);

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Cocina' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Uno de los servicios no existe en el catálogo.');
  });

  it('avisa si el catálogo no se puede cargar y no deja guardar', async () => {
    vi.mocked(listarServicios).mockRejectedValue(new ErrorApi(500, 'Error.'));
    render(<ServiciosPublicacion publicacionId={5} seleccionados={[]} alGuardar={vi.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el catálogo de servicios.');
    expect(screen.getByRole('button', { name: 'Guardar servicios' })).toBeDisabled();
  });
});
