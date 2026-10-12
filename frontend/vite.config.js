import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/configuracion-pruebas.js',
    coverage: {
      provider: 'v8',
      // SonarCloud lee el informe lcov desde esta ruta.
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/main.jsx', 'src/configuracion-pruebas.js', 'src/**/*.test.{js,jsx}'],
    },
  },
});
