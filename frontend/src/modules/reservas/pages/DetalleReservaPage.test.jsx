import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { formatearFecha, formatearPesos } from '../models/reserva.js';

// Intl separa el signo del número con un espacio duro; Testing Library normaliza el DOM pero no lo esperado.
const pesos = (valor) => formatearPesos(valor).replace(/\s/g, ' ');
import { consultarReserva } from '../services/reservaService.js';
import DetalleReservaPage from './DetalleReservaPage.jsx';

vi.mock('../services/reservaService.js', () => ({ consultarReserva: vi.fn() }));

function renderizar(id = '3') {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      initialEntries={[`/huesped/reservas/${id}`]}>
      <Routes>
        <Route path="/huesped/reservas/:id" element={<DetalleReservaPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

function detalle(datos) {
  return {
    id: 3, alojamiento: { id: 5, titulo: 'Casa en la playa', ciudad: 'Santa Marta', imagenPrincipal: null },
    llegada: '2026-12-10', salida: '2026-12-14', estado: 'CONFIRMADA', venceEn: '2026-11-20T15:00:00Z',
    noches: 4, precioBase: 400000, tarifaLimpieza: 30000, tarifaServicio: 40000, descuento: 20000,
    serviciosAdicionales: [{ id: 1, nombre: 'Desayuno' }, { id: 2, nombre: null }],
    totalServiciosAdicionales: 35000, total: 485000, estadoPago: 'APROBADO', transaccionId: 'sim_abc', ...datos,
  };
}

function filaDe(desglose, etiqueta) {
  return within(desglose).getByText(etiqueta).closest('div');
}

describe('DetalleReservaPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra el alojamiento, las fechas y el estado actual de la reserva', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar('3');

    expect(await screen.findByRole('heading', { name: 'Casa en la playa' })).toBeInTheDocument();
    expect(consultarReserva).toHaveBeenCalledWith('3');
    expect(screen.getByText('Santa Marta')).toBeInTheDocument();
    expect(screen.getByText('Confirmada')).toBeInTheDocument();
    expect(screen.getByText(`${formatearFecha('2026-12-10')} – ${formatearFecha('2026-12-14')} · 4 noches`))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a mis reservas' })).toHaveAttribute('href', '/huesped/reservas');
  });

  it('muestra el desglose del pago que quedó fijo al reservar', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    const desglose = await screen.findByLabelText('Desglose del pago');
    expect(filaDe(desglose, 'Noches')).toHaveTextContent('4');
    expect(filaDe(desglose, 'Precio base')).toHaveTextContent(pesos(400000));
    expect(filaDe(desglose, 'Tarifa de limpieza')).toHaveTextContent(pesos(30000));
    expect(filaDe(desglose, 'Tarifa de servicio')).toHaveTextContent(pesos(40000));
    expect(filaDe(desglose, 'Servicios adicionales')).toHaveTextContent(pesos(35000));
    expect(filaDe(desglose, 'Descuentos')).toHaveTextContent(pesos(20000));
    expect(filaDe(desglose, 'Total')).toHaveTextContent(pesos(485000));
  });

  it('lista los servicios adicionales por nombre y avisa si uno ya no está disponible', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    const servicios = await screen.findByRole('list', { name: 'Servicios adicionales incluidos' });
    expect(within(servicios).getAllByRole('listitem').map((item) => item.textContent))
      .toEqual(['Desayuno', 'Servicio no disponible']);
  });

  it('muestra el estado del pago y la transacción cuando la hay', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle());

    renderizar();

    expect(await screen.findByText('Aprobado')).toBeInTheDocument();
    expect(screen.getByText('sim_abc')).toBeInTheDocument();
  });

  it('sin servicios adicionales ni transacción no muestra esas partes', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({
      estado: 'VENCIDA', estadoPago: 'SIN_PAGO', transaccionId: null, serviciosAdicionales: [],
      totalServiciosAdicionales: 0,
    }));

    renderizar();

    expect(await screen.findByText('Vencida')).toBeInTheDocument();
    expect(screen.getByText('Sin pago')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Servicios adicionales incluidos' })).not.toBeInTheDocument();
    expect(screen.queryByText('Transacción')).not.toBeInTheDocument();
  });

  it('si el alojamiento ya no existe lo indica', async () => {
    vi.mocked(consultarReserva).mockResolvedValue(detalle({ alojamiento: null }));

    renderizar();

    expect(await screen.findByRole('heading', { name: 'Alojamiento no disponible' })).toBeInTheDocument();
  });

  it('si la API la rechaza muestra su mensaje, y si falla otra cosa un mensaje general', async () => {
    vi.mocked(consultarReserva).mockRejectedValueOnce(new ErrorApi(404, 'No encontramos esa reserva.'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No encontramos esa reserva.');
    expect(screen.getByRole('link', { name: 'Volver a mis reservas' })).toBeInTheDocument();
  });

  it('si falla sin respuesta de la API muestra un mensaje general', async () => {
    vi.mocked(consultarReserva).mockRejectedValueOnce(new Error('caída'));

    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar la reserva. Intenta de nuevo.');
  });
});
