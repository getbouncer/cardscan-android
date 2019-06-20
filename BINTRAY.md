* update local.properties with bintray user and api key

* For a new build, make sure you bump the libraryVersion

* We have two libraries in this project and we have to build and
  upload them separately.

  $ ./gradlew base:install
  $ ./gradlew base:bintrayUpload
  $ ./gradlew cardscan:install
  $ ./gradlew cardscan:bintrayUpload


