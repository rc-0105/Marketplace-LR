import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, get, post } from '../shared/services/http.js';
import { guardarSesion, limpiarSesion, obtenerSesion } from '../shared/services/sesion.js';
import { listarNotificaciones } from '../modules/notificaciones/services/notificacionesService.js';
import App from './App.jsx';

vi.mock('../shared/services/http.js', async (importOriginal) => ({
  ...(await importOriginal()),
  get: vi.fn(),
  post: vi.fn(),
}));

// La campana del encabezado consulta notificaciones con sesión: se aísla para no tocar las llamadas a `get`.
vi.mock('../modules/notificaciones/services/notificacionesService.js', () => ({
  listarNotificaciones: vi.fn(),
  marcarLeida: vi.fn(),
}));

function iniciar(rol) {
  const expiraEn = new Date(Date.now() + 3600_000).toISOString();
  guardarSesion({ token: 't', expiraEn, usuario: { id: 1, nombre: 'Ana', correo: 'a@b.co', rol, activa: true } });
}

// Cada rol vuelve a su inicio: el anfitrión a Mis publicaciones, el huésped a su ventana principal
// (el saludo con el buscador) y el administrador a su panel.
function esperarInicioDe(rol, nombre) {
  if (rol === 'ANFITRION') {
    expect(screen.getByRole('heading', { name: 'Mis publicaciones' })).toBeInTheDocument();
  } else if (rol === 'HUESPED') {
    expect(screen.getByRole('heading', { name: 'Hola, Ana' })).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Buscar alojamientos' })).toBeInTheDocument();
  } else {
    expect(screen.getByText(`Ingresaste como ${nombre}.`)).toBeInTheDocument();
  }
}

function renderizarEn(ruta) {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[ruta]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App', () => {
  beforeEach(() => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [] });
  });

  afterEach(() => {
    limpiarSesion();
    vi.clearAllMocks();
  });

  it.each(['HUESPED', 'ANFITRION', 'ADMINISTRADOR'])('muestra las notificaciones a un %s con sesión', async (rol) => {
    vi.mocked(listarNotificaciones).mockResolvedValue({ noLeidas: 0, notificaciones: [] });
    iniciar(rol);
    renderizarEn('/notificaciones');
    expect(screen.getByRole('heading', { name: 'Notificaciones' })).toBeInTheDocument();
    expect(await screen.findByText('No tienes notificaciones.')).toBeInTheDocument();
  });

  it('sin sesión las notificaciones llevan al inicio de sesión', () => {
    renderizarEn('/notificaciones');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(listarNotificaciones).not.toHaveBeenCalled();
  });

  it('muestra el Inicio con su título en la ruta raíz', () => {
    vi.mocked(get).mockResolvedValue([]);
    renderizarEn('/');
    expect(screen.getByRole('heading', { name: 'Encuentra tu alojamiento' })).toBeInTheDocument();
  });

  it('ofrece crear una cuenta desde el inicio', () => {
    vi.mocked(get).mockResolvedValue([]);
    renderizarEn('/');
    expect(screen.getByRole('link', { name: 'Crear una cuenta' })).toHaveAttribute('href', '/registro');
  });

  it('muestra el registro en su ruta', () => {
    renderizarEn('/registro');
    expect(screen.getByRole('heading', { name: 'Regístrate con nosotros' })).toBeInTheDocument();
  });

  it('muestra el encabezado con la marca en todas las páginas', () => {
    renderizarEn('/registro');
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir al inicio' })).toBeInTheDocument();
  });

  it.each([
    ['HUESPED', '/huesped', 'Huésped'],
    ['ADMINISTRADOR', '/administrador', 'Administrador'],
  ])('muestra el panel de %s en su ruta', (rol, ruta, nombre) => {
    iniciar(rol);
    renderizarEn(ruta);
    esperarInicioDe(rol, nombre);
  });

  it('el inicio del anfitrión es Mis publicaciones: /anfitrion lleva directo ahí', async () => {
    vi.mocked(get).mockResolvedValue([]);
    iniciar('ANFITRION');
    renderizarEn('/anfitrion');
    expect(await screen.findByRole('heading', { name: 'Mis publicaciones' })).toBeInTheDocument();
    expect(screen.queryByText(/^Ingresaste como/)).not.toBeInTheDocument();
  });

  it('un huésped que entra a /anfitrion vuelve a su panel', () => {
    iniciar('HUESPED');
    renderizarEn('/anfitrion');
    esperarInicioDe('HUESPED', 'Huésped');
  });

  it.each(['HUESPED', 'ANFITRION', 'ADMINISTRADOR'])('muestra Mi cuenta a un %s con sesión', (rol) => {
    iniciar(rol);
    renderizarEn('/mi-cuenta');
    expect(screen.getByRole('heading', { name: 'Mi cuenta' })).toBeInTheDocument();
  });

  it('sin sesión Mi cuenta lleva al inicio de sesión', () => {
    renderizarEn('/mi-cuenta');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it('sin sesión un panel lleva al inicio de sesión', () => {
    renderizarEn('/huesped');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it('muestra la revisión de publicaciones al administrador', () => {
    iniciar('ADMINISTRADOR');
    renderizarEn('/administrador/publicaciones');
    expect(screen.getByRole('heading', { name: 'Revisión de publicaciones' })).toBeInTheDocument();
  });

  it('muestra la auditoría al administrador y la pide a la API', async () => {
    get.mockResolvedValue({ contenido: [], pagina: 0, tamano: 50, totalElementos: 0, totalPaginas: 0 });
    iniciar('ADMINISTRADOR');
    renderizarEn('/administrador/auditoria');

    expect(screen.getByRole('heading', { name: 'Auditoría' })).toBeInTheDocument();
    expect(await screen.findByText(/^Sin resultados/)).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/v1/admin/auditoria?pagina=0&tamano=50');
  });

  it.each([['HUESPED', 'Huésped'], ['ANFITRION', 'Anfitrión']])(
    'un %s que entra a la auditoría vuelve a su panel', (rol, nombre) => {
      iniciar(rol);
      renderizarEn('/administrador/auditoria');
      esperarInicioDe(rol, nombre);
      expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/admin/'));
    },
  );

  it('sin sesión la auditoría lleva al inicio de sesión', () => {
    renderizarEn('/administrador/auditoria');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('muestra el detalle del evento de auditoría al administrador y lo pide a la API', async () => {
    get.mockResolvedValue({ id: 7, fecha: '2026-11-16T03:30:00Z', usuario: '2', accion: 'RESERVA', entidad: 'Reserva',
      entidadId: '55', resultado: 'EXITO', datos: {}, correlacion: 'c-1' });
    iniciar('ADMINISTRADOR');
    renderizarEn('/administrador/auditoria/7');

    expect(await screen.findByRole('heading', { name: 'Evento de auditoría 7' })).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/v1/admin/auditoria/7');
  });

  it.each([['HUESPED', 'Huésped'], ['ANFITRION', 'Anfitrión']])(
    'un %s que entra al detalle de auditoría vuelve a su panel', (rol, nombre) => {
      iniciar(rol);
      renderizarEn('/administrador/auditoria/7');
      esperarInicioDe(rol, nombre);
      expect(get).not.toHaveBeenCalledWith(expect.stringContaining('/admin/'));
    },
  );

  it('sin sesión el detalle de auditoría lleva al inicio de sesión', () => {
    renderizarEn('/administrador/auditoria/7');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('muestra el calendario de la publicación al huésped y lo pide a la API', async () => {
    get.mockResolvedValue({ publicacionId: 10, desde: '2026-12-01', hasta: '2026-12-02', tienePeriodos: true,
      dias: [{ fecha: '2026-12-01', estado: 'DISPONIBLE' }] });
    iniciar('HUESPED');
    renderizarEn('/huesped/publicaciones/10/calendario');

    expect(screen.getByRole('heading', { name: 'Calendario de disponibilidad' })).toBeInTheDocument();
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/v1/publicaciones/10/calendario');
  });

  it('sin sesión el calendario del huésped lleva al inicio de sesión', () => {
    renderizarEn('/huesped/publicaciones/10/calendario');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it('un anfitrión que entra al calendario del huésped vuelve a su panel', () => {
    iniciar('ANFITRION');
    renderizarEn('/huesped/publicaciones/10/calendario');
    esperarInicioDe('ANFITRION', 'Anfitrión');
  });

  it('un anfitrión que entra a la revisión de publicaciones vuelve a su panel', () => {
    iniciar('ANFITRION');
    renderizarEn('/administrador/publicaciones');
    esperarInicioDe('ANFITRION', 'Anfitrión');
  });

  it('un rol ajeno vuelve a su propio panel', () => {
    iniciar('HUESPED');
    renderizarEn('/administrador');
    esperarInicioDe('HUESPED', 'Huésped');
  });

  it.each(['/inicio-sesion', '/registro', '/recuperar-contrasena'])('con sesión %s lleva al panel del rol', (ruta) => {
    iniciar('ANFITRION');
    renderizarEn(ruta);
    esperarInicioDe('ANFITRION', 'Anfitrión');
  });

  it('sin sesión muestra el inicio de sesión normalmente', () => {
    renderizarEn('/inicio-sesion');
    expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
  });

  it.each([
    ['responde bien', () => Promise.resolve({})],
    ['falla con 503', () => Promise.reject(new ErrorApi(503, 'No disponible'))],
  ])('cerrar sesión desde un panel cuando el servidor %s limpia la sesión y vuelve al inicio de sesión', async (_caso, respuesta) => {
    post.mockImplementation(respuesta);
    iniciar('HUESPED');
    renderizarEn('/huesped');

    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout');
    expect(obtenerSesion()).toBeNull();
    expect(screen.queryByText(/^Ingresaste como/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cerrar sesión' })).not.toBeInTheDocument();
  });
});
