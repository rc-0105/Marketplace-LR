import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listarPendientesDeRevision } from '../services/alojamientosService.js';
import ResumenRevision from './ResumenRevision.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  listarPendientesDeRevision: vi.fn(),
}));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ResumenRevision />
    </MemoryRouter>,
  );
}

describe('ResumenRevision', () => {
  beforeEach(() => vi.resetAllMocks());

  it('dice cuántas publicaciones esperan revisión y lleva al panel de revisión', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([{ id: 1 }, { id: 2 }, { id: 3 }]);

    renderizar();

    expect(await screen.findByText('3 publicaciones esperan tu revisión.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Revisión de publicaciones' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Revisar publicaciones' }))
      .toHaveAttribute('href', '/administrador/publicaciones');
  });

  it('usa el singular cuando hay una sola pendiente', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([{ id: 1 }]);

    renderizar();

    expect(await screen.findByText('1 publicación espera tu revisión.')).toBeInTheDocument();
  });

  it('avisa cuando no hay nada pendiente', async () => {
    vi.mocked(listarPendientesDeRevision).mockResolvedValue([]);

    renderizar();

    expect(await screen.findByText('No hay publicaciones pendientes de revisión.')).toBeInTheDocument();
  });

  it('avisa mientras consulta', () => {
    vi.mocked(listarPendientesDeRevision).mockReturnValue(new Promise(() => {}));

    renderizar();

    expect(screen.getByText('Consultando publicaciones pendientes…')).toBeInTheDocument();
  });

  it('si la API falla lo dice y sigue ofreciendo el enlace', async () => {
    vi.mocked(listarPendientesDeRevision).mockRejectedValue(new ErrorApi(500, 'Error interno.'));

    renderizar();

    expect(await screen.findByText('No pudimos consultar las publicaciones pendientes.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Revisar publicaciones' })).toBeInTheDocument();
  });
});
