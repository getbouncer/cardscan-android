# Release

CardScan UI release guide

## Contents

* [Versioning](#versioning)
* [Creating a new Release](#creating-a-new-release)
* [Installing to local maven](#installing-to-local-maven)
* [Installing to BinTray](#installing-to-bintray)

## Versioning

The release version of this library is determined by the value of the `version` field in the [gradle.properties](../gradle.properties) file.

## Creating a new release

1. Increment the version field in [gradle.properties](../gradle.properties).

1. Get your PR reviewed, approved, and merged.

1. Create a tag from master branch
    ```bash
    git tag <version_number> -a
    ```

1. Write a description for the tag

1. Push the tag to github
    ```bash
    git push origin --tags
    ```

## Updating the ChangeLog

1. Install the [github-changelog-generator](https://github.com/github-changelog-generator/github-changelog-generator). See the README for that project for installation instructions.

1. put your github personal access token in a file called `github_token` in the root directory

1. run:
    ```bash
    github_changelog_generator -u getbouncer -p cardscan-ui-android -t `cat github_token`
    ```

1. Create a new pull request with the updated changelog, get it approved, and merged.

## Installing to local maven

* execute `./gradlew cardscan-ui:install`

## Installing to BinTray

1. Update or create a file `local.properties` with your BinTray user and api key
    ```properties
    bintray.user=<your_bintray_user>
    bintray.apikey=<your_bintray_apikey>
    ```

1. We have two libraries in this project and we have to build and upload them separately.

1. build and upload the library
    ```bash
    ./gradlew cardscan-ui:build
    ./gradlew cardscan-ui:install
    ./gradlew cardscan-ui:bintrayUpload
    ```

1. Make sure you update docs in this readme and the apidocs repo.
