import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EasyEatBrandMark } from '@/components/common/EasyEatBrandMark';

describe('EasyEatBrandMark', () => {
  it('renders mini variant with mobile-first 1:1 classes and decorative accessibility attributes', () => {
    render(<EasyEatBrandMark variant="mini" size="nav" decorative />);

    const picture = screen.getByTestId('easyeat-logo-mini');
    expect(picture).toHaveAttribute('aria-hidden', 'true');
    expect(picture).toHaveClass('aspect-square', 'h-8', 'w-8', 'sm:h-9', 'sm:w-9', 'lg:h-10', 'lg:w-10');

    const img = picture.querySelector('img');
    expect(img).not.toBeNull();
    expect(img).toHaveAttribute('alt', '');
    expect(img).toHaveAttribute('width', '128');
    expect(img).toHaveAttribute('height', '128');
    expect(img).toHaveClass('aspect-square', 'object-cover', 'rounded-ee-sm');
  });

  it('renders full variant with explicit alt text and progressive mobile -> tablet -> desktop scale', () => {
    render(<EasyEatBrandMark variant="full" size="hero" alt="Logo EasyEat" />);

    const picture = screen.getByTestId('easyeat-logo-full');
    expect(picture).not.toHaveAttribute('aria-hidden');
    expect(picture).toHaveClass(
      'aspect-square',
      'h-28',
      'w-28',
      'sm:h-32',
      'sm:w-32',
      'md:h-36',
      'md:w-36',
      'lg:h-40',
      'lg:w-40'
    );

    const img = screen.getByRole('img', { name: 'Logo EasyEat' });
    expect(img).toHaveAttribute('width', '512');
    expect(img).toHaveAttribute('height', '512');
    expect(img).toHaveClass('aspect-square', 'object-contain');
    expect(picture.querySelector('source[type="image/webp"]')).not.toBeNull();
  });
});
