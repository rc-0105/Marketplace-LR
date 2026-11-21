import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { consultarDestacados } from '../../modules/busqueda/services/inicioService.js';
import InicioHuespedPage from './InicioHuespedPage.jsx';

vi.mock('../../modules/busqueda/services/inicioService.js', () => ({ consultarDestacados: vi.fn() }));

const ALOJAMIENTOS = [
  { id: 1, titulo: 'Apto luminoso', ciudad: 'Bogotá', tipo: 'Apartamento', capacidad: 4, precioNoche: 200000,
    imagenPrincipal: 'https://imagenes.com/1.jpg' },
  { id: 2, titulo: 'Casa colonial', ciudad: 'Cartagena', tipo: 'Casa', capacidad: 6, precioNoche: 450000,
    imagenPrincipal: null },
  { id: 3, titulo: 'Habitación en Modelia', ciudad: 'bogotá ', tipo: 'Habitación', capacidad: 2, precioNoche: 140000,
    imagenPrincipal: null },
];

function UbicacionActual() {
  const { pathname, search } = useLocation();
  return <p>{`${pathname}${search}`}</p>;
}

function renderizar() {
  render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/" element={<InicioHuespedPage nombre="Jose" />} />
        <Route path="/huesped/*" element={<UbicacionActual />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('InicioHuespedPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(consultarDestacados).mockResolvedValue(ALOJAMIENTOS);
  });

  it('saluda por el nombre y ofrece el buscador', async () => {
    renderizar();

    expect(screen.getByRole('heading', { name: 'Hola, Jose' })).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Buscar alojamientos' })).toBeInTheDocument();
    await screen.findByRole('heading', { name: 'Bogotá' });
  });

  it('agrupa los alojamientos por ciudad, sin distinguir mayúsculas ni espacios', async () => {
    renderizar();

    expect(await screen.findByRole('heading', { name: 'Bogotá' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Cartagena' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(2);
    expect(screen.getByText('Habitación')).toBeInTheDocument();
  });

  it('cada tarjeta lleva al detalle del alojamiento y muestra su precio por noche', async () => {
    renderizar();

    const tarjeta = await screen.findByRole('link', { name: /Apto luminoso/ });

    expect(tarjeta).toHaveAttribute('href', '/huesped/busqueda/1');
    expect(screen.getByText(/200\.000.*por noche/)).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Foto de Apto luminoso' })).toBeInTheDocument();
  });

  it('muestra como máximo cuatro tarjetas por ciudad', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue(
      Array.from({ length: 6 }, (_, posicion) => ({
        id: posicion + 1, titulo: `Alojamiento ${posicion + 1}`, ciudad: 'Bogotá', tipo: 'Casa', capacidad: 2,
        precioNoche: 100000, imagenPrincipal: null,
      })),
    );
    renderizar();

    await screen.findByRole('heading', { name: 'Bogotá' });

    expect(screen.getAllByRole('link', { name: /Alojamiento/ })).toHaveLength(4);
  });

  it('"Ver todos" lleva a la búsqueda con la ciudad ya cargada', async () => {
    renderizar();

    fireEvent.click(await screen.findByRole('link', { name: 'Ver todos los alojamientos de Cartagena' }));

    expect(screen.getByText('/huesped/busqueda?ciudad=Cartagena')).toBeInTheDocument();
  });

  it('buscar lleva a la búsqueda con la ciudad, las fechas y los huéspedes', async () => {
    renderizar();
    await screen.findByRole('heading', { name: 'Bogotá' });

    fireEvent.change(screen.getByLabelText('Dónde'), { target: { value: 'Bogotá' } });
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2999-11-12' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2999-11-15' } });
    fireEvent.change(screen.getByLabelText('Quién'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByText(
      '/huesped/busqueda?ciudad=Bogot%C3%A1&llegada=2999-11-12&salida=2999-11-15&capacidad=2',
    )).toBeInTheDocument();
  });

  it('no navega si falta algún dato y lo indica', async () => {
    renderizar();
    await screen.findByRole('heading', { name: 'Bogotá' });

    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Indica a dónde quieres ir.');
    expect(screen.getByRole('heading', { name: 'Hola, Jose' })).toBeInTheDocument();
  });

  it('si aún no hay alojamientos publicados lo avisa', async () => {
    vi.mocked(consultarDestacados).mockResolvedValue([]);
    renderizar();

    expect(await screen.findByText('Todavía no hay alojamientos publicados.')).toBeInTheDocument();
  });

  it('si la vitrina falla muestra el error y el buscador sigue disponible', async () => {
    vi.mocked(consultarDestacados).mockRejectedValue(new Error('No pudimos cargar los alojamientos.'));
    renderizar();

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar los alojamientos.');
    expect(screen.getByRole('form', { name: 'Buscar alojamientos' })).toBeInTheDocument();
  });
});
