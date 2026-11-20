import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { listar } from '../services/auditoriaService.js';
import AuditoriaPage from './AuditoriaPage.jsx';

vi.mock('../services/auditoriaService.js', () => ({ listar: vi.fn() }));

const EVENTO = {
  id: 1, fecha: '2026-11-16T03:30:00Z', usuario: '2', accion: 'RESERVA', entidad: 'Reserva', entidadId: '55',
  resultado: 'EXITO', datos: { operacion: 'CREAR' }, correlacion: 'c-1',
};
const SIN_USUARIO = { ...EVENTO, id: 6, usuario: null, entidadId: null, resultado: 'FALLO', accion: 'AUTENTICACION' };

function pagina(contenido, extra = {}) {
  return { contenido, pagina: 0, tamano: 50, totalElementos: contenido.length, totalPaginas: contenido.length ? 1 : 0, ...extra };
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuditoriaPage />
    </MemoryRouter>,
  );
}

describe('AuditoriaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra el estado de carga y luego la tabla de la página 0 con tamaño 50', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([EVENTO]));

    renderizar();

    expect(screen.getByText('Cargando eventos…')).toBeInTheDocument();
    const tabla = await screen.findByRole('table', { name: 'Eventos de auditoría' });
    expect(listar).toHaveBeenCalledWith({}, 0, 50);
    expect(screen.queryByText('Cargando eventos…')).not.toBeInTheDocument();
    const encabezados = within(tabla).getAllByRole('columnheader').map((columna) => columna.textContent);
    expect(encabezados).toEqual(['Fecha (Bogotá)', 'Usuario', 'Evento', 'Entidad', 'Id de la entidad', 'Resultado']);
    const celdas = within(within(tabla).getAllByRole('row')[1]).getAllByRole('cell').map((celda) => celda.textContent);
    expect(celdas).toEqual(['15/11/2026 22:30:00', '2', 'RESERVA', 'Reserva', '55', 'Éxito']);
  });

  it('muestra los campos nulos y el fallo en lenguaje claro', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([SIN_USUARIO]));

    renderizar();

    const fila = (await screen.findAllByRole('row'))[1];
    expect(within(fila).getAllByRole('cell').map((celda) => celda.textContent))
      .toEqual(['15/11/2026 22:30:00', 'Sin usuario', 'AUTENTICACION', 'Reserva', 'Sin id', 'Fallo']);
  });

  it('con la lista vacía muestra "sin resultados" y no la tabla', async () => {
    vi.mocked(listar).mockResolvedValue(pagina([]));

    renderizar();

    expect(await screen.findByText('Sin resultados: no hay eventos de auditoría con esos criterios.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('ante un 400 muestra el mensaje de la API sin romper la página', async () => {
    vi.mocked(listar).mockRejectedValue(new ErrorApi(400, 'La fecha inicial no puede ser posterior a la final.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha inicial no puede ser posterior a la final.');
    expect(screen.getByRole('heading', { name: 'Auditoría' })).toBeInTheDocument();
    expect(screen.queryByText('Cargando eventos…')).not.toBeInTheDocument();
  });

  it.each([new ErrorApi(500, 'Error interno'), new Error('red')])(
    'ante un fallo del servidor o de red muestra un mensaje genérico', async (error) => {
      vi.mocked(listar).mockRejectedValue(error);

      renderizar();

      expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los eventos de auditoría. Intenta de nuevo.');
    },
  );
});
