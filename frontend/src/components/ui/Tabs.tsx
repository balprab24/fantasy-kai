"use client";

import { useRef } from "react";

export interface TabItem<K extends string> {
  key: K;
  label: string;
  /** A count or short note after the label ("21 games"). */
  meta?: string;
}

/**
 * A tab list with the WAI-ARIA keyboard contract: one Tab stop, the arrow keys
 * move between tabs and select (automatic activation -- every panel here is
 * already loaded or cheap), Home and End jump to the ends. The chosen tab is
 * marked by an energy bar cut on the logo's slant, plus full ink and weight,
 * so colour is never the only cue.
 *
 * Panels are rendered by the caller with `<TabPanel tabsId={id} tab={key}>`;
 * the shared `id` (the caller's `useId()`) is what ties each panel's
 * `aria-labelledby` to its tab and each tab's `aria-controls` to its panel.
 */
export function Tabs<K extends string>({
  id: base,
  label,
  items,
  value,
  onChange,
  className = "",
}: {
  id: string;
  label: string;
  items: TabItem<K>[];
  value: K;
  onChange: (key: K) => void;
  className?: string;
}) {
  const refs = useRef(new Map<K, HTMLButtonElement>());

  const move = (to: number) => {
    const next = items[(to + items.length) % items.length];
    onChange(next.key);
    refs.current.get(next.key)?.focus();
  };

  return (
    <div
      role="tablist"
      aria-label={label}
      className={`flex max-w-full gap-0.5 overflow-x-auto [scrollbar-width:none] sm:gap-1 ${className}`}
    >
      {items.map((item, i) => {
        const selected = item.key === value;
        return (
          <button
            key={item.key}
            ref={(el) => {
              if (el) refs.current.set(item.key, el);
              else refs.current.delete(item.key);
            }}
            id={tabId(base, item.key)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelId(base, item.key)}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(item.key)}
            onKeyDown={(e) => {
              if (e.key === "ArrowRight") move(i + 1);
              else if (e.key === "ArrowLeft") move(i - 1);
              else if (e.key === "Home") move(0);
              else if (e.key === "End") move(items.length - 1);
              else return;
              e.preventDefault();
            }}
            className={`relative flex h-12 shrink-0 items-center gap-2 rounded-control px-2.5 text-[14px] whitespace-nowrap transition-colors focus-visible:outline-offset-[-2px] sm:px-4 sm:text-[15px] ${
              selected ? "font-semibold text-ink" : "text-mute hover:bg-well hover:text-ink"
            }`}
          >
            {item.label}
            {item.meta && <span className="text-[13px] font-normal text-faint max-sm:hidden">{item.meta}</span>}
            <span
              aria-hidden
              className={`slash absolute inset-x-2.5 bottom-0 h-[3px] bg-energy transition-opacity duration-200 ${selected ? "opacity-100" : "opacity-0"}`}
            />
          </button>
        );
      })}
    </div>
  );
}

const tabId = (base: string, key: string) => `${base}-tab-${key}`;
const panelId = (base: string, key: string) => `${base}-panel-${key}`;

/** A tab's panel, tied to its tab by the `id` both were given. */
export function TabPanel({
  tabsId,
  tab,
  children,
  className = "",
}: {
  tabsId: string;
  tab: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div role="tabpanel" id={panelId(tabsId, tab)} aria-labelledby={tabId(tabsId, tab)} tabIndex={0} className={className}>
      {children}
    </div>
  );
}
