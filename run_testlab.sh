#!/bin/bash

# run `gcloud firebase test android models list` to get the latest models
# and to_device_arg.py to turn it into device arguments

./gradlew clean
./gradlew :app:assembleDebug
./gradlew :app:assembleAndroidTest

gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=hero2lte,version=23,locale=en,orientation=portrait \
  --device model=hwALE-H,version=21,locale=en,orientation=portrait \
  --device model=A0001,version=22,locale=en,orientation=portrait \
  --device model=FRT,version=27,locale=en,orientation=portrait \
  --device model=G8142,version=25,locale=en,orientation=portrait \
  --device model=G8441,version=26,locale=en,orientation=portrait \
  --device model=HWMHA,version=24,locale=en,orientation=portrait \
  --device model=OnePlus5,version=26,locale=en,orientation=portrait \
  --device model=a5y17lte,version=24,locale=en,orientation=portrait \
  --device model=athene,version=23,locale=en,orientation=portrait \
  --device model=athene_f,version=23,locale=en,orientation=portrait \
  --device model=blueline,version=28,locale=en,orientation=portrait \
  --device model=cheryl,version=25,locale=en,orientation=portrait \
  --device model=crownqlteue,version=27,locale=en,orientation=portrait \
  --device model=flo,version=19,locale=en,orientation=portrait \
  --device model=flo,version=21,locale=en,orientation=portrait \
  --device model=g3,version=19,locale=en,orientation=portrait \
  --device model=grandpplte,version=23,locale=en,orientation=portrait \
  --device model=griffin,version=24,locale=en,orientation=portrait \
  --device model=hammerhead,version=21,locale=en,orientation=portrait \
  --device model=hammerhead,version=23,locale=en,orientation=portrait \
  --device model=harpia,version=23,locale=en,orientation=portrait \
  --device model=hlte,version=19,locale=en,orientation=portrait \
  --device model=htc_m8,version=19,locale=en,orientation=portrait \
  --device model=j1acevelte,version=22,locale=en,orientation=portrait \
  --device model=j7xelte,version=23,locale=en,orientation=portrait \
  --device model=lt02wifi,version=19,locale=en,orientation=portrait \
  --device model=lucye,version=24,locale=en,orientation=portrait \
  --device model=lv0,version=23,locale=en,orientation=portrait \
  --device model=m0,version=18,locale=en,orientation=portrait \
  --device model=mata,version=25,locale=en,orientation=portrait \
  --device model=mlv1,version=23,locale=en,orientation=portrait \
  --device model=potter,version=24,locale=en,orientation=portrait \
  --device model=sailfish,version=25,locale=en,orientation=portrait \
  --device model=sailfish,version=26,locale=en,orientation=portrait \
  --device model=sailfish,version=27,locale=en,orientation=portrait \
  --device model=sailfish,version=28,locale=en,orientation=portrait \
  --device model=shamu,version=21,locale=en,orientation=portrait \
  --device model=shamu,version=22,locale=en,orientation=portrait \
  --device model=shamu,version=23,locale=en,orientation=portrait \
  --device model=star2qlteue,version=26,locale=en,orientation=portrait \
  --device model=starqlteue,version=26,locale=en,orientation=portrait \
  --device model=taimen,version=26,locale=en,orientation=portrait \
  --device model=taimen,version=27,locale=en,orientation=portrait \
  --device model=victara,version=19,locale=en,orientation=portrait \
  --device model=walleye,version=26,locale=en,orientation=portrait \
  --device model=walleye,version=27,locale=en,orientation=portrait \
  --device model=walleye,version=28,locale=en,orientation=portrait \
  --device model=zeroflte,version=23,locale=en,orientation=portrait
