/**
 * A piece of the product floating over imagery -- the landing plate's
 * readouts. The one place a pill shape is allowed (DESIGN.md radius rules):
 * its edge is a top highlight and a soft offset shadow, so it reads as lifted
 * off the plate rather than boxed on it.
 */
export function Chip({
  children,
  className = "",
  style,
}: {
  children: React.ReactNode;
  className?: string;
  style?: React.CSSProperties;
}) {
  return (
    <div
      style={style}
      className={`rounded-full bg-surface shadow-[inset_0_1px_0_rgb(255_255_255/0.08),var(--shadow-float)] ${className}`}
    >
      {children}
    </div>
  );
}
