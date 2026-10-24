# Third-party notices

ThreeViewer's original code and procedural assets are licensed under Apache-2.0.
The upstream components retain their own licenses and copyright notices.

Offline copies are bundled in `app/src/main/assets/licenses/` and available at
**Settings → Open source licenses**. They include the full Apache-2.0 text,
notices embedded in resolved Maven artifacts, Material Symbols attribution,
and the vendored native-library notices from the pinned Filament source tag.

- `runtime-dependencies.txt`: resolved release Maven components, including transitive dependencies.
- `provenance.json`: component license metadata and upstream Filament notice sources.
- `filament.txt`: Filament and its third-party notices. Optional upstream backend notices
  are included conservatively even when that backend is not used by this app.

To refresh after dependency changes:

```sh
./gradlew :app:writeRuntimeDependencies
python tools/generate_notices.py
```

The generated files are committed. Normal builds and viewing the licenses do
not need a network connection. The app has no Internet permission.
