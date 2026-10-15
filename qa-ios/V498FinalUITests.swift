import XCTest

final class V498FinalUITests: XCTestCase {
    @MainActor
    func testForegroundAdAppearsAndClearsAfterOpeningAds() {
        let app = XCUIApplication()
        app.launch()
        let publish = app.buttons["V498_QA_PUBLISH_FIXTURE"]
        XCTAssertTrue(publish.waitForExistence(timeout: 20))
        let adsTab = app.buttons["student-tab-3"].firstMatch
        XCTAssertTrue(adsTab.waitForExistence(timeout: 20))
        publish.tap()
        let unread = NSPredicate(format: "value CONTAINS %@", "إعلانات جديدة")
        expectation(for: unread, evaluatedWith: adsTab)
        waitForExpectations(timeout: 25)
        screenshot("01-live-ad-red-badge")
        adsTab.tap()
        XCTAssertTrue(app.staticTexts["مدرسة الاختبار 98000"].waitForExistence(timeout: 8))
        XCTAssertEqual(adsTab.value as? String ?? "", "")
        screenshot("02-ad-read")
        XCTAssertEqual(app.buttons.matching(identifier: "student-tab-0").count, 1)
        XCTAssertTrue(app.buttons["student-tab-0"].isHittable)
        app.buttons["student-tab-0"].tap()
        XCTAssertEqual(app.buttons.matching(identifier: "student-tab-3").count, 1)
        XCTAssertEqual(app.buttons["student-tab-3"].firstMatch.value as? String ?? "", "")
    }

    @MainActor
    func testCompactBrandLogosAndAccessibleTouchTargets() {
        let app = XCUIApplication()
        app.launch()
        let control = app.buttons["V498_QA_SOCIAL"]
        XCTAssertTrue(control.waitForExistence(timeout: 20))
        control.tap()
        for name in ["instagram", "facebook", "telegram"] {
            let element = app.descendants(matching: .any)["social-\(name)"].firstMatch
            XCTAssertTrue(element.waitForExistence(timeout: 5))
            XCTAssertGreaterThanOrEqual(element.frame.width, 44)
            XCTAssertGreaterThanOrEqual(element.frame.height, 44)
            XCTAssertLessThanOrEqual(element.frame.width, 48)
        }
        screenshot("04-compact-social-icons")
    }

    @MainActor
    func testOfflineColdStartAfterVerifiedLoginWithoutRememberedCookie() {
        // All bootstrap responses are synthetic URLProtocol fixtures; the QA
        // user was verified but no server cookies are saved for restoration.
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["student-tab-0"].waitForExistence(timeout: 25))
        app.terminate()

        // Reopen the real native UI with the network monitor forced offline.
        app.launchArguments = ["V498_QA_FORCE_OFFLINE"]
        app.launch()
        XCTAssertTrue(app.staticTexts["وضع الدراسة دون إنترنت"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["مراجعاتك محفوظة على الآيفون"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["فتح المراجعة دون إنترنت"].exists)
        screenshot("06-cold-offline-student-without-remember")
    }

    @MainActor private func screenshot(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
    }
}

