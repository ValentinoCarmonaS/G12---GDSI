import React from 'react';
import { isValidHexColor, getOptimalContrastColor } from '../../utils/colorContrastUtils';

interface ColorPickerInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  helperText?: string;
  name?: string;
  disabled?: boolean;
}

export const ColorPickerInput: React.FC<ColorPickerInputProps> = ({
  label,
  value,
  onChange,
  helperText,
  name,
  disabled = false,
}) => {
  const isValid = isValidHexColor(value);
  const contrastColor = isValid ? getOptimalContrastColor(value) : '#000000';

  const handleTextChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let inputVal = e.target.value.trim();
    if (!inputVal.startsWith('#') && inputVal.length > 0) {
      inputVal = '#' + inputVal;
    }
    onChange(inputVal.toUpperCase());
  };

  const handleColorPickerChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value.toUpperCase());
  };

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <label className="block text-sm font-medium text-stone-700 dark:text-stone-300">
          {label}
        </label>
        {isValid && (
          <span
            className="text-xs px-2 py-0.5 rounded font-mono font-semibold transition-colors shadow-sm"
            style={{ backgroundColor: value, color: contrastColor }}
          >
            Contraste {contrastColor === '#FFFFFF' ? 'Claro' : 'Oscuro'}
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <div className="relative w-10 h-10 rounded-lg overflow-hidden border border-stone-300 dark:border-stone-700 shadow-sm flex-shrink-0 cursor-pointer">
          <input
            type="color"
            value={isValid ? value : '#10B981'}
            onChange={handleColorPickerChange}
            disabled={disabled}
            className="absolute -top-2 -left-2 w-14 h-14 cursor-pointer border-0 p-0"
            aria-label={`Selector cromático para ${label}`}
          />
        </div>

        <input
          type="text"
          name={name}
          value={value}
          onChange={handleTextChange}
          placeholder="#RRGGBB"
          maxLength={7}
          disabled={disabled}
          className={`flex-1 px-3 py-2 border rounded-lg font-mono text-sm uppercase bg-white dark:bg-stone-900 text-stone-900 dark:text-stone-100 shadow-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 ${
            !isValid && value.length > 0
              ? 'border-red-500 focus:ring-red-500'
              : 'border-stone-300 dark:border-stone-700'
          }`}
        />
      </div>

      {helperText && (
        <p className="text-xs text-stone-500 dark:text-stone-400">{helperText}</p>
      )}
      {!isValid && value.length > 0 && (
        <p className="text-xs text-red-500">Debe cumplir con el formato hexadecimal #RRGGBB (ej: #10B981)</p>
      )}
    </div>
  );
};
