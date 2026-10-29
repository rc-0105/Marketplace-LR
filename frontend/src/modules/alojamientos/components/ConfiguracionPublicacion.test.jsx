import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { actualizarConfiguracion, listarPoliticasCancelacion } from '../services/alojamientosService.js';
import ConfiguracionPublicacion from './ConfiguracionPublicacion.jsx';

vi.mock('../services/alojamientosService.js', () => ({
  actualizarConfiguracion: vi.fn(),
  listarPoliticasCancelacion: vi.fn(),
}));

const POLITICAS = [
  { id: 1, nombre: 'Flexible', descripcion: 'Devolución total hasta 1 día antes.', diasSinCosto: 1, porcentajeDevolucion: 50 },
  { id: 2, nombre: 'Moderada', descripcion: 'Devolución total hasta 5 días antes.', diasSinCosto: 5, porcentajeDevolucion: 50 },
];

const SIN_CONFIGURAR = {
  id: 5, tarifaLimpieza: null, modoReserva: 'INMEDIATA', politicaCancelacion: null, serviciosAdicionales: [],
};

const CONFIGURADA = {
  id: 5,
  tarifaLimpieza: 40000,
  modoReserva: 'APROBACION',
  politicaCancelacion: POLITICAS[1],
  serviciosAdicionales: [{ id: 7, nombre: 'Desayuno', costo: 15000 }],
};

function renderizar(publicacion, alGuardar = vi.fn()) {
  render(<ConfiguracionPublicacion publicacionId={5} publicacion={publicacion} alGuardar={alGuardar} />);
}

function escribir(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

function agregarAdicional(nombre, costo) {
  escribir('Nombre del servicio adicional', nombre);
  escribir('Costo del servicio adicional (COP)', costo);
  fireEvent.click(screen.getByRole('button', { name: 'Agregar servicio' }));
}

function adicionalesEnPantalla() {
  return within(screen.getByRole('list', { name: 'Servicios adicionales' })).queryAllByRole('listitem')
    .map((item) => item.textContent);
}

describe('ConfiguracionPublicacion', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listarPoliticasCancelacion).mockResolvedValue(POLITICAS);
  });

  it('muestra las condiciones guardadas: tarifa, modo, política con su descripción y servicios adicionales', async () => {
    renderizar(CONFIGURADA);

    expect(await screen.findByRole('option', { name: 'Moderada' })).toBeInTheDocument();
    expect(screen.getByLabelText('Tarifa de limpieza (COP)')).toHaveValue('40000');
    expect(screen.getByRole('radio', { name: 'Requiere mi aprobación' })).toBeChecked();
    expect(screen.getByLabelText('Política de cancelación')).toHaveValue('2');
    expect(screen.getByText('Devolución total hasta 5 días antes.')).toBeInTheDocument();
    expect(adicionalesEnPantalla()).toEqual([expect.stringContaining('Desayuno')]);
  });

  it('sin modo configurado marca la reserva inmediata', async () => {
    renderizar(SIN_CONFIGURAR);

    await screen.findByRole('option', { name: 'Flexible' });
    expect(screen.getByRole('radio', { name: 'Reserva inmediata' })).toBeChecked();
    expect(screen.getByLabelText('Política de cancelación')).toHaveValue('');
    expect(screen.getByText('Todavía no hay servicios adicionales.')).toBeInTheDocument();
  });

  it('guarda las condiciones elegidas y entrega la publicación actualizada', async () => {
    const actualizada = { ...CONFIGURADA, tarifaLimpieza: 50000 };
    vi.mocked(actualizarConfiguracion).mockResolvedValue(actualizada);
    const alGuardar = vi.fn();
    renderizar(SIN_CONFIGURAR, alGuardar);
    await screen.findByRole('option', { name: 'Moderada' });

    escribir('Tarifa de limpieza (COP)', ' 50000 ');
    fireEvent.click(screen.getByRole('radio', { name: 'Requiere mi aprobación' }));
    escribir('Política de cancelación', '2');
    agregarAdicional(' Desayuno ', '15000');
    agregarAdicional('Traslado', '60000.50');
    fireEvent.click(screen.getByRole('button', { name: 'Quitar Traslado' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar condiciones' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos las condiciones.');
    expect(actualizarConfiguracion).toHaveBeenCalledWith(5, {
      tarifaLimpieza: 50000,
      modoReserva: 'APROBACION',
      politicaCancelacion: 2,
      serviciosAdicionales: [{ nombre: 'Desayuno', costo: 15000 }],
    });
    expect(alGuardar).toHaveBeenCalledWith(actualizada);
  });

  it('sin tarifa ni política las envía vacías', async () => {
    vi.mocked(actualizarConfiguracion).mockResolvedValue(SIN_CONFIGURAR);
    renderizar(CONFIGURADA);
    await screen.findByRole('option', { name: 'Moderada' });

    escribir('Tarifa de limpieza (COP)', '');
    escribir('Política de cancelación', '');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar condiciones' }));

    await screen.findByRole('status');
    expect(actualizarConfiguracion).toHaveBeenCalledWith(5, {
      tarifaLimpieza: null,
      modoReserva: 'APROBACION',
      politicaCancelacion: null,
      serviciosAdicionales: [{ nombre: 'Desayuno', costo: 15000 }],
    });
  });

  it('no guarda una tarifa con formato inválido', async () => {
    renderizar(SIN_CONFIGURAR);
    await screen.findByRole('option', { name: 'Flexible' });

    escribir('Tarifa de limpieza (COP)', '-100');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar condiciones' }));

    expect(screen.getByText('La tarifa de limpieza debe ser cero o más, con máximo 2 decimales.')).toBeInTheDocument();
    expect(actualizarConfiguracion).not.toHaveBeenCalled();
  });

  it.each([
    ['', '15000', 'Escribe el nombre del servicio adicional.'],
    ['Desayuno', '0', 'El costo debe ser mayor que cero, con máximo 2 decimales.'],
    ['Desayuno', 'gratis', 'El costo debe ser mayor que cero, con máximo 2 decimales.'],
  ])('no agrega el servicio adicional %j con costo %j', async (nombre, costo, mensaje) => {
    renderizar(SIN_CONFIGURAR);
    await screen.findByRole('option', { name: 'Flexible' });

    agregarAdicional(nombre, costo);

    expect(screen.getByText(mensaje)).toBeInTheDocument();
    expect(screen.getByText('Todavía no hay servicios adicionales.')).toBeInTheDocument();
  });

  it('muestra el error de la API si no se pueden guardar', async () => {
    vi.mocked(actualizarConfiguracion).mockRejectedValue(
      new ErrorApi(400, 'Hay servicios adicionales con el mismo nombre.'),
    );
    renderizar(SIN_CONFIGURAR);
    await screen.findByRole('option', { name: 'Flexible' });

    fireEvent.click(screen.getByRole('button', { name: 'Guardar condiciones' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Hay servicios adicionales con el mismo nombre.');
  });

  it('avisa si no se pueden cargar las políticas de cancelación', async () => {
    vi.mocked(listarPoliticasCancelacion).mockRejectedValue(new ErrorApi(500, 'Error.'));
    renderizar(SIN_CONFIGURAR);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar las políticas de cancelación.');
  });

  it('avisa que hay cambios sin guardar hasta que se guardan', async () => {
    vi.mocked(actualizarConfiguracion).mockResolvedValue({ ...SIN_CONFIGURAR, modoReserva: 'APROBACION' });
    renderizar(SIN_CONFIGURAR);
    await screen.findByRole('option', { name: 'Flexible' });

    expect(screen.queryByText('Hay cambios en las condiciones sin guardar.')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'Requiere mi aprobación' }));
    expect(screen.getByText('Hay cambios en las condiciones sin guardar.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Guardar condiciones' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos las condiciones.');
    expect(screen.queryByText('Hay cambios en las condiciones sin guardar.')).not.toBeInTheDocument();
  });
});
