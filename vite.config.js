import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      includeAssets: ['logo-180.png', 'logo-192.png', 'logo-512.png'],
      manifest: {
        name: 'Universal IR Hub',
        short_name: 'IR Hub',
        description: 'Global Cloud Zaza-style Universal Remote',
        theme_color: '#050608',
        background_color: '#050608',
        display: 'standalone',
        icons: [
          {
            src: 'logo-192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'logo-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      }
    })
  ]
});
