import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

const manualChunkGroups = [
  ['react-vendor', ['react', 'react-dom', 'react-router']],
  ['ui-vendor', ['semantic-ui-react']]
] as const;

function manualChunks(id: string) {
  const normalizedId = id.replaceAll('\\', '/');

  if (!normalizedId.includes('/node_modules/')) {
    return undefined;
  }

  for (const [chunkName, packages] of manualChunkGroups) {
    if (packages.some((pkg) => normalizedId.includes(`/node_modules/${pkg}/`))) {
      return chunkName;
    }
  }

  return undefined;
}

export default defineConfig({
  plugins: [react()],
  base: '/',
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8001',
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    outDir: 'target/classes/META-INF/console2',
    emptyOutDir: true,
    // Semantic UI CSS contains selectors Lightning CSS rejects in Vite 8.
    cssMinify: 'esbuild',
    sourcemap: true,
    rollupOptions: {
      output: {
        manualChunks
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'react-router',
      'semantic-ui-react'
    ]
  }
});
