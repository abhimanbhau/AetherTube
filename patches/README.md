# Patched dependencies

`SharedModules` and `MediaServiceCore` used to be personal forks
(`abhimanbhau/SharedModules`, `abhimanbhau/MediaServiceCore`) kept in sync with upstream via a
scheduled merge workflow in each fork. That broke down in practice: the actual divergence from
upstream was always narrow (AGP 7→8 migration syntax, plus a dependency-version bump in
`SharedModules/constants.gradle` for the Compose toolchain) but touched shared files upstream
also keeps editing, so *every* sync hit the same conflicts, forever - not a one-time fix, a
structural, permanent disagreement in build tooling between the two projects.

This replaces that with the same pattern used for vendoring a patched third-party dependency at
work: pin an exact upstream commit, keep the actual divergence as small `.patch` files versioned
in *this* repo, and apply them fresh at build time via `scripts/fetch-deps.sh` (run automatically
before Gradle - see that script and the CI/release workflows for where it's invoked). Advantages
over the fork-and-merge approach:

- A patch that fails to apply is a loud, immediate, specific failure ("this exact file changed"),
  not a silent 3-way-merge heuristic that can resolve subtly wrong, or a conflict that sits
  unnoticed for days.
- The entire divergence is readable in one place (these two patch files) instead of requiring a
  trip through a separate fork's merge-commit history to find out what's actually different.
- Two fewer GitHub repos and two fewer scheduled workflows to maintain.

## Bumping a pinned commit

1. Update `SHARED_MODULES_SHA` / `MEDIA_SERVICE_CORE_SHA` in `pins.env` to the new upstream commit.
2. Run `./scripts/fetch-deps.sh` - if a patch fails to apply, upstream changed a line this patch
   touches. Resolve it the same way you'd resolve any patch-reject: open the `.rej` file (or the
   partially-applied source) next to the failure, manually reconcile, then regenerate the patch:
   ```
   cd SharedModules   # or MediaServiceCore
   git diff > ../patches/SharedModules/0001-agp8-and-toolchain-bump.patch
   ```
3. Re-run `./scripts/fetch-deps.sh` to confirm it applies cleanly against the new pin, then build
   normally to confirm nothing broke.

## Adding a new patch

Add a new numbered `.patch` file under `patches/<dep>/` - they're applied in filename order.
Keep unrelated changes in separate files (e.g. a future real bugfix patch shouldn't live in the
same file as the AGP8 migration) so bumping one doesn't force regenerating the other.
