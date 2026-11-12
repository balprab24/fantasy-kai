import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { describe, it } from "node:test";
import { runInNewContext } from "node:vm";
import { Children, isValidElement, type ReactElement, type ReactNode } from "react";
import ts from "typescript";
import { profileLabel } from "../src/lib/profiles.ts";
import { seasons } from "../src/lib/season.ts";

// node --test cannot load TSX. Compile the actual component with the existing TypeScript
// dependency and supply only its router/presentational boundaries; exercise its real handler.
const source = readFileSync(new URL("../src/components/rankings/FilterBar.tsx", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
}).outputText;
const require = createRequire(import.meta.url);
type SelectProps = {
  "aria-label"?: string;
  children?: ReactNode;
  value: number | string;
  onChange: (event: { currentTarget: { value: string } }) => void;
};

function scoringSelect(node: ReactNode): ReactElement<SelectProps> | undefined {
  for (const child of Children.toArray(node)) {
    if (!isValidElement<SelectProps>(child)) continue;
    if (child.type === "select" && child.props["aria-label"] === "Scoring") return child;
    const found = scoringSelect(child.props.children);
    if (found) return found;
  }
}

function setup() {
  const navigations: string[] = [];
  const selected: number[] = [];
  const boardParams = new URLSearchParams("profileId=2&season=2026&pos=WR");
  const exports: { FilterBar?: (props: Record<string, unknown>) => ReactNode } = {};
  runInNewContext(compiled, {
    exports,
    require: (name: string) => {
      if (name === "next/navigation") return { useRouter: () => ({ push: (href: string) => navigations.push(href) }) };
      if (name === "@/lib/profiles") return { profileLabel };
      if (name === "@/lib/season") return { seasons };
      if (name === "../ui/Icon") return { Icon: () => null };
      if (name === "react/jsx-runtime") return require(name);
      throw new Error(`Unexpected component dependency: ${name}`);
    },
  });
  assert.ok(exports.FilterBar);
  const select = scoringSelect(exports.FilterBar({
    profiles: [{ id: 2, name: "Half PPR", preset: true }, { id: 3, name: "Full PPR", preset: true }],
    profileId: 2,
    onProfile: (id: number) => { selected.push(id); boardParams.set("profileId", String(id)); },
    view: "overview", season: 2026, scope: "season", position: "WR", find: "", filtersOpen: false,
  }));
  assert.ok(select);
  const options = Children.toArray(select.props.children).filter(isValidElement) as ReactElement<{ value: string; children: ReactNode }>[];
  const last = options.at(-1)!;
  assert.equal(last.props.children, "+ New ruleset…");
  return { select, newValue: last.props.value, navigations, selected, boardParams };
}

describe("scoring select", () => {
  it("navigates to the existing create flow without selecting a profile or changing profileId", () => {
    const { select, newValue, navigations, selected, boardParams } = setup();
    const before = boardParams.toString();
    const currentTarget = { value: newValue };
    select.props.onChange({ currentTarget });
    assert.deepEqual(navigations, ["/profiles"]);
    assert.deepEqual(selected, []);
    assert.equal(boardParams.toString(), before);
    assert.equal(boardParams.get("profileId"), "2");
    assert.equal(currentTarget.value, "2");
    assert.equal(select.props.value, 2);
  });

  it("still selects an existing ruleset without navigating away", () => {
    const { select, navigations, selected, boardParams } = setup();
    select.props.onChange({ currentTarget: { value: "3" } });
    assert.deepEqual(selected, [3]);
    assert.deepEqual(navigations, []);
    assert.equal(boardParams.get("profileId"), "3");
  });
});
