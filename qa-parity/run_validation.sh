#!/usr/bin/env bash
set -eu
output="$1"
case "$2" in
  pages) python3 android-native/scripts/screen_parity_qa.py --output "$output/pages" ;;
  specialized)
    python3 android-native/scripts/specialized_exam_qa.py --output "$output/specialized"
    python3 android-native/scripts/matching_qa.py --output "$output/matching" ;;
  offline) python3 android-native/scripts/offline_exam_qa.py --output "$output/offline" ;;
esac
