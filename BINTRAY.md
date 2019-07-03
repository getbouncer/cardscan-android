* update local.properties with bintray user and api key

* For a new build, make sure you bump the libraryVersion

* We have two libraries in this project and we have to build and
  upload them separately.

MAKE SURE TO UPDATE THE LIBRARY VERSIONS BEFORE DOING THIS and to keep
things sane I suggest keeping the versions the same.

  $ ./gradlew base:install
  $ ./gradlew base:bintrayUpload
  $ ./gradlew cardscan:install
  $ ./gradlew cardscan:bintrayUpload


