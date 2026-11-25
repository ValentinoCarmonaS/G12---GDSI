import colors from 'tailwindcss/colors';

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: colors.emerald,
        brand: {
          dark: '#0b0b0d',
          accent: '#c9a96e',
          text: '#f0ede8',
        },
        ee: {
          surface: 'var(--ee-surface)',
          'surface-elevated': 'var(--ee-surface-elevated)',
          elevated: 'var(--ee-surface-elevated)',
          'surface-subtle': 'var(--ee-surface-subtle)',
          text: 'var(--ee-text)',
          ink: 'var(--ee-text)',
          'text-secondary': 'var(--ee-text-secondary)',
          muted: 'var(--ee-text-secondary)',
          'border-subtle': 'var(--ee-border-subtle)',
          subtle: 'var(--ee-border-subtle)',
          'border-strong': 'var(--ee-border-strong)',
          strong: 'var(--ee-border-strong)',
          primary: 'var(--ee-primary)',
          'on-primary': 'var(--ee-on-primary)',
          'primary-hover': 'var(--ee-primary-hover)',
          'primary-border': 'var(--ee-primary-border)',
          secondary: 'var(--ee-secondary)',
          'on-secondary': 'var(--ee-on-secondary)',
          'secondary-hover': 'var(--ee-secondary-hover)',
          'secondary-border': 'var(--ee-secondary-border)',
          'danger-solid': 'var(--ee-danger-solid)',
          'on-danger': 'var(--ee-on-danger)',
          'danger-hover': 'var(--ee-danger-hover)',
          focus: 'var(--ee-focus-ring)',
          'focus-ring': 'var(--ee-focus-ring)',
          neutral: {
            bg: 'var(--ee-neutral-bg)',
            fg: 'var(--ee-neutral-fg)',
            border: 'var(--ee-neutral-border)',
          },
          info: {
            bg: 'var(--ee-info-bg)',
            fg: 'var(--ee-info-fg)',
            border: 'var(--ee-info-border)',
          },
          success: {
            bg: 'var(--ee-success-bg)',
            fg: 'var(--ee-success-fg)',
            border: 'var(--ee-success-border)',
          },
          warning: {
            bg: 'var(--ee-warning-bg)',
            fg: 'var(--ee-warning-fg)',
            border: 'var(--ee-warning-border)',
          },
          danger: {
            bg: 'var(--ee-danger-bg)',
            fg: 'var(--ee-danger-fg)',
            border: 'var(--ee-danger-border)',
          },
        },
      },
      fontFamily: {
        'ee-display': ['var(--ee-font-display)'],
        'ee-sans': ['var(--ee-font-sans)'],
        'ee-mono': ['var(--ee-font-mono)'],
      },
      borderRadius: {
        'ee-sm': 'var(--ee-radius-sm)',
        'ee-md': 'var(--ee-radius-md)',
        'ee-lg': 'var(--ee-radius-lg)',
      },
      boxShadow: {
        'ee-elevated': 'var(--ee-shadow-elevated)',
      },
    },
  },
  plugins: [],
}
