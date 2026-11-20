import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FiltrosAuditoria from './FiltrosAuditoria.jsx';

function llenar(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

describe('FiltrosAuditoria', () => {
  it('ofrece usuario, evento, entidad, id de entidad y el rango de fechas', () => {
    render(<FiltrosAuditoria alAplicar={vi.fn()} />);

    ['Usuario', 'Entidad', 'Id de la entidad', 'Desde', 'Hasta'].forEach((etiqueta) => {
      expect(screen.getByLabelText(etiqueta)).toBeInTheDocument();
    });
    const opciones = within(screen.getByLabelText('Evento')).getAllByRole('option').map((o) => o.textContent);
    expect(opciones).toEqual([
      'Todos', 'AUTENTICACION', 'PUBLICACION', 'RESERVA', 'CANCELACION', 'RESENA', 'ACCION_ADMINISTRATIVA',
    ]);
  });

  it('al aplicar emite solo los filtros con valor', () => {
    const alAplicar = vi.fn();
    render(<FiltrosAuditoria alAplicar={alAplicar} />);

    llenar('Usuario', '2');
    llenar('Evento', 'RESERVA');
    llenar('Entidad', '   ');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(alAplicar).toHaveBeenCalledTimes(1);
    expect(alAplicar).toHaveBeenCalledWith({ usuario: '2', evento: 'RESERVA' });
  });

  it('sin nada escrito emite un objeto vacío', () => {
    const alAplicar = vi.fn();
    render(<FiltrosAuditoria alAplicar={alAplicar} />);

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(alAplicar).toHaveBeenCalledWith({});
  });

  it('parte de los valores que recibe', () => {
    render(<FiltrosAuditoria valores={{ usuario: '9', evento: 'RESENA', desde: '2026-11-01' }} alAplicar={vi.fn()} />);

    expect(screen.getByLabelText('Usuario')).toHaveValue('9');
    expect(screen.getByLabelText('Evento')).toHaveValue('RESENA');
    expect(screen.getByLabelText('Desde')).toHaveValue('2026-11-01');
  });

  it('con desde posterior a hasta muestra el error y no emite', () => {
    const alAplicar = vi.fn();
    render(<FiltrosAuditoria alAplicar={alAplicar} />);

    llenar('Desde', '2026-11-20');
    llenar('Hasta', '2026-11-10');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(screen.getByRole('alert')).toHaveTextContent('La fecha inicial no puede ser posterior a la final.');
    expect(screen.getByLabelText('Desde')).toBeInvalid();
    expect(alAplicar).not.toHaveBeenCalled();
  });

  it('con desde igual a hasta emite y al corregir el rango quita el error', () => {
    const alAplicar = vi.fn();
    render(<FiltrosAuditoria alAplicar={alAplicar} />);

    llenar('Desde', '2026-11-20');
    llenar('Hasta', '2026-11-10');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));
    llenar('Hasta', '2026-11-20');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(alAplicar).toHaveBeenCalledWith({ desde: '2026-11-20', hasta: '2026-11-20' });
  });

  it('no ofrece botones de crear, editar, eliminar ni limpiar registros', () => {
    render(<FiltrosAuditoria alAplicar={vi.fn()} />);

    const botones = screen.getAllByRole('button').map((boton) => boton.textContent);
    expect(botones).toEqual(['Aplicar filtros']);
  });
});
