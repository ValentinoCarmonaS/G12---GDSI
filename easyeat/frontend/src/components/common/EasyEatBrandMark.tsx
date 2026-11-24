import React from 'react';
import logoFullWebp from '../../assets/brand/easyeat-logo-full.webp';
import logoFullPng from '../../assets/brand/easyeat-logo-full.png';
import logoMiniWebp from '../../assets/brand/easyeat-logo-mini.webp';
import logoMiniPng from '../../assets/brand/easyeat-logo-mini.png';

export type EasyEatBrandVariant = 'mini' | 'full';
export type EasyEatBrandSize = 'nav' | 'auth' | 'hero' | 'badge';

export interface EasyEatBrandMarkProps {
  /** 'mini' = sello oscuro con tilde dorado; 'full' = emblema principal transparente. */
  readonly variant: EasyEatBrandVariant;
  /** Escala responsiva mobile-first (móvil -> tablet -> ordenador). */
  readonly size?: EasyEatBrandSize;
  /** Texto alternativo accesible. Se ignora cuando decorative es true. */
  readonly alt?: string;
  /** Marca la imagen como decorativa cuando ya hay texto de marca visible adyacente. */
  readonly decorative?: boolean;
  /** Prioriza carga inmediata en elementos above-the-fold. */
  readonly priority?: boolean;
  /** Clases adicionales para el elemento contenedor <picture>. */
  readonly className?: string;
}

/**
 * Escala mobile-first estrictamente 1:1 (aspect-square) para garantizar
 * tamaño y proporción consistentes en móvil, tablet (sm/md) y ordenador (lg).
 */
const SIZE_CLASSES: Record<EasyEatBrandSize, string> = {
  nav: 'h-8 w-8 sm:h-9 sm:w-9 lg:h-10 lg:w-10',
  badge: 'h-12 w-12 sm:h-14 sm:w-14 lg:h-16 lg:w-16',
  auth: 'h-24 w-24 sm:h-28 sm:w-28 md:h-32 md:w-32 lg:h-36 lg:w-36',
  hero: 'h-28 w-28 sm:h-32 sm:w-32 md:h-36 md:w-36 lg:h-40 lg:w-40',
};

const ASSET_MAP = {
  mini: {
    webp: logoMiniWebp,
    png: logoMiniPng,
    intrinsicSize: 128,
    imageClasses:
      'h-full w-full aspect-square object-cover rounded-ee-sm border border-ee-border-subtle',
  },
  full: {
    webp: logoFullWebp,
    png: logoFullPng,
    intrinsicSize: 512,
    imageClasses: 'h-full w-full aspect-square object-contain',
  },
} as const;

export const EasyEatBrandMark: React.FC<EasyEatBrandMarkProps> = ({
  variant,
  size = variant === 'mini' ? 'nav' : 'auth',
  alt = 'EasyEat',
  decorative = false,
  priority = true,
  className = '',
}) => {
  const asset = ASSET_MAP[variant];
  const sizeClass = SIZE_CLASSES[size];

  return (
    <picture
      data-testid={`easyeat-logo-${variant}`}
      aria-hidden={decorative ? 'true' : undefined}
      className={`inline-flex shrink-0 aspect-square items-center justify-center select-none ${sizeClass} ${className}`.trim()}
    >
      <source srcSet={asset.webp} type="image/webp" />
      <img
        src={asset.png}
        alt={decorative ? '' : alt}
        width={asset.intrinsicSize}
        height={asset.intrinsicSize}
        loading={priority ? 'eager' : 'lazy'}
        decoding="async"
        draggable={false}
        className={`block max-w-full ${asset.imageClasses}`}
      />
    </picture>
  );
};

export default EasyEatBrandMark;
