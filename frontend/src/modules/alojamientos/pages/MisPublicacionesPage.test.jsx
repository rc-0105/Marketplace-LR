import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listarMisPublicaciones } from '../services/alojamientosService.js';
import MisPublicacionesPage from './MisPublicacionesPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({ listarMisPublicaciones: vi.fn() }));

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

  it('muestra un error si la API falla', async () => {
    vi.mocked(listarMisPublicaciones).mockRejectedValue(new ErrorApi(500, 'Error interno.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus publicaciones. Intenta de nuevo.');
  });
});
