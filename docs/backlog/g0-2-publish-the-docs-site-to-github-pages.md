# G0.2 — Publish the docs site to GitHub Pages

**Stage:** Stage 0 — Project foundation

## Motivation

The `docs/` Jekyll site should be live at the project Pages URL.

## Scope

- Add `.github/workflows/pages.yml` building `docs/` with `actions/jekyll-build-pages` and deploying via `actions/deploy-pages`.
- Enable Pages with source = GitHub Actions.
- Confirm the `just-the-docs` remote theme and Mermaid rendering work.

## Implementation notes

- `docs/_config.yml` already sets `remote_theme` and `mermaid`.
- `baseurl` is `/grip`; check relative links resolve.

## Acceptance criteria

- The site builds and deploys on a push touching `docs/`.
- Home, Architecture, Protocol, Security, Development, Roadmap are all reachable from the nav and Mermaid diagrams render.

## Tests required

- n/a (workflow change); verified by loading the published site.

## Dependencies

_None._
