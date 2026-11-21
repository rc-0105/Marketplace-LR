import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../shared/services/http.js';
import { guardarSesion, limpiarSesion } from '../../shared/services/sesion.js';
import { buscarAlojamientos } from '../../modules/busqueda/services/busquedaService.js';
import { consultarDestacados } from '../../modules/busqueda/services/inicioService.js';
import InicioPage from './InicioPage.jsx';

vi.mock('../../modules/busqueda/services/inicioService.js', () => ({ consultarDestacados: vi.fn() }));
vi.mock('../../modules/busqueda/services/busquedaService.js', () => ({ buscarAlojamientos: vi.fn() }));

const CASA = {
  id: 7, titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'Casa', capacidad: 4,
  precioNoche: 250000, imagenPrincipal: 'https://imagenes.com/7.jpg',
};

function llenarBusqueda({ ciudad = 'Santa Marta', llegada = '2026-12-20', salida = '2026-12-25', capacidad = '' } = {}) {
  fireEvent.change(screen.getByLabelText('Dónde'), { target: { value: ciudad } });
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: llegada } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: salida } });
  fireEvent.change(screen.getByLabelText('Quién'), { target: { value: capacidad } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/" element={<InicioPage />} />
        <Route path="/inicio-sesion" element={<p>Pantalla de inicio de sesión</p>} />
        <Route path="/huesped/busqueda/:id" element={<p>Detalle del alojamiento</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('InicioPage', () => {
  beforeEach(() => vi.resetAllMocks());
  afterEach(() => limpiarSesion());

  it('muestra cada alojamiento con su foto, ciudad y precio', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([CASA]);
    renderizar();

    expect(await screen.findByAltText('Foto de Casa en la playa')).toHaveAttribute('src', CASA.imagenPrincipal);
    expect(screen.getByRole('heading', { name: 'Alojamientos en Santa Marta' })).toBeInTheDocument();
    expect(screen.getByText('Casa en la playa · Santa Marta')).toBeInTheDocument();
    expect(screen.getByText(/250\.000/)).toBeInTheDocument();
  });

  it('al elegir un alojamiento muestra un resumen y se puede cerrar', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([CASA]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: /Casa en la playa/ }));
    const resumen = screen.getByRole('dialog');
    expect(within(resumen).getByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(within(resumen).getByText('Hasta 4 huéspedes')).toBeInTheDocument();

    fireEvent.click(within(resumen).getByRole('button', { name: 'Cerrar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('sin sesión, reservar lleva al inicio de sesión', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([CASA]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: /Casa en la playa/ }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reservar' }));

    expect(await screen.findByText('Pantalla de inicio de sesión')).toBeInTheDocument();
  });

  it('con sesión de huésped, reservar lleva al detalle del alojamiento', async () => {
    guardarSesion({
      token: 't', expiraEn: new Date(Date.now() + 3600_000).toISOString(),
      usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true },
    });
    vi.mocked(consultarDestacados).mockResolvedValue([CASA]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: /Casa en la playa/ }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reservar' }));

    expect(await screen.findByText('Detalle del alojamiento')).toBeInTheDocument();
  });

  it('un anfitrión ve el resumen pero no puede reservar', async () => {
    guardarSesion({
      token: 't', expiraEn: new Date(Date.now() + 3600_000).toISOString(),
      usuario: { id: 2, nombre: 'Luis', correo: 'l@b.co', rol: 'ANFITRION', activa: true },
    });
    vi.mocked(consultarDestacados).mockResolvedValue([CASA]);
    renderizar();

    fireEvent.click(await screen.findByRole('button', { name: /Casa en la playa/ }));
    const resumen = screen.getByRole('dialog');
    expect(within(resumen).getByText('Solo las cuentas de huésped pueden reservar.')).toBeInTheDocument();
    expect(within(resumen).queryByRole('button', { name: 'Reservar' })).not.toBeInTheDocument();
  });

  it('separa los alojamientos en una fila por ciudad, sin distinguir mayúsculas', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([
      { ...CASA, id: 1, titulo: 'Loft', ciudad: 'Bogota' },
      { ...CASA, id: 2, titulo: 'Casa del mar', ciudad: 'Cartagena' },
      { ...CASA, id: 3, titulo: 'Estudio', ciudad: 'bogota' },
    ]);
    renderizar();

    const bogota = await screen.findByRole('region', { name: 'Alojamientos en Bogota' });
    const cartagena = screen.getByRole('region', { name: 'Alojamientos en Cartagena' });
    expect(within(bogota).getAllByRole('listitem')).toHaveLength(2);
    expect(within(cartagena).getAllByRole('listitem')).toHaveLength(1);
    expect(within(cartagena).getByText(/Casa del mar/)).toBeInTheDocument();
  });

  it('sin alojamientos avisa que todavía no hay publicados', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    renderizar();

    expect(await screen.findByText('Todavía no hay alojamientos publicados.')).toBeInTheDocument();
  });

  it('si la consulta falla muestra el mensaje de error', async () => {
    vi.mocked(consultarDestacados).mockRejectedValue(new ErrorApi(0, 'No pudimos conectar con el servidor.'));
    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos conectar con el servidor.');
  });

  it('un visitante sin sesión ve crear una cuenta y la búsqueda lo lleva al flujo del huésped', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    renderizar();
    await waitFor(() => expect(consultarDestacados).toHaveBeenCalled());

    expect(screen.getByRole('link', { name: 'Crear una cuenta' })).toHaveAttribute('href', '/registro');
    expect(screen.getByRole('link', { name: 'Reservar' })).toHaveAttribute('href', '/huesped/busqueda');
  });

  it('con sesión iniciada no ofrece crear una cuenta', async () => {
    guardarSesion({
      token: 't', expiraEn: new Date(Date.now() + 3600_000).toISOString(),
      usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true },
    });
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    renderizar();
    await waitFor(() => expect(consultarDestacados).toHaveBeenCalled());

    expect(screen.queryByRole('link', { name: 'Crear una cuenta' })).not.toBeInTheDocument();
  });

  it('busca con la ciudad, las fechas y los huéspedes, y muestra los resultados en lugar de las filas', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([{ ...CASA, id: 1, ciudad: 'Bogota' }]);
    vi.mocked(buscarAlojamientos).mockResolvedValue([CASA]);
    renderizar();
    await screen.findByRole('region', { name: 'Alojamientos en Bogota' });

    llenarBusqueda({ capacidad: '3' });

    expect(await screen.findByRole('region', { name: 'Resultados en Santa Marta' })).toBeInTheDocument();
    expect(buscarAlojamientos).toHaveBeenCalledWith({
      ciudad: 'Santa Marta', llegada: '2026-12-20', salida: '2026-12-25', capacidad: '3',
    });
    expect(screen.queryByRole('region', { name: 'Alojamientos en Bogota' })).not.toBeInTheDocument();
  });

  it('sin coincidencias avisa y se puede quitar la búsqueda para volver a las filas', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([{ ...CASA, id: 1, ciudad: 'Bogota' }]);
    vi.mocked(buscarAlojamientos).mockResolvedValue([]);
    renderizar();
    await screen.findByRole('region', { name: 'Alojamientos en Bogota' });

    llenarBusqueda();
    expect(await screen.findByText(/No encontramos alojamientos disponibles/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Quitar búsqueda' }));
    expect(await screen.findByRole('region', { name: 'Alojamientos en Bogota' })).toBeInTheDocument();
  });

  it('no busca si faltan datos o si la salida no es posterior a la llegada', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    renderizar();
    await waitFor(() => expect(consultarDestacados).toHaveBeenCalled());

    llenarBusqueda({ ciudad: '' });
    expect(screen.getByRole('alert')).toHaveTextContent('Indica a dónde quieres ir.');

    llenarBusqueda({ llegada: '2026-12-25', salida: '2026-12-20' });
    expect(screen.getByRole('alert')).toHaveTextContent('La salida debe ser posterior a la llegada.');
    expect(buscarAlojamientos).not.toHaveBeenCalled();
  });

  it('si la búsqueda falla muestra el mensaje de la API', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    vi.mocked(buscarAlojamientos).mockRejectedValue(new ErrorApi(400, 'La fecha de salida debe ser posterior a la de llegada'));
    renderizar();
    await waitFor(() => expect(consultarDestacados).toHaveBeenCalled());

    llenarBusqueda();

    expect(await screen.findByText('La fecha de salida debe ser posterior a la de llegada')).toBeInTheDocument();
  });

  it('desde un resultado, reservar lleva al detalle con las fechas buscadas', async () => {
    guardarSesion({
      token: 't', expiraEn: new Date(Date.now() + 3600_000).toISOString(),
      usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol: 'HUESPED', activa: true },
    });
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    vi.mocked(buscarAlojamientos).mockResolvedValue([CASA]);
    renderizar();
    await waitFor(() => expect(consultarDestacados).toHaveBeenCalled());

    llenarBusqueda();
    fireEvent.click(await screen.findByRole('button', { name: /Casa en la playa/ }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Reservar' }));

    expect(await screen.findByText('Detalle del alojamiento')).toBeInTheDocument();
  });
});
