import PropTypes from 'prop-types';

// Trazos de los íconos de la aplicación. Son decorativos: el texto que los acompaña ya dice lo que significan.
const TRAZOS = {
  reservas: 'M7 3v3M17 3v3M4 9h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z',
  publicaciones: 'M4 11l8-7 8 7M6 9.5V20h12V9.5M10 20v-5h4v5',
  revisar: 'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6zM9 12l2 2 4-4',
  moderacion: 'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6zM9.5 9.5l5 5M14.5 9.5l-5 5',
  resenas: 'M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z',
  usuarios: 'M16 20v-1a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v1M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM20 20v-1a4 4 0 0 0-3-3.9M16 4.2a3.5 3.5 0 0 1 0 6.6',
  indicadores: 'M4 20h16M7 16v-5M12 16V6M17 16v-8',
  auditoria: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M9 13h6M9 17h6',
  reportes: 'M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM14 3v5h5M12 11v6M9.5 14.5L12 17l2.5-2.5',
  cuenta: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0',
  salir: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10',
  flecha: 'M5 12h14M13 6l6 6-6 6',
  ocultar: 'M3 3l18 18M10.6 6.1A9.8 9.8 0 0 1 12 6c5 0 9 6 9 6a16 16 0 0 1-2.6 3.2M6.6 7.6C4.3 9.2 3 12 3 12s4 6 9 6a8.6 8.6 0 0 0 4-1',
  buscar:'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3',
  bloquear: 'M5 11h14v10H5zM8 11V8a4 4 0 0 1 8 0v3',
  aprobar: 'M5 12.5l4.5 4.5L19 7',
  devolver: 'M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3',
};

export default function Icono({ nombre, className }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none"
      stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d={TRAZOS[nombre]} />
    </svg>
  );
}

Icono.propTypes = {
  nombre: PropTypes.oneOf(Object.keys(TRAZOS)).isRequired,
  className: PropTypes.string,
};
