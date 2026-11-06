# Prepare the app for built-in station providers

## Context
Radio Browser is the app's only station source, and adding others would mean editing its code paths. Before adding any provider, move Radio Browser behind a common `StationProvider` interface, in the style of TorrentSearch: one Kotlin class per provider, registered through DI.

This is a refactor. Radio Browser stays the only provider, and users should see no change. Adding a provider later means one new class plus one DI line.

The coupling is small: Radio Browser is used directly only in `StationRepositoryImpl` and in `LibraryRepositoryImpl` (two `addStation` upload calls). Everything else goes through `StationRepository`.

## Design decisions
- **Keep `StationRepository` as the app's entry point.** ViewModels, Android Auto and the player keep calling it, so about 10 callers and their test fakes don't change.
- **No database migration.** Station IDs stay in the existing `stationUuid` column. Radio Browser IDs stay bare, and future providers use `"<providerId>:<id>"`, such as `lautfm:jazz`. Existing libraries, recents, schedules and backups keep working.
- **Custom stations** have random UUIDs and map to Radio Browser, so `getStationsByUuid` finds nothing for them, exactly as it does today.

## Changes

### New: `domain/provider/`
- `StationProvider.kt`
  - Properties: `id`, `name`, `capabilities: Set<ProviderCapability>`.
  - Required: `search(query: StationQuery): Result<List<RadioStation>>`.
  - Optional, with defaults that return empty results or do nothing: `getCountries`, `getLanguages`, `getTags`, `getStationsByIds`, `getStationsByUrl`, `reportPlay`, `submitStation`.
- `ProviderCapability.kt`: `COUNTRY_FILTER`, `LANGUAGE_FILTER`, `TAG_FILTER`, `SORTING`, `PLAY_TRACKING`, `STATION_SUBMISSION`. A later source switcher uses these to hide unsupported filters. Nothing reads them yet except the submission check.
- `StationQuery.kt`: data class holding the `filterStations` parameters with the same defaults.
- `StationIds.kt`: `toStationId(providerId, localId)`, `providerIdOf(stationId)`, `localIdOf(stationId)`. A value with no `:` belongs to Radio Browser.

### New: `data/provider/`
- `radiobrowser/RadioBrowserProvider.kt`: the `RadioBrowserApi` calls moved from `StationRepositoryImpl` unchanged, including sort parameters and error handling.
- `StationProviderRegistry.kt`: `@Singleton` that takes the injected `Set<StationProvider>` and exposes `active` (always Radio Browser for now), `byId(id)`, and `forStation(stationId)`.

### Edit
- `di/RepositoryModule.kt`: bind `RadioBrowserProvider` with `@IntoSet`. This is the app's first multibinding.
- `data/repository/StationRepositoryImpl.kt`: replace `RadioBrowserApi` with the registry.
  - `filterStations` builds a `StationQuery` and calls `active.search`.
  - `getCountries`, `getLanguages`, `getTags` and `getStationsByUrl` go to `active`.
  - `registerClick` goes to `forStation`, passing the local ID.
  - `getStationsByUuid` groups IDs by provider and combines the results.
  - These stay here because they aren't provider-specific: `getCurrentCountryCode` (reads the device) and merging custom-station tags in `getTags`.
- `data/repository/LibraryRepositoryImpl.kt`: both upload functions call `submitStation` on the provider with `STATION_SUBMISSION`, instead of `RadioBrowserApi`. Local save logic stays.
- `domain/repository/StationRepository.kt`: remove `addStation`, which nothing calls, along with its implementation and any fake overrides.

## Not included
- Any new provider, or any Browse or filter UI change. The source switcher comes when a second provider exists.
- Resolving stream URLs at play time. The providers tested so far supply stream URLs directly, so add this only if one needs it.
- `RadioBrowserApi.kt` and the Retrofit setup, which stay as they are.

## Verification
- New `StationIdsTest`: bare IDs, prefixed IDs, and existing UUIDs.
- New `StationRepositoryImplTest` with fake providers: query mapping, `getStationsByUuid` grouping across providers, click routing, and custom tag merging.
- Run `./gradlew compileFossDebugKotlin compilePlayDebugKotlin testFossDebugUnitTest testPlayDebugUnitTest`. `LibraryRemovalCoordinatorTest.kt` still blocks test compilation, so set it aside for the run unless it's fixed first.
- Manual on-device test, done by the user (not driven on an emulator):
  1. Browse, search, sort, and filter by country, language and tag: same results as before.
  2. Play a station: it plays and appears in Recent.
  3. Existing library, schedules and backup import still work.
  4. Upload a custom station to Radio Browser: it succeeds.
  5. Android Auto browsing still lists stations.
