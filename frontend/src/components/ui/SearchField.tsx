import { Icon } from "./Icon";

export function SearchField({
  label,
  placeholder,
  value,
  onChange,
  className = "",
}: {
  /** What a screen reader hears. The placeholder is not a label. */
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  className?: string;
}) {
  return (
    <label className={`relative block ${className}`}>
      <span className="sr-only">{label}</span>
      <Icon
        name="search"
        size={18}
        className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-faint"
      />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete="off"
        spellCheck={false}
        className="h-[46px] w-full rounded-lg border border-line-strong bg-raised pr-3 pl-10 text-sm text-ink placeholder:text-faint hover:border-mute md:h-[38px] focus-visible:border-energy"
      />
    </label>
  );
}
