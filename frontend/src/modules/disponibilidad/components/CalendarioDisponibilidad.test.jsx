import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import CalendarioDisponibilidad from './CalendarioDisponibilidad.jsx';

// Los días de [desde, hasta) con el estado que indique el mapa; el resto, DISPONIBLE.
function calendario({ desde = '2026-12-01', hasta = '2026-12-11', tienePeriodos = true, estados = {} } = {}) {
  const dias = [];
  for (let fecha = new Date(`${desde}T00:00:00Z`); fecha < new Date(`${hasta}T00:00:00Z`);
    fecha = new Date(fecha.getTime() + 24 * 60 * 60 * 1000)) {
    const iso = fecha.toISOString().slice(0, 10);
    dias.push({ fecha: iso, estado: 'DISPONIBLE', ...estados[iso] });
  }
  return { publicacionId: 10, desde, hasta, tienePeriodos, dias };
}

const BASE = {
  '2026-12-05': { estado: 'RESERVADO', reservaId: 77 },
  '2026-12-06': { estado: 'RESERVADO', reservaId: 77 },
  '2026-12-10': { estado: 'BLOQUEADO' },
};

describe('CalendarioDisponibilidad', () => {
  it('muestra el mes de la ventana y cada día con su estado en el nombre accesible', () => {
    render(<CalendarioDisponibilidad calendario={calendario({ estados: BASE })} />);

    expect(screen.getByText('diciembre de 2026')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^1 de diciembre de 2026.*Disponible/ })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^5 de diciembre de 2026.*Reservado/ })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^10 de diciembre de 2026.*Bloqueado/ })).toBeInTheDocument();
  });

  it('pinta el estado que entrega la API sin aplicar reglas propias', () => {
    render(<CalendarioDisponibilidad
      calendario={calendario({ estados: { '2026-12-03': { estado: 'BLOQUEADO' } } })} />);

    expect(screen.getByRole('cell', { name: /^3 de diciembre de 2026.*Bloqueado/ })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^2 de diciembre de 2026.*Disponible/ })).toBeInTheDocument();
  });

  it('deja sin estado los días que la ventana no cubre', () => {
    render(<CalendarioDisponibilidad calendario={calendario({ estados: BASE })} />);

    const fuera = screen.getByRole('cell', { name: /^11 de diciembre de 2026/ });
    expect(fuera.getAttribute('aria-label')).not.toMatch(/Disponible|Reservado|Bloqueado/);
    expect(screen.getByRole('cell', { name: /^31 de diciembre de 2026/ }).getAttribute('aria-label'))
      .not.toMatch(/Disponible|Reservado|Bloqueado/);
  });

  it('explica los tres estados en una leyenda de texto', () => {
    render(<CalendarioDisponibilidad calendario={calendario()} />);

    const leyenda = screen.getByRole('list', { name: 'Leyenda' });
    expect(leyenda).toHaveTextContent('Disponible');
    expect(leyenda).toHaveTextContent('Reservado');
    expect(leyenda).toHaveTextContent('Bloqueado');
  });

  it('avisa que la publicación no tiene periodos y sigue mostrando la grilla', () => {
    const sinPeriodos = calendario({ tienePeriodos: false,
      estados: Object.fromEntries(['01', '02', '03', '04', '05', '06', '07', '08', '09', '10']
        .map((dia) => [`2026-12-${dia}`, { estado: 'BLOQUEADO' }])) });
    render(<CalendarioDisponibilidad calendario={sinPeriodos} />);

    expect(screen.getByRole('status')).toHaveTextContent('aún no tiene periodos de disponibilidad');
    expect(screen.getByRole('cell', { name: /^1 de diciembre de 2026.*Bloqueado/ })).toBeInTheDocument();
  });

  it('con periodos no muestra el aviso, aunque todos los días estén bloqueados', () => {
    const bloqueado = calendario({ estados: Object.fromEntries(['01', '02', '03', '04', '05', '06', '07', '08', '09', '10']
      .map((dia) => [`2026-12-${dia}`, { estado: 'BLOQUEADO' }])) });
    render(<CalendarioDisponibilidad calendario={bloqueado} />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('no muestra el número de reserva si la API no lo entregó', () => {
    render(<CalendarioDisponibilidad calendario={calendario({
      estados: { '2026-12-05': { estado: 'RESERVADO' } } })} />);

    const dia = screen.getByRole('cell', { name: /^5 de diciembre de 2026/ });
    expect(dia).toHaveAccessibleName(/Reservado/);
    expect(dia.getAttribute('aria-label')).not.toMatch(/Reserva \d/);
  });

  it('muestra el número de reserva en el nombre accesible cuando la API lo entrega', () => {
    render(<CalendarioDisponibilidad calendario={calendario({ estados: BASE })} />);

    expect(screen.getByRole('cell', { name: /^5 de diciembre de 2026.*Reservado.*Reserva 77/ })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: /^6 de diciembre de 2026.*Reserva 77/ })).toBeInTheDocument();
  });

  describe('navegación', () => {
    const ventana = () => calendario({ desde: '2026-12-01', hasta: '2027-02-15' });

    it('empieza en el mes de desde y avanza y retrocede de a un mes', () => {
      render(<CalendarioDisponibilidad calendario={ventana()} />);
      expect(screen.getByText('diciembre de 2026')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
      expect(screen.getByText('enero de 2027')).toBeInTheDocument();
      expect(screen.queryByText('diciembre de 2026')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'Mes anterior' }));
      expect(screen.getByText('diciembre de 2026')).toBeInTheDocument();
    });

    it('deshabilita Mes anterior en el primer mes y Mes siguiente en el último', () => {
      render(<CalendarioDisponibilidad calendario={ventana()} />);
      expect(screen.getByRole('button', { name: 'Mes anterior' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Mes siguiente' })).toBeEnabled();

      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));

      expect(screen.getByText('febrero de 2027')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Mes siguiente' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Mes anterior' })).toBeEnabled();
    });

    it('en el último mes deja sin estado los días desde hasta, que es exclusivo', () => {
      render(<CalendarioDisponibilidad calendario={ventana()} />);
      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));

      expect(screen.getByRole('cell', { name: /^14 de febrero de 2027.*Disponible/ })).toBeInTheDocument();
      expect(screen.getByRole('cell', { name: /^15 de febrero de 2027/ }).getAttribute('aria-label'))
        .not.toMatch(/Disponible|Reservado|Bloqueado/);
      expect(screen.getByRole('cell', { name: /^28 de febrero de 2027/ }).getAttribute('aria-label'))
        .not.toMatch(/Disponible|Reservado|Bloqueado/);
    });

    it('en un mes parcial deja sin estado los días anteriores a desde', () => {
      render(<CalendarioDisponibilidad calendario={calendario({ desde: '2026-12-15', hasta: '2026-12-20' })} />);

      expect(screen.getByRole('cell', { name: /^14 de diciembre de 2026/ }).getAttribute('aria-label'))
        .not.toMatch(/Disponible|Reservado|Bloqueado/);
      expect(screen.getByRole('cell', { name: /^15 de diciembre de 2026.*Disponible/ })).toBeInTheDocument();
    });

    it('conserva el mes mostrado cuando llega un calendario nuevo de la misma ventana', () => {
      const { rerender } = render(<CalendarioDisponibilidad calendario={ventana()} />);
      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));

      rerender(<CalendarioDisponibilidad calendario={ventana()} />);

      expect(screen.getByText('enero de 2027')).toBeInTheDocument();
    });

    it('vuelve al primer mes si el mes mostrado ya no existe en el calendario nuevo', () => {
      const { rerender } = render(<CalendarioDisponibilidad calendario={ventana()} />);
      fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));

      rerender(<CalendarioDisponibilidad calendario={calendario({ desde: '2027-03-01', hasta: '2027-03-10' })} />);

      expect(screen.getByText('marzo de 2027')).toBeInTheDocument();
    });
  });

  it('sin días no muestra grilla pero sí la leyenda', () => {
    render(<CalendarioDisponibilidad calendario={calendario({ desde: '2026-12-01', hasta: '2026-12-01' })} />);

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Leyenda' })).toBeInTheDocument();
  });
});
