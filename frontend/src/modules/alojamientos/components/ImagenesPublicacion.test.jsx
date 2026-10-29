import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { actualizarImagenes } from '../services/alojamientosService.js';
import ImagenesPublicacion from './ImagenesPublicacion.jsx';

vi.mock('../services/alojamientosService.js', () => ({ actualizarImagenes: vi.fn() }));

const UNO = 'https://imagenes.com/1.jpg';
const DOS = 'https://imagenes.com/2.jpg';
const TRES = 'https://imagenes.com/3.jpg';

function renderizar(imagenes, alGuardar = vi.fn()) {
  render(<ImagenesPublicacion publicacionId={5} imagenes={imagenes} alGuardar={alGuardar} />);
}

function urlsEnPantalla() {
  return within(screen.getByRole('list')).getAllByRole('img').map((imagen) => imagen.getAttribute('src'));
}

function agregar(url) {
  fireEvent.change(screen.getByLabelText('URL de la imagen'), { target: { value: url } });
  fireEvent.click(screen.getByRole('button', { name: 'Agregar imagen' }));
}

describe('ImagenesPublicacion', () => {
  beforeEach(() => vi.resetAllMocks());

  it('muestra las imágenes en orden y marca la primera como principal', () => {
    renderizar([UNO, DOS]);

    expect(urlsEnPantalla()).toEqual([UNO, DOS]);
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Principal');
    expect(screen.getAllByRole('listitem')[1]).not.toHaveTextContent('Principal');
  });

  it('al subir la segunda imagen pasa a ser la principal', () => {
    renderizar([UNO, DOS]);

    fireEvent.click(screen.getByRole('button', { name: 'Subir la imagen 2' }));

    expect(urlsEnPantalla()).toEqual([DOS, UNO]);
    expect(screen.getByRole('button', { name: 'Subir la imagen 1' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Bajar la imagen 2' })).toBeDisabled();
  });

  it('agrega una URL válida al final y quita una sin afectar a las demás', () => {
    renderizar([UNO, DOS]);

    agregar(` ${TRES} `);
    fireEvent.click(screen.getByRole('button', { name: 'Quitar la imagen 1' }));

    expect(urlsEnPantalla()).toEqual([DOS, TRES]);
  });

  it.each([
    ['', 'Escribe la URL de la imagen.'],
    ['ftp://imagenes.com/1.jpg', 'La imagen debe ser una URL http o https.'],
    ['no es una url', 'La imagen debe ser una URL http o https.'],
  ])('rechaza agregar %j', (url, mensaje) => {
    renderizar([]);

    agregar(url);

    expect(screen.getByText(mensaje)).toBeInTheDocument();
    expect(screen.getByText('Todavía no hay imágenes.')).toBeInTheDocument();
  });

  it('no deja pasar de 10 imágenes', () => {
    renderizar(Array.from({ length: 10 }, (_, indice) => `https://imagenes.com/${indice}.jpg`));

    agregar(TRES);

    expect(screen.getByText('Se admiten máximo 10 imágenes.')).toBeInTheDocument();
  });

  it('guarda la lista en el orden elegido y entrega la publicación actualizada', async () => {
    const actualizada = { id: 5, imagenes: [DOS, UNO] };
    vi.mocked(actualizarImagenes).mockResolvedValue(actualizada);
    const alGuardar = vi.fn();
    renderizar([UNO, DOS], alGuardar);

    fireEvent.click(screen.getByRole('button', { name: 'Bajar la imagen 1' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar imágenes' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos las imágenes.');
    expect(actualizarImagenes).toHaveBeenCalledWith(5, [DOS, UNO]);
    expect(alGuardar).toHaveBeenCalledWith(actualizada);
  });

  it('muestra el error de la API si no se pueden guardar', async () => {
    vi.mocked(actualizarImagenes).mockRejectedValue(new ErrorApi(403, 'No tienes permiso.'));
    renderizar([UNO]);

    fireEvent.click(screen.getByRole('button', { name: 'Guardar imágenes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No tienes permiso.');
  });
});
