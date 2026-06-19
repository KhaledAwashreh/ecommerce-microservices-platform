---
title: "Convert address create/edit to event-based HTMX pattern"
labels: ["enhancement"]
---

## Problem

The address create/edit form currently returns the grid fragment directly as the HTMX response (`hx-target="#address-grid" hx-swap="outerHTML"` on the form). This couples the form to the grid — the success response can only be the grid HTML.

## Desired

Decouple form submission from grid refresh using HTMX events:

1. Form submits via `hx-post`/`hx-put` with `hx-swap="none"` (no DOM swap from the response)
2. Server returns an empty body with `HX-Trigger: address-updated`
3. The grid listens for `address-updated from:body` via `hx-trigger` and auto-refreshes from `GET /addresses/grid`
4. Benefits: grid refresh is reusable from multiple sources (create, edit, delete, set-default), and the form response could instead show a toast notification or other feedback

## Files

- `frontend-service/.../controller/ProfileController.java` — HTMX POST handlers (marked with `// TODO`)
- `frontend-service/.../templates/user/address-grid.html` — grid fragment has `hx-trigger` ready for events
- `frontend-service/.../templates/user/address-modal.html` — form `hx-target`/`hx-swap` needs updating
