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
        <label className="block text-sm font-medium text-[var(--ee-text)]">
          {label}
        </label>
        {isValid && (
          <span
            className="text-xs px-2 py-0.5 rounded font-ee-mono tabular-nums font-semibold transition-colors shadow-sm"
            style={{ backgroundColor: value, color: contrastColor }}
          >
            Contraste {contrastColor === '#FFFFFF' ? 'Claro' : 'Oscuro'}
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <div className="relative w-11 h-11 rounded-[var(--ee-radius-sm)] overflow-hidden border border-[var(--ee-border)] shadow-sm flex-shrink-0 cursor-pointer">
          <input
            type="color"
            value={isValid ? value : '#10B981'}
            onChange={handleColorPickerChange}
            disabled={disabled}
            className="absolute -top-2 -left-2 w-16 h-16 cursor-pointer border-0 p-0"
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
          className={`flex-1 min-h-[44px] px-3 py-2 border rounded-[var(--ee-radius-sm)] font-ee-mono tabular-nums text-sm uppercase bg-[var(--ee-surface)] text-[var(--ee-text)] shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--ee-focus-ring)] ${
            !isValid && value.length > 0
              ? 'border-[var(--ee-danger-border)] focus-visible:ring-[var(--ee-danger-text)]'
              : 'border-[var(--ee-border)]'
          }`}
        />
      </div>

      {helperText && (
        <p className="text-xs text-[var(--ee-text-muted)]">{helperText}</p>
      )}
      {!isValid && value.length > 0 && (
        <p className="text-xs text-[var(--ee-danger-text)]">Debe cumplir con el formato hexadecimal #RRGGBB (ej: #10B981)</p>
      )}
    </div>
  );
};
