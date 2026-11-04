/**
 * The dish allergen/dietary taxonomy (spec 005's culinary labels).
 *
 * Shared by the Chef's `AllergenStep` and by the Administrator's rejection dialog, so a code the
 * Chef can label is a code the Administrator can propose changing. Keeping one list prevents the
 * two editors from drifting apart and silently disagreeing about what "CELIAC" means.
 */
export interface TaxonomyItem {
  code: string;
  name: string;
  description: string;
  isPositive: boolean;
  warningLabel: string;
}

export const DIETARY_LIFESTYLES: TaxonomyItem[] = [
  {
    code: 'VEGAN',
    name: 'Vegano',
    description: 'No contiene ingredientes de origen animal.',
    isPositive: true,
    warningLabel: 'Cumplimiento positivo (Apto)',
  },
  {
    code: 'VEGETARIAN',
    name: 'Vegetariano',
    description: 'No contiene carne ni pescado.',
    isPositive: true,
    warningLabel: 'Cumplimiento positivo (Apto)',
  },
  {
    code: 'CELIAC',
    name: 'Celíaco',
    description: 'Contiene gluten o trazas. No apto sin TACC.',
    isPositive: false,
    warningLabel: 'Advertencia preventiva (Contiene)',
  },
];

export const COMMON_ALLERGENS: TaxonomyItem[] = [
  {
    code: 'DAIRY',
    name: 'Lácteos',
    description: 'Contiene leche o derivados lácteos.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'PEANUTS',
    name: 'Maní',
    description: 'Contiene maní o trazas de maní.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'TREE_NUTS',
    name: 'Frutos Secos',
    description: 'Contiene nueces, almendras, castañas u otros frutos secos.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'EGGS',
    name: 'Huevos',
    description: 'Contiene huevos o derivados.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'FISH',
    name: 'Pescado',
    description: 'Contiene pescado o derivados marinos.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'SHELLFISH',
    name: 'Mariscos',
    description: 'Contiene mariscos, moluscos o crustáceos.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'SOY',
    name: 'Soja',
    description: 'Contiene soja o lecitina de soja.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
  {
    code: 'WHEAT',
    name: 'Trigo',
    description: 'Contiene trigo, avena, cebada o centeno.',
    isPositive: false,
    warningLabel: 'Advertencia de alérgeno',
  },
];

const ALL_ITEMS: TaxonomyItem[] = [...DIETARY_LIFESTYLES, ...COMMON_ALLERGENS];

/** Human label for a taxonomy code, falling back to the code itself for unknown values. */
export function taxonomyLabel(code: string): string {
  return ALL_ITEMS.find((item) => item.code === code)?.name ?? code;
}
