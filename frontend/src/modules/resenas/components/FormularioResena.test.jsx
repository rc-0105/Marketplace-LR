import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import { crearResena } from '../services/resenaService.js';
import FormularioResena from './FormularioResena.jsx';

vi.mock('../services/resenaService.js', () => ({ crearResena: vi.fn() }));

function rellenar(calificacion = 4, comentario = 'Muy cómodo y limpio') {
  fireEvent.click(screen.getByRole('radio', { name: `${calificacion} de 5` }));
  fireEvent.change(screen.getByLabelText('Comentario'), { target: { value: comentario } });
}

describe('FormularioResena', () => {
  beforeEach(() => vi.resetAllMocks());

  it('ofrece calificar de 1 a 5 y comentar, y no deja publicar mientras falte algo', () => {
    render(<FormularioResena reservaId={7} />);

    expect(screen.getAllByRole('radio')).toHaveLength(5);
    const publicar = screen.getByRole('button', { name: 'Publicar reseña' });
    expect(publicar).toBeDisabled();

    fireEvent.click(screen.getByRole('radio', { name: '4 de 5' }));
    expect(publicar).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Comentario'), { target: { value: '   ' } });
    expect(publicar).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Comentario'), { target: { value: 'Muy cómodo' } });
    expect(publicar).toBeEnabled();
  });

  it('publica la reseña con la calificación elegida y el comentario sin espacios sobrantes', async () => {
    vi.mocked(crearResena).mockResolvedValue({ id: 9 });
    render(<FormularioResena reservaId={7} />);

    rellenar(5, '  Excelente  ');
    fireEvent.click(screen.getByRole('button', { name: 'Publicar reseña' }));

    expect(await screen.findByText('Gracias por tu reseña: ya quedó publicada.')).toBeInTheDocument();
    expect(crearResena).toHaveBeenCalledWith(7, { calificacion: 5, contenido: 'Excelente' });
    expect(screen.queryByRole('button', { name: 'Publicar reseña' })).not.toBeInTheDocument();
  });

  it('se deshabilita mientras publica para no enviar dos veces', async () => {
    let terminar;
    vi.mocked(crearResena).mockReturnValue(new Promise((resolver) => { terminar = resolver; }));
    render(<FormularioResena reservaId={7} />);

    rellenar();
    fireEvent.click(screen.getByRole('button', { name: 'Publicar reseña' }));

    expect(await screen.findByRole('button', { name: 'Publicando…' })).toBeDisabled();
    terminar({ id: 9 });
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Publicando…' })).not.toBeInTheDocument());
    expect(crearResena).toHaveBeenCalledTimes(1);
  });

  it('si la reserva ya tiene reseña muestra el mensaje de la API y retira el formulario', async () => {
    vi.mocked(crearResena).mockRejectedValue(new ErrorApi(409, 'Ya existe una reseña para esta reserva.'));
    render(<FormularioResena reservaId={7} />);

    rellenar();
    fireEvent.click(screen.getByRole('button', { name: 'Publicar reseña' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya existe una reseña para esta reserva.');
    expect(screen.queryByRole('button', { name: 'Publicar reseña' })).not.toBeInTheDocument();
  });

  it('con un 4xx distinto de 409 muestra el mensaje y deja corregir y reintentar', async () => {
    vi.mocked(crearResena).mockRejectedValue(new ErrorApi(400, 'La calificación va de 1 a 5'));
    render(<FormularioResena reservaId={7} />);

    rellenar();
    fireEvent.click(screen.getByRole('button', { name: 'Publicar reseña' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La calificación va de 1 a 5');
    expect(screen.getByRole('button', { name: 'Publicar reseña' })).toBeEnabled();
  });

  it('si el servidor falla muestra un aviso general y deja reintentar', async () => {
    vi.mocked(crearResena).mockRejectedValue(new ErrorApi(500, 'detalle interno'));
    render(<FormularioResena reservaId={7} />);

    rellenar();
    fireEvent.click(screen.getByRole('button', { name: 'Publicar reseña' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos publicar tu reseña. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Publicar reseña' })).toBeEnabled();
  });
});
