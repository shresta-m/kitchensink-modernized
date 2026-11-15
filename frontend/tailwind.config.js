/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'system-ui', 'sans-serif'] },
      colors: {
        brand: { DEFAULT: '#0F766E', dark: '#0B4F4A', tint: '#E6F4F1', soft: '#F2FAF8', line: '#BFE3DC' },
        ink: { DEFAULT: '#1A1A1A', soft: '#3C4043', muted: '#5F6368' },
        line: { DEFAULT: '#E6E8EB', strong: '#D0D3D8', input: '#8A8F98' },
        surface: '#F6F8F9',
        danger: { DEFAULT: '#C62828', dark: '#9B1C1C', tint: '#FDECEC', line: '#F3C1C1' },
      },
    },
  },
  plugins: [],
}
