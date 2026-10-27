import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import Encabezado from './Encabezado.jsx';

describe('Encabezado', () => {
  it('muestra la marca con un enlace al inicio', () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Encabezado />
      </MemoryRouter>,
    );

    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inversiones LR, ir al inicio' })).toHaveAttribute('href', '/');
  });
});
