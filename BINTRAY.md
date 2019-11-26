* update local.properties with bintray user and api key

* For a new build, make sure you bump the libraryVersion by setting
  the `version` in the `gradle.properties` file

* Although it's not strictly required, it is a good practice to tag
  this repo with the new release version and push that tag to github

* We have two libraries in this project and we have to build and
  upload them separately.

To build and upload the two libraries:

  $ ./gradlew cardscan-base:install
  $ ./gradlew cardscan-base:bintrayUpload
  $ ./gradlew cardscan:install
  $ ./gradlew cardscan:bintrayUpload

* Then make sure that you update the docs both in the README in this
  repo as well as the apidocs repo