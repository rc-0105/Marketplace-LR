import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import {
  activarPublicacion, actualizarImagenes, actualizarServicios, consultarPublicacion, crearPublicacion,
  editarPublicacion, listarPoliticasCancelacion, listarServicios, listarTiposAlojamiento,
} from '../services/alojamientosService.js';
import PublicacionFormPage from './PublicacionFormPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  activarPublicacion: vi.fn(),
  subirImagen: vi.fn(),
  actualizarConfiguracion: vi.fn(),
  listarPoliticasCancelacion: vi.fn(),
  actualizarImagenes: vi.fn(),
  actualizarServicios: vi.fn(),
  listarServicios: vi.fn(),
  consultarPublicacion: vi.fn(),
  crearPublicacion: vi.fn(),
  editarPublicacion: vi.fn(),
  listarTiposAlojamiento: vi.fn(),
}));

const TIPOS = [{ id: 1, nombre: 'Apartamento' }, { id: 2, nombre: 'Casa' }];

const EXISTENTE = {
  id: 5, titulo: 'Casa en la playa', descripcion: null, ciudad: 'Santa Marta', direccion: null,
  tipo: { id: 2, nombre: 'Casa' }, capacidad: 4, precioNoche: 250000, reglas: null,
  servicios: [{ id: 9, nombre: 'Wifi' }], imagenes: ['https://imagenes.com/1.jpg'],
  tarifaLimpieza: null, modoReserva: 'INMEDIATA', politicaCancelacion: null, serviciosAdicionales: [],
  estado: 'BORRADOR', camposFaltantes: [],
};

function renderizarEn(ruta) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <Routes>
        <Route path="/anfitrion/publicaciones/nueva" element={<PublicacionFormPage />} />
        <Route path="/anfitrion/publicaciones/:id/editar" element={<PublicacionFormPage />} />
        <Route path="/anfitrion/publicaciones/:id/enviada" element={<p>Publicación enviada</p>} />
        <Route path="/anfitrion/publicaciones" element={<p>Lista de publicaciones</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function escribir(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

describe('PublicacionFormPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listarTiposAlojamiento).mockResolvedValue(TIPOS);
    vi.mocked(listarServicios).mockResolvedValue([{ id: 9, nombre: 'Wifi' }]);
    vi.mocked(listarPoliticasCancelacion).mockResolvedValue([
      { id: 1, nombre: 'Flexible', descripcion: 'Devolución total.', diasSinCosto: 1, porcentajeDevolucion: 50 },
    ]);
  });

  it('empieza en el paso 1, con los seis pasos a la vista y el estado BORRADOR', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva');

    expect(screen.getByText('PASO 1 DE 6')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Información básica' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Nueva publicación' })).toBeInTheDocument();
    expect(screen.getByText('Borrador')).toBeInTheDocument();
    for (const nombre of ['Información básica', 'Ubicación', 'Precio y tarifas', 'Servicios y reglas', 'Imágenes',
      'Reserva y cancelación']) {
      expect(screen.getByRole('button', { name: new RegExp(nombre) })).toBeInTheDocument();
    }
    await screen.findByRole('option', { name: 'Casa' });
  });

  it('el paso viene de la dirección y un valor inválido cae al paso 1', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva?paso=99');
    expect(screen.getByText('PASO 1 DE 6')).toBeInTheDocument();
    await screen.findByRole('option', { name: 'Casa' });
  });

  it('al editar muestra en el paso 6 las condiciones de reserva y la política', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=6');

    expect(await screen.findByRole('radio', { name: /Reserva inmediata/ })).toBeChecked();
    expect(screen.getByText('PASO 6 DE 6')).toBeInTheDocument();
    expect(screen.getByLabelText('Política de cancelación')).toBeInTheDocument();
    expect(screen.queryByLabelText('Tarifa de limpieza (COP)')).not.toBeInTheDocument();
  });

  it('al crear, los pasos que necesitan el borrador piden guardarlo primero', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva?paso=6');
    await screen.findByText(/Guarda el borrador/);

    expect(screen.queryByRole('radio', { name: /Reserva inmediata/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar activación' })).not.toBeInTheDocument();
  });

  it('crea el borrador con los datos escritos y lleva a editarlo en el mismo paso', async () => {
    vi.mocked(crearPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    renderizarEn('/anfitrion/publicaciones/nueva');

    await screen.findByRole('option', { name: 'Casa' });
    escribir('Título', '  Casa en la playa ');
    escribir('Tipo de alojamiento', '2');
    escribir('Capacidad (huéspedes)', '4');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByRole('heading', { name: 'Editar publicación' })).toBeInTheDocument();
    expect(screen.getByText('PASO 1 DE 6')).toBeInTheDocument();
    expect(crearPublicacion).toHaveBeenCalledWith({
      titulo: 'Casa en la playa', descripcion: null, ciudad: null, direccion: null, tipo: 2,
      capacidad: 4, precioNoche: null, reglas: null, servicios: [], imagenes: [],
    });
  });

  it('«Siguiente» al crear guarda el borrador y sigue al paso siguiente', async () => {
    vi.mocked(crearPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, id: 12 });
    renderizarEn('/anfitrion/publicaciones/nueva');

    await screen.findByRole('option', { name: 'Casa' });
    escribir('Título', 'Casa');
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText('PASO 2 DE 6')).toBeInTheDocument();
    expect(crearPublicacion).toHaveBeenCalledTimes(1);
  });

  it('sin título muestra el error y no llama a la API', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva');
    await screen.findByRole('option', { name: 'Casa' });

    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(screen.getByText('El título es obligatorio.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('rechaza una capacidad con formato inválido', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva');
    await screen.findByRole('option', { name: 'Casa' });

    escribir('Título', 'Casa');
    escribir('Capacidad (huéspedes)', '2.5');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(screen.getByText('La capacidad debe ser un número entero mayor que cero.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('un error que está en otro paso lleva a ese paso', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva?paso=3');

    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    // Falta el título, que se captura en el paso 1: el paso 3 no tiene errores, así que se salta al 1.
    expect(await screen.findByText('PASO 1 DE 6')).toBeInTheDocument();
    expect(screen.getByText('El título es obligatorio.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('el precio inválido se muestra junto al campo del paso 3', async () => {
    renderizarEn('/anfitrion/publicaciones/nueva?paso=3');

    fireEvent.click(screen.getByRole('button', { name: /^1\s*Información básica/ }));
    escribir('Título', 'Casa');
    fireEvent.click(screen.getByRole('button', { name: /Precio y tarifas/ }));
    escribir('Precio por noche (COP)', '-10');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(screen.getByText('El precio por noche debe ser mayor que cero, con máximo 2 decimales.')).toBeInTheDocument();
    expect(crearPublicacion).not.toHaveBeenCalled();
  });

  it('muestra junto a su campo los errores que devuelve la API y lleva al paso de ese campo', async () => {
    vi.mocked(crearPublicacion).mockRejectedValue(
      new ErrorApi(400, 'Datos inválidos.', { ciudad: 'La ciudad admite máximo 100 caracteres.' }));
    renderizarEn('/anfitrion/publicaciones/nueva');
    await screen.findByRole('option', { name: 'Casa' });

    escribir('Título', 'Casa');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar borrador' }));

    expect(await screen.findByText('PASO 2 DE 6')).toBeInTheDocument();
    expect(screen.getByText('La ciudad admite máximo 100 caracteres.')).toBeInTheDocument();
  });

  it('al editar carga los datos actuales y conserva servicios e imágenes al guardar', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(editarPublicacion).mockResolvedValue({ ...EXISTENTE, precioNoche: 300000 });
    renderizarEn('/anfitrion/publicaciones/5/editar');

    expect(await screen.findByLabelText('Título')).toHaveValue('Casa en la playa');
    fireEvent.click(screen.getByRole('button', { name: /Ubicación/ }));
    expect(screen.getByLabelText('Ciudad')).toHaveValue('Santa Marta');
    fireEvent.click(screen.getByRole('button', { name: /Precio y tarifas/ }));
    expect(screen.getByLabelText('Precio por noche (COP)')).toHaveValue('250000');
    escribir('Precio por noche (COP)', '300000');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos los cambios.');
    expect(consultarPublicacion).toHaveBeenCalledWith('5');
    expect(editarPublicacion).toHaveBeenCalledWith('5', expect.objectContaining({
      titulo: 'Casa en la playa', ciudad: 'Santa Marta', tipo: 2, precioNoche: 300000,
      servicios: [9], imagenes: ['https://imagenes.com/1.jpg'],
    }));
  });

  it('«Siguiente» y «Atrás» al editar guardan y mueven entre pasos', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(editarPublicacion).mockResolvedValue(EXISTENTE);
    renderizarEn('/anfitrion/publicaciones/5/editar');
    await screen.findByLabelText('Título');

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText('PASO 2 DE 6')).toBeInTheDocument();
    expect(editarPublicacion).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(screen.getByText('PASO 1 DE 6')).toBeInTheDocument();
  });

  it('en el paso 4 ofrece los servicios, y el PUT envía los recién guardados', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(actualizarServicios).mockResolvedValue({ ...EXISTENTE, servicios: [] });
    vi.mocked(editarPublicacion).mockResolvedValue(EXISTENTE);
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=4');

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Wifi' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar servicios' }));
    await screen.findByText('Guardamos los servicios.');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText('Guardamos los cambios.');
    expect(actualizarServicios).toHaveBeenCalledWith('5', []);
    expect(editarPublicacion).toHaveBeenCalledWith('5', expect.objectContaining({ servicios: [] }));
  });

  it('en el paso 5 ofrece las imágenes, y el PUT envía las recién guardadas', async () => {
    const nueva = 'https://imagenes.com/2.jpg';
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(actualizarImagenes).mockResolvedValue({ ...EXISTENTE, imagenes: [nueva, 'https://imagenes.com/1.jpg'] });
    vi.mocked(editarPublicacion).mockResolvedValue(EXISTENTE);
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=5');

    await screen.findByLabelText('URL de la imagen');
    escribir('URL de la imagen', nueva);
    fireEvent.click(screen.getByRole('button', { name: 'Agregar imagen' }));
    fireEvent.click(screen.getByRole('button', { name: 'Subir la imagen 2' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar imágenes' }));
    await screen.findByText('Guardamos las imágenes.');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await screen.findByText('Guardamos los cambios.');
    expect(actualizarImagenes).toHaveBeenCalledWith('5', [nueva, 'https://imagenes.com/1.jpg']);
    expect(editarPublicacion).toHaveBeenCalledWith('5', expect.objectContaining({
      imagenes: [nueva, 'https://imagenes.com/1.jpg'],
    }));
  });

  it('el panel lateral marca lo que falta y lista los requisitos de activación', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, camposFaltantes: ['direccion'] });
    renderizarEn('/anfitrion/publicaciones/5/editar');

    expect(await screen.findByText('Falta la dirección')).toBeInTheDocument();
    expect(screen.getByText('Antes de solicitar la activación')).toBeInTheDocument();
    expect(screen.getByText('Título y descripción')).toBeInTheDocument();
    expect(screen.getByText('Al menos una imagen')).toBeInTheDocument();
  });

  it('al pulsar un paso del panel lateral se va a ese paso', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    renderizarEn('/anfitrion/publicaciones/5/editar');
    await screen.findByLabelText('Título');

    fireEvent.click(screen.getByRole('button', { name: /Servicios y reglas/ }));

    expect(screen.getByText('PASO 4 DE 6')).toBeInTheDocument();
  });

  it('en el último paso de un borrador, «Solicitar activación» la envía a revisión', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue(EXISTENTE);
    vi.mocked(activarPublicacion).mockResolvedValue({ ...EXISTENTE, estado: 'PENDIENTE_REVISION' });
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=6');

    fireEvent.click(await screen.findByRole('button', { name: 'Solicitar activación' }));

    expect(await screen.findByText('Publicación enviada')).toBeInTheDocument();
    expect(activarPublicacion).toHaveBeenCalledWith('5');
  });

  it('si falta información, la activación explica qué completar y no cambia de pantalla', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, camposFaltantes: ['direccion', 'imagenes'] });
    vi.mocked(activarPublicacion).mockRejectedValue(new ErrorApi(422, 'Faltan datos para activar.'));
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=6');

    fireEvent.click(await screen.findByRole('button', { name: 'Solicitar activación' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Faltan datos para activar. Completa: dirección, imágenes.');
    expect(screen.getByText('PASO 6 DE 6')).toBeInTheDocument();
  });

  it('una publicación que ya no es borrador termina en «Terminar», sin pedir activación', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue({ ...EXISTENTE, estado: 'ACTIVA' });
    renderizarEn('/anfitrion/publicaciones/5/editar?paso=6');

    fireEvent.click(await screen.findByRole('button', { name: 'Terminar' }));

    expect(await screen.findByText('Lista de publicaciones')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Solicitar activación' })).not.toBeInTheDocument();
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

  it('avisa si no se pueden cargar los tipos de alojamiento', async () => {
    vi.mocked(listarTiposAlojamiento).mockRejectedValue(new ErrorApi(500, 'Error.'));
    renderizarEn('/anfitrion/publicaciones/nueva');

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los tipos de alojamiento.');
  });
});
