import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { detalle } from '../services/auditoriaService.js';
import DetalleEventoAuditoriaPage from './DetalleEventoAuditoriaPage.jsx';

vi.mock('../services/auditoriaService.js', () => ({ detalle: vi.fn() }));

const EVENTO = {
  id: 1, fecha: '2026-11-16T03:30:00Z', usuario: '2', accion: 'RESERVA', entidad: 'Reserva', entidadId: '55',
  resultado: 'EXITO', datos: { operacion: 'CREAR', anidado: { a: 1 } }, correlacion: 'c-1',
};

function renderizar(ruta = '/administrador/auditoria/1') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <Routes>
        <Route path="/administrador/auditoria/:id" element={<DetalleEventoAuditoriaPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function valorDe(termino) {
  return screen.getByText(termino, { selector: 'dt' }).nextElementSibling;
}

describe('DetalleEventoAuditoriaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('consulta el evento por id y muestra todos sus campos', async () => {
    vi.mocked(detalle).mockResolvedValue(EVENTO);

    renderizar();

    expect(screen.getByText('Cargando evento…')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Evento de auditoría 1' })).toBeInTheDocument();
    expect(detalle).toHaveBeenCalledWith('1');
    expect(valorDe('Fecha (Bogotá)')).toHaveTextContent('15/11/2026 22:30:00');
    expect(valorDe('Usuario')).toHaveTextContent('2');
    expect(valorDe('Evento')).toHaveTextContent('RESERVA');
    expect(valorDe('Entidad')).toHaveTextContent('Reserva');
    expect(valorDe('Id de la entidad')).toHaveTextContent('55');
    expect(valorDe('Resultado')).toHaveTextContent('Éxito');
    expect(valorDe('Correlación')).toHaveTextContent('c-1');
  });

  it('muestra los datos como JSON indentado', async () => {
    vi.mocked(detalle).mockResolvedValue(EVENTO);

    renderizar();

    const datos = await screen.findByLabelText('Datos del evento');
    expect(datos.textContent).toBe('{\n  "operacion": "CREAR",\n  "anidado": {\n    "a": 1\n  }\n}');
  });

  it('con usuario, entidad y correlación nulos lo dice en lenguaje claro', async () => {
    vi.mocked(detalle).mockResolvedValue({ ...EVENTO, usuario: null, entidadId: null, correlacion: null, datos: {} });

    renderizar();

    await screen.findByRole('heading', { name: 'Evento de auditoría 1' });
    expect(valorDe('Usuario')).toHaveTextContent('Sin usuario');
    expect(valorDe('Id de la entidad')).toHaveTextContent('Sin id');
    expect(valorDe('Correlación')).toHaveTextContent('Sin correlación');
    expect(screen.getByLabelText('Datos del evento').textContent).toBe('{}');
  });

  it('ante un 404 muestra que no se encontró el evento', async () => {
    vi.mocked(detalle).mockRejectedValue(new ErrorApi(404, 'No encontramos ese evento de auditoría.'));

    renderizar('/administrador/auditoria/999999');

    expect(await screen.findByRole('alert')).toHaveTextContent('No encontramos ese evento de auditoría.');
    expect(screen.queryByRole('term')).not.toBeInTheDocument();
  });

  it.each([new ErrorApi(500, 'Error interno'), new Error('red')])(
    'ante un fallo del servidor o de red muestra un mensaje genérico sin romper', async (error) => {
      vi.mocked(detalle).mockRejectedValue(error);

      renderizar();

      expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el evento. Intenta de nuevo.');
      expect(screen.getByRole('link', { name: 'Volver a la lista' })).toBeInTheDocument();
    },
  );

  it('"Volver a la lista" conserva los filtros y la página de la búsqueda', async () => {
    vi.mocked(detalle).mockResolvedValue(EVENTO);

    renderizar('/administrador/auditoria/1?usuario=2&evento=RESERVA&pagina=1');

    expect(await screen.findByRole('link', { name: 'Volver a la lista' }))
      .toHaveAttribute('href', '/administrador/auditoria?usuario=2&evento=RESERVA&pagina=1');
  });

  it('sin búsqueda vuelve a la lista sin parámetros', async () => {
    vi.mocked(detalle).mockResolvedValue(EVENTO);

    renderizar();

    expect(await screen.findByRole('link', { name: 'Volver a la lista' }))
      .toHaveAttribute('href', '/administrador/auditoria');
  });

  it('es de solo lectura: no ofrece botones ni otros enlaces que el de volver', async () => {
    vi.mocked(detalle).mockResolvedValue(EVENTO);

    renderizar();

    await screen.findByRole('heading', { name: 'Evento de auditoría 1' });
    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(screen.getAllByRole('link').map((enlace) => enlace.textContent)).toEqual(['Volver a la lista']);
  });
});
