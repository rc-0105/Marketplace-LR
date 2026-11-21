import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { formatearFecha, formatearPesos } from '../models/reserva.js';

// Intl separa el signo del número con un espacio duro; Testing Library normaliza el DOM pero no lo esperado.
const pesos = (valor) => formatearPesos(valor).replace(/\s/g, ' ');
import { listarMisReservas } from '../services/reservaService.js';
import MisReservasPage from './MisReservasPage.jsx';

vi.mock('../services/reservaService.js', () => ({ listarMisReservas: vi.fn() }));

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <MisReservasPage />
    </MemoryRouter>,
  );
}

function resumen(datos) {
  return {
    id: 1, publicacionId: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null,
    llegada: '2026-12-10', salida: '2026-12-14', noches: 4, estado: 'CONFIRMADA', total: 485000, ...datos,
  };
}

describe('MisReservasPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra las próximas y las pasadas con su estado, fechas, noches y total', async () => {
    vi.mocked(listarMisReservas).mockResolvedValue({
      futuras: [resumen({ id: 2, titulo: 'Cabaña en el bosque', estado: 'PENDIENTE', noches: 1,
        llegada: '2026-12-20', salida: '2026-12-21', total: 150000 })],
      pasadas: [resumen({ id: 1, estado: 'CANCELADA' })],
    });

    renderizar();

    const proximas = await screen.findByRole('region', { name: 'Próximas' });
    const cabana = within(proximas).getByRole('listitem');
    expect(within(cabana).getByRole('heading', { name: 'Cabaña en el bosque' })).toBeInTheDocument();
    expect(within(cabana).getByText('Pendiente de pago')).toBeInTheDocument();
    expect(within(cabana).getByText(`${formatearFecha('2026-12-20')} – ${formatearFecha('2026-12-21')} · 1 noche`))
      .toBeInTheDocument();
    expect(within(cabana).getByText(pesos(150000))).toBeInTheDocument();
    expect(within(cabana).getByRole('link', { name: 'Ver detalle de Cabaña en el bosque' }))
      .toHaveAttribute('href', '/huesped/reservas/2');

    const pasadas = screen.getByRole('region', { name: 'Pasadas' });
    const casa = within(pasadas).getByRole('listitem');
    expect(within(casa).getByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(within(casa).getByText('Cancelada')).toBeInTheDocument();
    expect(within(casa).getByText(`${formatearFecha('2026-12-10')} – ${formatearFecha('2026-12-14')} · 4 noches`))
      .toBeInTheDocument();
    expect(within(casa).getByText(pesos(485000))).toBeInTheDocument();
  });

  it('si una de las listas está vacía lo dice en esa sección', async () => {
    vi.mocked(listarMisReservas).mockResolvedValue({ futuras: [resumen({ id: 2 })], pasadas: [] });

    renderizar();

    const pasadas = await screen.findByRole('region', { name: 'Pasadas' });
    expect(within(pasadas).getByText('No tienes reservas pasadas.')).toBeInTheDocument();
  });

  it('sin ninguna reserva invita a buscar alojamiento', async () => {
    vi.mocked(listarMisReservas).mockResolvedValue({ futuras: [], pasadas: [] });

    renderizar();

    expect(await screen.findByText('Todavía no tienes reservas.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buscar alojamiento' })).toHaveAttribute('href', '/huesped/busqueda');
    expect(screen.queryByRole('region', { name: 'Próximas' })).not.toBeInTheDocument();
  });

  it('si el alojamiento ya no existe lo indica en lugar del título', async () => {
    vi.mocked(listarMisReservas).mockResolvedValue({
      futuras: [resumen({ id: 2, titulo: null, ciudad: null })], pasadas: [],
    });

    renderizar();

    expect(await screen.findByRole('heading', { name: 'Alojamiento no disponible' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver detalle de Alojamiento no disponible' }))
      .toHaveAttribute('href', '/huesped/reservas/2');
  });

  it('mientras carga lo indica y si falla muestra el error', async () => {
    vi.mocked(listarMisReservas).mockRejectedValue(new Error('caída'));

    renderizar();

    expect(screen.getByText('Cargando tus reservas…')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus reservas. Intenta de nuevo.');
  });
});
