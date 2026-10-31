import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { activarPublicacion, listarMisPublicaciones, pausarPublicacion } from '../services/alojamientosService.js';
import MisPublicacionesPage from './MisPublicacionesPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  activarPublicacion: vi.fn(),
  listarMisPublicaciones: vi.fn(),
  pausarPublicacion: vi.fn(),
}));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <MisPublicacionesPage />
    </MemoryRouter>,
  );
}

function publicacion(datos) {
  return { id: 1, titulo: 'Casa en la playa', ciudad: null, estado: 'BORRADOR', camposFaltantes: [], ...datos };
}

describe('MisPublicacionesPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra cada publicación con su estado, su ciudad y cuántos datos le faltan', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([
      publicacion({ id: 1, titulo: 'Casa en la playa', ciudad: 'Santa Marta', camposFaltantes: ['reglas', 'imagenes'] }),
      publicacion({ id: 2, titulo: 'Apartamento en el centro', camposFaltantes: ['reglas'] }),
      publicacion({ id: 3, titulo: 'Cabaña completa' }),
    ]);

    renderizar();

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(screen.getByText('Borrador · Santa Marta')).toBeInTheDocument();
    expect(screen.getByText('Le faltan 2 datos para activarse.')).toBeInTheDocument();
    expect(screen.getByText('Le falta 1 dato para activarse.')).toBeInTheDocument();
    expect(screen.getByText('Tiene todos los datos para activarse.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Editar Casa en la playa' }))
      .toHaveAttribute('href', '/anfitrion/publicaciones/1/editar');
  });

  it('ofrece crear una publicación nueva', () => {
    vi.mocked(listarMisPublicaciones).mockReturnValue(new Promise(() => {}));

    renderizar();

    expect(screen.getByRole('link', { name: 'Nueva publicación' })).toHaveAttribute('href', '/anfitrion/publicaciones/nueva');
  });

  it('avisa mientras carga', () => {
    vi.mocked(listarMisPublicaciones).mockReturnValue(new Promise(() => {}));

    renderizar();

    expect(screen.getByText('Cargando tus publicaciones…')).toBeInTheDocument();
  });

  it('avisa cuando el anfitrión todavía no tiene publicaciones', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([]);

    renderizar();

    expect(await screen.findByText('Todavía no tienes publicaciones.')).toBeInTheDocument();
  });

  it('al activar un borrador completo lo muestra en revisión y ya no ofrece activarlo', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([publicacion({ id: 1 })]);
    vi.mocked(activarPublicacion).mockResolvedValue(publicacion({ id: 1, estado: 'PENDIENTE_REVISION' }));

    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Activar Casa en la playa' }));

    expect(await screen.findByText('En revisión')).toBeInTheDocument();
    expect(screen.getByText('Esperando la aprobación de un administrador.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Casa en la playa/ })).not.toBeInTheDocument();
    expect(activarPublicacion).toHaveBeenCalledWith(1);
  });

  it('si faltan datos, explica qué completar y la publicación sigue en borrador', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([
      publicacion({ id: 1, camposFaltantes: ['precioNoche', 'imagenes'] }),
    ]);
    vi.mocked(activarPublicacion).mockRejectedValue(new ErrorApi(422, 'Faltan campos para activar la publicación.'));

    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Activar Casa en la playa' }));

    expect(await screen.findByRole('alert'))
      .toHaveTextContent('Faltan campos para activar la publicación. Completa: precio por noche, imágenes.');
    expect(screen.getByText('Borrador')).toBeInTheDocument();
  });

  it('pausa una publicación activa y después ofrece reactivarla', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([publicacion({ id: 1, estado: 'ACTIVA' })]);
    vi.mocked(pausarPublicacion).mockResolvedValue(publicacion({ id: 1, estado: 'PAUSADA' }));
    vi.mocked(activarPublicacion).mockResolvedValue(publicacion({ id: 1, estado: 'ACTIVA' }));

    renderizar();
    expect(await screen.findByText('Visible en las búsquedas.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Pausar Casa en la playa' }));

    expect(await screen.findByText('Pausada')).toBeInTheDocument();
    expect(screen.getByText('Fuera de las búsquedas hasta que la reactives.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reactivar Casa en la playa' }));

    expect(await screen.findByText('Activa')).toBeInTheDocument();
    expect(pausarPublicacion).toHaveBeenCalledWith(1);
    expect(activarPublicacion).toHaveBeenCalledWith(1);
  });

  it('muestra el mensaje de la API cuando rechaza el cambio de estado', async () => {
    vi.mocked(listarMisPublicaciones).mockResolvedValue([publicacion({ id: 1, estado: 'ACTIVA' })]);
    vi.mocked(pausarPublicacion).mockRejectedValue(new ErrorApi(409, 'Solo se puede pausar una publicación activa.'));

    renderizar();
    fireEvent.click(await screen.findByRole('button', { name: 'Pausar Casa en la playa' }));

    await waitFor(() => expect(screen.getByRole('alert'))
      .toHaveTextContent('Solo se puede pausar una publicación activa.'));
    expect(screen.getByRole('button', { name: 'Pausar Casa en la playa' })).toBeEnabled();
  });

  it('muestra un error si la API falla', async () => {
    vi.mocked(listarMisPublicaciones).mockRejectedValue(new ErrorApi(500, 'Error interno.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus publicaciones. Intenta de nuevo.');
  });
});
