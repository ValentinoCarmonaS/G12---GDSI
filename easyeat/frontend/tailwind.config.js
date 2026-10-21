/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          dark: '#0b0b0d',
          accent: '#c9a96e',
          text: '#f0ede8',
        },
      },
    },
  },
  plugins: [],
}
