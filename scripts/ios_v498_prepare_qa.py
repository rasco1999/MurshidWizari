"""Add simulator-only QA targets; the Release app contains no fixtures."""
from pathlib import Path

project = Path("source/project.yml")
text = project.read_text()
assert "    scheme: {}" in text, "Expected original app scheme"
text = text.replace("    scheme: {}", """    scheme:
      testTargets:
        - name: MurshidFinalTests
          parallelizable: false
        - name: MurshidFinalUITests
          parallelizable: false
""", 1)
text += """
  MurshidFinalTests:
    type: bundle.unit-test
    platform: iOS
    sources:
      - ../qa-ios/V498FinalTests.swift
    dependencies:
      - target: MurshidWizari
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.muriq.murshid.finaltests
        GENERATE_INFOPLIST_FILE: YES
        TEST_HOST: $(BUILT_PRODUCTS_DIR)/MurshidWizari.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/MurshidWizari
        BUNDLE_LOADER: $(TEST_HOST)
  MurshidFinalUITests:
    type: bundle.ui-testing
    platform: iOS
    sources:
      - ../qa-ios/V498FinalUITests.swift
    dependencies:
      - target: MurshidWizari
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.muriq.murshid.finaluitests
        GENERATE_INFOPLIST_FILE: YES
        TEST_TARGET_NAME: MurshidWizari
"""
project.write_text(text)
