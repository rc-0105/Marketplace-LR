import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarPublicacion } from '../services/alojamientosService.js';
import PublicacionEnviadaPage from './PublicacionEnviadaPage.jsx';

vi.mock('../services/alojamientosService.js', () => ({ consultarPublicacion: vi.fn() }));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      initialEntries={['/anfitrion/publicaciones/5/enviada']}>
      <Routes>
        <Route path="/anfitrion/publicaciones/:id/enviada" element={<PublicacionEnviadaPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PublicacionEnviadaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('confirma el envío a revisión con el título y el estado de la publicación', async () => {
    vi.mocked(consultarPublicacion).mockResolvedValue({
      id: 5, titulo: 'Apartamento con balcón', estado: 'PENDIENTE_REVISION',
    });
    renderizar();

    expect(screen.getByRole('heading', { name: 'Publicación enviada a revisión' })).toBeInTheDocument();
    expect(await screen.findByText('Apartamento con balcón')).toBeInTheDocument();
    expect(screen.getByText('En revisión')).toBeInTheDocument();
    expect(consultarPublicacion).toHaveBeenCalledWith('5');
    expect(screen.getByRole('link', { name: 'Volver a mis publicaciones' }))
      .toHaveAttribute('href', '/anfitrion/publicaciones');
  });

  it('si no se pueden cargar los datos, igual confirma el envío y avisa del fallo', async () => {
    vi.mocked(consultarPublicacion).mockRejectedValue(new ErrorApi(500, 'Error.'));
    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los datos de la publicación.');
    expect(screen.getByRole('heading', { name: 'Publicación enviada a revisión' })).toBeInTheDocument();
  });
});
