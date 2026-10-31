import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../../shared/services/http.js';
import DatosPerfil from './DatosPerfil.jsx';

const PERFIL = { nombre: 'Ana Gómez', correo: 'ana@correo.com', telefono: '', fotoUrl: '' };

function renderizar(alGuardar = vi.fn()) {
  render(<DatosPerfil perfil={PERFIL} alGuardar={alGuardar} />);
  return alGuardar;
}

function escribir(etiqueta, valor) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } });
}

function guardar() {
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
}

describe('DatosPerfil', () => {
  it('muestra el correo sin campo para editarlo y los datos actuales en el formulario', () => {
    renderizar();

    expect(screen.getByText('ana@correo.com')).toBeInTheDocument();
    expect(screen.getByText('El correo no se puede cambiar.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /correo/i })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveValue('Ana Gómez');
    expect(screen.getByLabelText('Teléfono')).toHaveValue('');
  });

  it('guarda nombre, teléfono y foto sin espacios sobrantes y avisa que quedaron guardados', async () => {
    const guardado = { ...PERFIL, nombre: 'Ana María', telefono: '+573001234567', fotoUrl: 'https://f.co/a.jpg' };
    const alGuardar = renderizar(vi.fn().mockResolvedValue(guardado));

    escribir('Nombre', '  Ana María ');
    escribir('Teléfono', ' +573001234567 ');
    escribir('Foto de perfil (dirección web)', 'https://f.co/a.jpg');
    guardar();

    expect(await screen.findByText('Tus datos quedaron guardados.')).toBeInTheDocument();
    expect(alGuardar).toHaveBeenCalledWith({
      nombre: 'Ana María', telefono: '+573001234567', fotoUrl: 'https://f.co/a.jpg',
    });
    expect(screen.getByLabelText('Nombre')).toHaveValue('Ana María');
  });

  it.each([
    ['Nombre', '   ', 'Escribe tu nombre.'],
    ['Teléfono', '12ab', 'El teléfono debe tener entre 7 y 15 dígitos y puede empezar con +.'],
    ['Foto de perfil (dirección web)', 'javascript:alert(1)',
      'La foto debe ser una dirección que empiece con http:// o https://.'],
  ])('con %s inválido muestra el error y no llama a la API', (etiqueta, valor, mensaje) => {
    const alGuardar = renderizar();

    escribir(etiqueta, valor);
    guardar();

    expect(screen.getByText(mensaje)).toBeInTheDocument();
    expect(alGuardar).not.toHaveBeenCalled();
  });

  it('si la API rechaza los datos muestra su mensaje y el error del campo', async () => {
    renderizar(vi.fn().mockRejectedValue(
      new ErrorApi(400, 'Hay datos con formato inválido.', { telefono: 'Teléfono rechazado por la API.' })));

    guardar();

    expect(await screen.findByRole('alert')).toHaveTextContent('Hay datos con formato inválido.');
    expect(screen.getByText('Teléfono rechazado por la API.')).toBeInTheDocument();
    expect(screen.queryByText('Tus datos quedaron guardados.')).not.toBeInTheDocument();
  });
});
