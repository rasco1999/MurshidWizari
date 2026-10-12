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
    func testArabicRecallListenAndStopControls() {
        let app = XCUIApplication()
        app.launch()
        let button = app.buttons["V498_QA_AUDIO"]
        XCTAssertTrue(button.waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["student-tab-0"].firstMatch.waitForExistence(timeout: 20))
        button.tap()
        let question = app.buttons["recall-question-98765001"]
        XCTAssertTrue(question.waitForExistence(timeout: 8))
        question.tap()
        let setup = app.staticTexts["recall-question-98765001-message"]
        if question.value as? String == "متوقف", setup.exists {
            XCTAssertTrue(setup.label.contains("لا يوجد صوت عربي أنثوي"))
            XCTAssertTrue(setup.label.contains("تسهيلات الاستخدام"))
            screenshot("03-female-voice-setup-guidance")
            app.buttons["اكشف الإجابة"].tap()
            let answer = app.buttons["recall-answer-98765001"]
            if !answer.isHittable { app.swipeUp() }
            answer.tap()
            XCTAssertEqual(answer.value as? String, "متوقف")
            XCTAssertTrue(app.staticTexts["recall-answer-98765001-message"].label.contains("لا يوجد صوت عربي أنثوي"))
            return
        }
        XCTAssertEqual(question.value as? String, "قيد التشغيل")
        question.tap()
        XCTAssertEqual(question.value as? String, "متوقف")
        app.buttons["اكشف الإجابة"].tap()
        let answer = app.buttons["recall-answer-98765001"]
        if !answer.isHittable { app.swipeUp() }
        XCTAssertTrue(answer.exists)
        answer.tap()
        XCTAssertEqual(answer.value as? String, "قيد التشغيل")
        screenshot("03-arabic-answer-playing")
        answer.tap()
        XCTAssertEqual(answer.value as? String, "متوقف")
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

    @MainActor private func screenshot(_ name: String) {
        let image = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        image.name = name
        image.lifetime = .keepAlways
        add(image)
    }
}

