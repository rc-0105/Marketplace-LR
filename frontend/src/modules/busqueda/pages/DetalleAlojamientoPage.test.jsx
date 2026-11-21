import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarResenas } from '../../resenas/services/resenaService.js';
import { consultarDetalle } from '../services/busquedaService.js';
import DetalleAlojamientoPage from './DetalleAlojamientoPage.jsx';

vi.mock('../services/busquedaService.js', () => ({ consultarDetalle: vi.fn() }));
vi.mock('../../resenas/services/resenaService.js', () => ({ consultarResenas: vi.fn() }));

const DETALLE = {
  id: 5,
  titulo: 'Casa en la playa',
  descripcion: 'Frente al mar',
  ciudad: 'Santa Marta',
  tipo: 'Casa',
  capacidad: 4,
  precioNoche: 250000,
  reglas: 'No fumar',
  imagenes: ['https://imagenes.com/1.jpg'],
  servicios: ['Wifi', 'Piscina'],
  politicaCancelacion: 'Flexible',
  descripcionPolitica: 'Devolución total hasta 2 días antes',
  periodos: [{ inicio: '2026-12-20', fin: '2026-12-31' }],
  calificacionPromedio: null,
};

function abrir(ruta = '/huesped/busqueda/5') {
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/huesped/busqueda/:id" element={<DetalleAlojamientoPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DetalleAlojamientoPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(consultarResenas).mockResolvedValue({ promedio: null, resenas: [] });
  });

  it('muestra imágenes, descripción, servicios, reglas, capacidad, precio, política y calendario', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue(DETALLE);

    abrir();

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(consultarDetalle).toHaveBeenCalledWith('5');
    expect(screen.getByRole('img', { name: 'Foto 1 de Casa en la playa' })).toBeInTheDocument();
    expect(screen.getByText('Frente al mar')).toBeInTheDocument();
    expect(screen.getByText('Casa · Santa Marta · Hasta 4 huéspedes')).toBeInTheDocument();
    expect(screen.getByText(/250\.000.*por noche/)).toBeInTheDocument();
    expect(screen.getByText('Wifi')).toBeInTheDocument();
    expect(screen.getByText('Piscina')).toBeInTheDocument();
    expect(screen.getByText('No fumar')).toBeInTheDocument();
    expect(screen.getByText('Flexible')).toBeInTheDocument();
    expect(screen.getByText(/Del 20 de diciembre de 2026 al 31 de diciembre de 2026/)).toBeInTheDocument();
  });

  it('el cotizador llega con la llegada y la salida de la búsqueda, y el huésped puede cambiarlas', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue(DETALLE);

    abrir('/huesped/busqueda/5?llegada=2026-12-20&salida=2026-12-25');

    expect(await screen.findByLabelText('Llegada')).toHaveValue('2026-12-20');
    expect(screen.getByLabelText('Salida')).toHaveValue('2026-12-25');
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2026-12-27' } });
    expect(screen.getByLabelText('Salida')).toHaveValue('2026-12-27');
  });

  it('sin fechas en la dirección, o con un formato inválido, el cotizador empieza vacío', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue(DETALLE);

    abrir('/huesped/busqueda/5?llegada=pronto&salida=');

    expect(await screen.findByLabelText('Llegada')).toHaveValue('');
    expect(screen.getByLabelText('Salida')).toHaveValue('');
  });

  it('si no tiene calificaciones lo indica en lugar de mostrar cero', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue(DETALLE);

    abrir();

    expect(await screen.findByText('Aún sin calificaciones')).toBeInTheDocument();
    expect(screen.queryByText(/Calificación promedio/)).not.toBeInTheDocument();
  });

  it('si tiene calificaciones muestra el promedio', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue({ ...DETALLE, calificacionPromedio: 4.5 });

    abrir();

    expect(await screen.findByText('Calificación promedio: 4.5 de 5')).toBeInTheDocument();
  });

  it('si la publicación ya no está activa muestra la página de no disponible', async () => {
    vi.mocked(consultarDetalle).mockRejectedValue(new ErrorApi(404, 'No encontramos esa publicación.'));

    abrir();

    expect(await screen.findByRole('heading', { name: 'Alojamiento no disponible' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a la búsqueda' })).toBeInTheDocument();
  });

  it('ante otro error muestra un aviso y no la página de no disponible', async () => {
    vi.mocked(consultarDetalle).mockRejectedValue(new ErrorApi(500, 'Error'));

    abrir();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el alojamiento');
    expect(screen.queryByText('Alojamiento no disponible')).not.toBeInTheDocument();
  });

  it('muestra la sección de reseñas con las reseñas visibles de la publicación', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue({ ...DETALLE, calificacionPromedio: 4.5 });
    vi.mocked(consultarResenas).mockResolvedValue({
      promedio: 4.5,
      resenas: [
        { id: 1, reservaId: 11, autor: 'Ana Gómez', calificacion: 4, contenido: 'Muy cómodo', fecha: '2026-01-10T10:00:00Z' },
      ],
    });

    abrir();

    expect(await screen.findByRole('heading', { name: 'Reseñas' })).toBeInTheDocument();
    expect(await screen.findByText('Muy cómodo')).toBeInTheDocument();
    expect(consultarResenas).toHaveBeenCalledWith(5);
  });

  it('si la publicación no tiene reseñas lo indica en la sección', async () => {
    vi.mocked(consultarDetalle).mockResolvedValue(DETALLE);

    abrir();

    expect(await screen.findByText('Esta publicación aún no tiene reseñas.')).toBeInTheDocument();
  });
});
