#!/bin/bash
set -x

[ -e 'external/BMW_Connected_v3.1.1.3078_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/BMW_Connected_v3.1.1.3078_apkpure.com.apk'
[ -e 'external/MINI_Connected_v3.1.1.3078_apkpure.com.apk' ] ||
wget --quiet -P external 'https://bimmergestalt.s3.amazonaws.com/aaidrive/external/MINI_Connected_v3.1.1.3078_apkpure.com.apk'
