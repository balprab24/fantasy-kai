"use client";

/**
 * A number input that does not change its value when the page scrolls past it.
 *
 * A focused `<input type="number">` treats the mouse wheel as increment and
 * decrement. In a form this tall that is a silent-corruption machine: touch a
 * rate, scroll down to reach the save button, and the rate you touched has
 * quietly moved -- no error, no highlight, and the ruleset saves wrong. Found
 * by scrolling this form, not by reasoning about it.
 *
 * Blurring on wheel is the fix rather than `preventDefault`, because the user
 * is trying to scroll the page and should be allowed to.
 */
export function RateInput({
  value,
  onChange,
  className = "",
  ...rest
}: {
  value: number;
  onChange: (value: string) => void;
  className?: string;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "type">) {
  return (
    <input
      {...rest}
      type="number"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onWheel={(e) => e.currentTarget.blur()}
      className={`tabular h-9 rounded-control border border-line-strong bg-well px-2 text-right text-ink transition-colors hover:border-mute focus-visible:border-energy ${className}`}
    />
  );
}
