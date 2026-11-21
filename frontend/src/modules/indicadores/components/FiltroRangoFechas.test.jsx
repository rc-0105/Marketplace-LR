import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import FiltroRangoFechas from './FiltroRangoFechas.jsx';

describe('FiltroRangoFechas', () => {
  it('parte de los valores recibidos y emite solo las fechas con valor', () => {
    const alAplicar = vi.fn();
    render(<FiltroRangoFechas valores={{ desde: '2026-11-01' }} alAplicar={alAplicar} />);

    expect(screen.getByLabelText('Desde')).toHaveValue('2026-11-01');
    expect(screen.getByLabelText('Hasta')).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar' }));

    expect(alAplicar).toHaveBeenCalledWith({ desde: '2026-11-01' });
  });

  it('no deja aplicar un rango con desde posterior a hasta', () => {
    const alAplicar = vi.fn();
    render(<FiltroRangoFechas alAplicar={alAplicar} />);

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-30' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-11-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar' }));

    expect(screen.getByRole('alert')).toHaveTextContent('La fecha inicial no puede ser posterior a la final.');
    expect(screen.getByLabelText('Desde')).toHaveAttribute('aria-invalid', 'true');
    expect(alAplicar).not.toHaveBeenCalled();
  });

  it('"Ver todo" vacía las fechas y emite un rango vacío', () => {
    const alAplicar = vi.fn();
    render(<FiltroRangoFechas valores={{ desde: '2026-11-01', hasta: '2026-11-30' }} alAplicar={alAplicar} />);

    fireEvent.click(screen.getByRole('button', { name: 'Ver todo' }));

    expect(screen.getByLabelText('Desde')).toHaveValue('');
    expect(screen.getByLabelText('Hasta')).toHaveValue('');
    expect(alAplicar).toHaveBeenCalledWith({});
  });
});
