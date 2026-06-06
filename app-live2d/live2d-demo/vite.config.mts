import { defineConfig, UserConfig, ConfigEnv } from 'vite';
import path from 'path';

export default defineConfig((env: ConfigEnv): UserConfig => {
  const common: UserConfig = {
    server: {
      port: 5000,
      host: true,
    },
    root: './',
    base: './',
    publicDir: './public',
    resolve: {
      extensions: ['.ts', '.js'],
      alias: {
        '@framework': path.resolve(__dirname, '../Framework/src'),
      }
    },
    build: {
      target: 'baseline-widely-available',
      assetsDir: 'assets',
      outDir: path.resolve(__dirname, '../static/live2d'),
      emptyOutDir: true,
      sourcemap: env.mode === 'development',
    },
  };
  return common;
});