import { describe, expect, it } from 'vitest';
import { calendarioDesdeApi, ETIQUETAS_ESTADO, mesesDe } from './calendario.js';

function dia(fecha, estado = 'DISPONIBLE', extra = {}) {
  return { fecha, estado, ...extra };
}

describe('calendario', () => {
  it('convierte la respuesta de la API sin inventar reservaId en los días que no lo traen', () => {
    const modelo = calendarioDesdeApi({
      publicacionId: 10,
      desde: '2026-12-01',
      hasta: '2026-12-04',
      tienePeriodos: true,
      dias: [
        { fecha: '2026-12-01', estado: 'DISPONIBLE', otro: 1 },
        { fecha: '2026-12-02', estado: 'RESERVADO', reservaId: 77 },
        { fecha: '2026-12-03', estado: 'BLOQUEADO', reservaId: null },
      ],
    });

    expect(modelo).toEqual({
      publicacionId: 10,
      desde: '2026-12-01',
      hasta: '2026-12-04',
      tienePeriodos: true,
      dias: [
        { fecha: '2026-12-01', estado: 'DISPONIBLE' },
        { fecha: '2026-12-02', estado: 'RESERVADO', reservaId: 77 },
        { fecha: '2026-12-03', estado: 'BLOQUEADO' },
      ],
    });
    expect(modelo.dias[0]).not.toHaveProperty('reservaId');
    expect(modelo.dias[2]).not.toHaveProperty('reservaId');
  });

  it('nombra los tres estados en texto', () => {
    expect(ETIQUETAS_ESTADO).toEqual({
      DISPONIBLE: 'Disponible',
      RESERVADO: 'Reservado',
      BLOQUEADO: 'Bloqueado',
    });
  });

  describe('mesesDe', () => {
    it('devuelve una lista vacía si no hay días', () => {
      expect(mesesDe([])).toEqual([]);
    });

    it('titula cada mes en español y lo identifica por año y mes', () => {
      const meses = mesesDe([dia('2026-12-31'), dia('2027-01-01')]);

      expect(meses.map((mes) => [mes.clave, mes.titulo])).toEqual([
        ['2026-12', 'diciembre de 2026'],
        ['2027-01', 'enero de 2027'],
      ]);
    });

    it('arma las semanas empezando en lunes y deja vacío el desfase del primer día', () => {
      // 1 de diciembre de 2026 es martes: la primera semana empieza con una celda vacía.
      const [diciembre] = mesesDe([dia('2026-12-01')]);

      expect(diciembre.semanas[0][0]).toBeNull();
      expect(diciembre.semanas[0][1].fecha).toBe('2026-12-01');
      expect(diciembre.semanas.every((semana) => semana.length === 7)).toBe(true);
    });

    it('un mes que empieza en lunes no lleva desfase', () => {
      // 1 de febrero de 2027 es lunes.
      const [febrero] = mesesDe([dia('2027-02-01')]);

      expect(febrero.semanas[0][0].fecha).toBe('2027-02-01');
    });

    it('completa el mes entero y deja sin estado los días que la API no devolvió', () => {
      const [diciembre] = mesesDe([dia('2026-12-15', 'RESERVADO', { reservaId: 77 }), dia('2026-12-16')]);
      const celdas = diciembre.semanas.flat().filter(Boolean);

      expect(celdas).toHaveLength(31);
      expect(celdas[0]).toEqual({ fecha: '2026-12-01', estado: null });
      expect(celdas[14]).toEqual({ fecha: '2026-12-15', estado: 'RESERVADO', reservaId: 77 });
      expect(celdas[16]).toEqual({ fecha: '2026-12-17', estado: null });
    });

    it('cuenta bien los días de febrero y cierra la última semana con celdas vacías', () => {
      const [febrero] = mesesDe([dia('2027-02-10')]);
      expect(febrero.semanas.flat().filter(Boolean)).toHaveLength(28);

      // El 31 de diciembre de 2026 es jueves: el viernes, sábado y domingo quedan vacíos.
      const [diciembre] = mesesDe([dia('2026-12-10')]);
      const ultima = diciembre.semanas.at(-1);
      expect(ultima[3].fecha).toBe('2026-12-31');
      expect(ultima.slice(4)).toEqual([null, null, null]);
    });
  });
});
