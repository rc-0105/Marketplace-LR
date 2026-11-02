import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import FormularioPeriodo from './FormularioPeriodo.jsx';

function renderizar(props = {}) {
  const alGuardar = props.alGuardar ?? vi.fn().mockResolvedValue(undefined);
  render(
    <FormularioPeriodo id="nuevo" titulo="Agregar periodo" etiquetaEnviar="Agregar periodo" alGuardar={alGuardar}
      {...props} />,
  );
  return alGuardar;
}

function escribirFechas(inicio, fin) {
  fireEvent.change(screen.getByLabelText('Fecha inicial'), { target: { value: inicio } });
  fireEvent.change(screen.getByLabelText('Fecha final'), { target: { value: fin } });
}

describe('FormularioPeriodo', () => {
  it('envía las fechas elegidas y deja el formulario vacío para agregar otro', async () => {
    const alGuardar = renderizar();

    escribirFechas('2027-01-10', '2027-01-15');
    fireEvent.click(screen.getByRole('button', { name: 'Agregar periodo' }));

    await waitFor(() => expect(alGuardar).toHaveBeenCalledWith({ fechaInicio: '2027-01-10', fechaFin: '2027-01-15' }));
    await waitFor(() => expect(screen.getByLabelText('Fecha inicial')).toHaveValue(''));
    expect(screen.getByLabelText('Fecha final')).toHaveValue('');
  });

  it('pide las dos fechas antes de enviar', async () => {
    const alGuardar = renderizar();

    fireEvent.click(screen.getByRole('button', { name: 'Agregar periodo' }));

    expect(await screen.findByText('Elige la fecha inicial.')).toBeInTheDocument();
    expect(screen.getByText('Elige la fecha final.')).toBeInTheDocument();
    expect(alGuardar).not.toHaveBeenCalled();
  });

  it('muestra el motivo con que la API rechaza el periodo y conserva las fechas', async () => {
    const alGuardar = vi.fn().mockRejectedValue(
      new ErrorApi(400, 'La fecha final debe ser posterior a la fecha inicial.'));
    renderizar({ alGuardar });

    escribirFechas('2027-01-15', '2027-01-10');
    fireEvent.click(screen.getByRole('button', { name: 'Agregar periodo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha final debe ser posterior a la fecha inicial.');
    expect(screen.getByLabelText('Fecha inicial')).toHaveValue('2027-01-15');
  });

  it('avisa con un mensaje genérico cuando no hay respuesta del servidor', async () => {
    const alGuardar = vi.fn().mockRejectedValue(new Error('caída'));
    renderizar({ alGuardar });

    escribirFechas('2027-01-10', '2027-01-15');
    fireEvent.click(screen.getByRole('button', { name: 'Agregar periodo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos guardar el periodo. Intenta de nuevo.');
  });

  it('al editar arranca con las fechas actuales y permite cancelar', async () => {
    const alCancelar = vi.fn();
    const alGuardar = renderizar({
      id: 'periodo-5',
      titulo: 'Editar periodo',
      etiquetaEnviar: 'Guardar cambios',
      inicial: { fechaInicio: '2027-01-10', fechaFin: '2027-01-15' },
      alCancelar,
    });

    expect(screen.getByLabelText('Fecha inicial')).toHaveValue('2027-01-10');
    fireEvent.change(screen.getByLabelText('Fecha final'), { target: { value: '2027-01-20' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await waitFor(() => expect(alGuardar).toHaveBeenCalledWith({ fechaInicio: '2027-01-10', fechaFin: '2027-01-20' }));
    expect(screen.getByLabelText('Fecha final')).toHaveValue('2027-01-20');

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(alCancelar).toHaveBeenCalled();
  });
});
