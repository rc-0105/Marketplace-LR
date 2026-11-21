import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { actualizarImagenes, subirImagen } from '../services/alojamientosService.js';
import ImagenesPublicacion from './ImagenesPublicacion.jsx';

vi.mock('../services/alojamientosService.js', () => ({ actualizarImagenes: vi.fn(), subirImagen: vi.fn() }));

function archivo(nombre = 'foto.png', tipo = 'image/png', tamano = 1024) {
  const contenido = new File(['x'], nombre, { type: tipo });
  Object.defineProperty(contenido, 'size', { value: tamano });
  return contenido;
}

function soltar(...archivos) {
  fireEvent.drop(screen.getByText('Arrastra tus fotos aquí').parentElement, { dataTransfer: { files: archivos } });
}

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
    expect(screen.getAllByRole('listitem')[1]).toHaveTextContent('Secundaria');
    expect(screen.getAllByRole('listitem')[1]).not.toHaveTextContent('Principal');
    expect(screen.getAllByRole('listitem')[0]).not.toHaveTextContent(UNO);
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

  it('avisa que hay cambios sin guardar hasta que se guardan', async () => {
    vi.mocked(actualizarImagenes).mockResolvedValue({ id: 5, imagenes: [DOS, UNO] });
    renderizar([UNO, DOS]);

    expect(screen.queryByText('Hay cambios en las imágenes sin guardar.')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Subir la imagen 2' }));
    expect(screen.getByText('Hay cambios en las imágenes sin guardar.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Guardar imágenes' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Guardamos las imágenes.');
    expect(screen.queryByText('Hay cambios en las imágenes sin guardar.')).not.toBeInTheDocument();
  });

  describe('subida de archivos', () => {
    it('al soltar un PNG lo sube, agrega su URL a la lista y avisa que falta guardar', async () => {
      vi.mocked(subirImagen).mockResolvedValue('/api/v1/imagenes/abc');
      renderizar([UNO]);

      const foto = archivo('foto.png');
      soltar(foto);

      expect(await screen.findByAltText('Imagen 2')).toHaveAttribute('src', '/api/v1/imagenes/abc');
      expect(subirImagen).toHaveBeenCalledWith(5, foto);
      expect(screen.getByText('Hay cambios en las imágenes sin guardar.')).toBeInTheDocument();
    });

    it('sube varios archivos de uno en uno y conserva el orden en que se soltaron', async () => {
      vi.mocked(subirImagen).mockResolvedValueOnce('/api/v1/imagenes/a').mockResolvedValueOnce('/api/v1/imagenes/b');
      renderizar([]);

      soltar(archivo('a.png'), archivo('b.jpg', 'image/jpeg'));

      await screen.findByAltText('Imagen 2');
      expect(urlsEnPantalla()).toEqual(['/api/v1/imagenes/a', '/api/v1/imagenes/b']);
    });

    it('elegir archivos con el botón sube igual que arrastrarlos', async () => {
      vi.mocked(subirImagen).mockResolvedValue('/api/v1/imagenes/abc');
      renderizar([]);

      fireEvent.change(screen.getByLabelText('Archivos de imagen'), { target: { files: [archivo('foto.jpg', 'image/jpeg')] } });

      expect(await screen.findByAltText('Imagen 1')).toHaveAttribute('src', '/api/v1/imagenes/abc');
    });

    it('rechaza un archivo que no es PNG ni JPG, o que pesa más de 5 MB, sin subirlo', async () => {
      renderizar([]);

      soltar(archivo('animacion.gif', 'image/gif'), archivo('enorme.png', 'image/png', 6 * 1024 * 1024));

      expect(await screen.findByText('«animacion.gif» no es PNG ni JPG.')).toBeInTheDocument();
      expect(screen.getByText('«enorme.png» pesa más de 5 MB.')).toBeInTheDocument();
      expect(subirImagen).not.toHaveBeenCalled();
    });

    it('no pasa del máximo de diez imágenes: lo que sobra se explica y no se sube', async () => {
      vi.mocked(subirImagen).mockResolvedValue('/api/v1/imagenes/nueva');
      const nueve = Array.from({ length: 9 }, (_, indice) => `https://imagenes.com/${indice}.jpg`);
      renderizar(nueve);

      soltar(archivo('uno.png'), archivo('dos.png'));

      expect(await screen.findByText('«dos.png» no cabe: se admiten máximo 10 imágenes.')).toBeInTheDocument();
      expect(subirImagen).toHaveBeenCalledTimes(1);
    });

    it('si la API rechaza un archivo, explica cuál y sigue con los demás', async () => {
      vi.mocked(subirImagen)
        .mockRejectedValueOnce(new ErrorApi(400, 'Solo se admiten imágenes PNG o JPG.'))
        .mockResolvedValueOnce('/api/v1/imagenes/buena');
      renderizar([]);

      soltar(archivo('falsa.png'), archivo('buena.png'));

      expect(await screen.findByText('«falsa.png»: Solo se admiten imágenes PNG o JPG.')).toBeInTheDocument();
      expect(await screen.findByAltText('Imagen 1')).toHaveAttribute('src', '/api/v1/imagenes/buena');
    });
  });
});
