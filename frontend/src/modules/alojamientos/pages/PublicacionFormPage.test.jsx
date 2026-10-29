import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import {
  consultarPublicacion, crearPublicacion, editarPublicacion,
} from '../services/alojamientosService.js';
import PublicacionFormPage from './PublicacionFormPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  consultarPublicacion: vi.fn(),
  crearPublicacion: vi.fn(),
  editarPublicacion: vi.fn(),
}));

const EXISTENTE = {
  id: 5, titulo: 'Casa en la playa', descripcion: null, ciudad: 'Santa Marta', direccion: null,
  tipo: 'CASA', capacidad: 4, precioNoche: 250000, reglas: null,
  servicios: ['Wifi'], imagenes: ['https://imagenes.com/1.jpg'],
  estado: 'BORRADOR', camposFaltantes: [],
};

function renderizarEn(ruta) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <Routes>
        <Route path="/anfitrion/publicaciones/nueva" element={<PublicacionFormPage />} />
        <Route path="/anfitrion/publicaciones/:id/editar" element={<PublicacionFormPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function escribir(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

describe('PublicacionFormPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('crea el borrador con los datos escritos y lleva a editarlo', async () => {
    vi.mocked(crearPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    renderizarEn('/anfitrion/publicaciones/nueva');

    expect(screen.getByRole('heading', { name: 'Nueva publicación' })).toBeInTheDocument();
    escribir('Título', '  Casa en la playa ');
    escribir('Tipo de alojamiento', 'CASA');
    escribir('Capacidad (huéspedes)', '4');
    escribir('Precio por noche (COP)', '250000.50');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByRole('heading', { name: 'Editar publicación' })).toBeInTheDocument();
    expect(crearPublicacion).toHaveBeenCalledWith({
      titulo: 'Casa en la playa', descripcion: null, ciudad: null, direccion: null, tipo: 'CASA',
      capacidad: 4, precioNoche: 250000.5, reglas: null, servicios: [], imagenes: [],
    });
  });

  it('sin título muestra el error y no llama a la API', () => {
    renderizarEn('/anfitrion/publicaciones/nueva');

    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(screen.getByText('El título es obligatorio.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('rechaza una capacidad o un precio con formato inválido', () => {
    renderizarEn('/anfitrion/publicaciones/nueva');

    escribir('Título', 'Casa');
    escribir('Capacidad (huéspedes)', '2.5');
    escribir('Precio por noche (COP)', '-10');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(screen.getByText('La capacidad debe ser un número entero mayor que cero.')).toBeInTheDocument();
    expect(screen.getByText('El precio por noche debe ser mayor que cero, con máximo 2 decimales.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('muestra junto a cada campo los errores que devuelve la API', async () => {
    vi.mocked(crearPublicacion).mockRejectedValue(
      new ErrorApi(400, 'Datos inválidos.', { ciudad: 'La ciudad admite máximo 100 caracteres.' }));
    renderizarEn('/anfitrion/publicaciones/nueva');

    escribir('Título', 'Casa');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText('La ciudad admite máximo 100 caracteres.')).toBeInTheDocument();
  });

  it('al editar carga los datos actuales y conserva servicios e imágenes al guardar', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(editarPublicacion).mockResolvedValue({ ...EXISTENTE, precioNoche: 300000 });
    renderizarEn('/anfitrion/publicaciones/5/editar');

    expect(await screen.findByLabelText('Título')).toHaveValue('Casa en la playa');
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Santa Marta');
    expect(screen.getByLabelText('Precio por noche (COP)')).toHaveValue('250000');
    escribir('Precio por noche (COP)', '300000');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos los cambios.');
    expect(consultarPublicacion).toHaveBeenCalledWith('5');
    expect(editarPublicacion).toHaveBeenCalledWith('5', expect.objectContaining({
      titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 'CASA', precioNoche: 300000,
      servicios: ['Wifi'], imagenes: ['https://imagenes.com/1.jpg'],
    }));
  });

  it('avisa cuando la publicación no es del anfitrión', async () => {
    vi.mocked(consultarPublicacion).mockRejectedValue(new ErrorApi(403, 'No tienes permiso.'));
    renderizarEn('/anfitrion/publicaciones/5/editar');

    expect(await screen.findByRole('alert')).toHaveTextContent('Esta publicación no es tuya.');
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
  });

  it('muestra un error general si la publicación no se puede cargar', async () => {
    vi.mocked(consultarPublicacion).mockRejectedValue(new ErrorApi(404, 'No encontramos esa publicación.'));
    renderizarEn('/anfitrion/publicaciones/5/editar');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la publicación. Intenta de nuevo.');
  });
});
