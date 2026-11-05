# Offline importer fixtures

These PCM/Vorbis files were generated through the shared synthesis/export tools for `AudioAssetsTest`. `receipt.json` intentionally simulates human acceptance **only inside isolated build test directories**. Its zero project hashes are synthetic test data. This is not a listening, license or production approval and is never referenced by `art/asset-manifest.json`.

The fixture master and synthesis recipe come from the authored completion transient. The OGG uses the shared pinned encoder with bit-exact options; the receipt records exact hashes and measurements. Tests copy these repository-contained files and do not need a DAW, Python/FFmpeg, auxiliary directory or network.
