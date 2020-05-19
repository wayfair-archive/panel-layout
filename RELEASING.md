Releasing
========

 1. Change the version in `gradle.properties` to a new version.
 1. Update the `CHANGELOG.md` for the impending release.
 1. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 1. `git tag -a X.Y.X -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 1. `git push && git push --tags`
 1. Wait for CI to publish and verify the version in `README.md` 
