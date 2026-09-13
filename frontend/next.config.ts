import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Next writes its own AGENTS.md and CLAUDE.md into this directory on every
  // dev start. The repo already has one CLAUDE.md at the root and it is the
  // project's operational memory -- a generated second one nested here shadows
  // it for anything working in frontend/, which is the opposite of useful.
  agentRules: false,
};

export default nextConfig;
