import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { consultarDashboard } from '../services/dashboardService.js';
import DashboardPage from './DashboardPage.jsx';

vi.mock('../services/dashboardService.js', () => ({ consultarDashboard: vi.fn() }));

const DASHBOARD = {
  desde: null,
  hasta: null,
  usuariosPorRol: { HUESPED: 5, ANFITRION: 2, ADMINISTRADOR: 1 },
  publicacionesActivas: 3,
  reservasPorEstado: { PENDIENTE: 1, CONFIRMADA: 2, CANCELADA: 1, VENCIDA: 0, COMPLETADA: 1, EN_REVISION_MANUAL: 1 },
  ingresosEstimados: 1250000,
  ciudadesMayorDemanda: [{ ciudad: 'Bogotá', reservas: 4 }, { ciudad: 'Cali', reservas: 1 }],
};

// Intl separa la moneda con un espacio duro; se normaliza para comparar texto.
function texto(elemento) {
  return elemento.textContent.replace(/\u00a0/g, ' ');
}

function renderizar(ruta = '/') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <DashboardPage />
    </MemoryRouter>,
  );
}

describe('DashboardPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra la carga y luego las cifras, los conteos por rol y estado y el ranking de ciudades', async () => {
    vi.mocked(consultarDashboard).mockResolvedValue(DASHBOARD);

    renderizar();

    expect(screen.getByText('Cargando indicadores…')).toBeInTheDocument();
    expect(await screen.findByText('Usuarios')).toBeInTheDocument();
    expect(consultarDashboard).toHaveBeenCalledWith({});
    expect(screen.getByText(/Todo el histórico\./)).toBeInTheDocument();

    const cifras = Object.fromEntries(screen.getAllByRole('term')
      .map((termino) => [termino.textContent, texto(termino.nextElementSibling)]));
    expect(cifras).toEqual({
      Usuarios: '8', 'Publicaciones activas': '3', Reservas: '6', 'Ingresos estimados': '$ 1.250.000',
    });

    const usuarios = screen.getByRole('region', { name: 'Usuarios por rol' });
    expect(within(usuarios).getAllByRole('row').map((fila) => fila.textContent))
      .toEqual(['Huéspedes5', 'Anfitriones2', 'Administradores1', 'Total8']);
    const reservas = screen.getByRole('region', { name: 'Reservas por estado' });
    expect(within(reservas).getAllByRole('row').map((fila) => fila.textContent))
      .toEqual(['Pendientes1', 'Confirmadas2', 'Canceladas1', 'Vencidas0', 'Completadas1', 'En revisión manual1',
        'Total6']);
    const ciudades = screen.getByRole('region', { name: 'Ciudades con mayor demanda' });
    expect(within(ciudades).getAllByRole('listitem').map((item) => item.textContent))
      .toEqual(['Bogotá4 reservas', 'Cali1 reserva']);
  });

  it('sin reservas en el rango lo dice en lugar del ranking', async () => {
    vi.mocked(consultarDashboard).mockResolvedValue({ ...DASHBOARD, ciudadesMayorDemanda: [] });

    renderizar();

    expect(await screen.findByText('Todavía no hay reservas en este rango.')).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('toma el rango de la dirección, lo describe y lo deja en el filtro', async () => {
    vi.mocked(consultarDashboard).mockResolvedValue({ ...DASHBOARD, desde: '2026-11-01', hasta: '2026-11-30' });

    renderizar('/?desde=2026-11-01&hasta=2026-11-30');

    expect(await screen.findByText('Usuarios')).toBeInTheDocument();
    expect(consultarDashboard).toHaveBeenCalledWith({ desde: '2026-11-01', hasta: '2026-11-30' });
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-11-01');
    expect(screen.getByLabelText('Hasta')).toHaveValue('2026-11-30');
    expect(screen.getByText(/^Cada indicador cuenta/).textContent).toMatch(/Del .+ al .+\.$/);
  });

  it('al aplicar un rango recalcula todos los indicadores sobre él', async () => {
    vi.mocked(consultarDashboard).mockResolvedValue(DASHBOARD);
    renderizar();
    await screen.findByText('Usuarios');

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar' }));

    expect(await screen.findByText(/^Cada indicador cuenta.*Desde el /)).toBeInTheDocument();
    expect(consultarDashboard).toHaveBeenLastCalledWith({ desde: '2026-11-01' });
  });

  it('con un 400 muestra el mensaje de la API', async () => {
    vi.mocked(consultarDashboard).mockRejectedValue(
      new ErrorApi(400, 'La fecha desde no puede ser posterior a la fecha hasta.'));

    renderizar('/?desde=2026-11-30&hasta=2026-11-01');

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha desde no puede ser posterior a la fecha hasta.');
    expect(screen.queryByText('Usuarios')).not.toBeInTheDocument();
  });

  it('con otro error muestra un mensaje genérico', async () => {
    vi.mocked(consultarDashboard).mockRejectedValue(new ErrorApi(500, 'Error interno'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los indicadores. Intenta de nuevo.');
  });
});
