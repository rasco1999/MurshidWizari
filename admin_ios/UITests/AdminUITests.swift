import XCTest

final class AdminUITests:XCTestCase {
    private func launch(_ login:Bool=false) -> XCUIApplication {
        continueAfterFailure=false
        let app=XCUIApplication()
        app.launchArguments=[login ? "--admin-login-preview":"--admin-preview"]
        app.launch()
        return app
    }
    func testNavigationAndCodeFilters() {
        let app=launch()
        XCTAssertTrue(app.staticTexts["نبض المنصة"].waitForExistence(timeout:10))
        app.tabBars.buttons["الأكواد"].tap()
        XCTAssertTrue(app.staticTexts["أكواد الاشتراك"].waitForExistence(timeout:5))
        app.buttons["مستخدمة"].tap()
        XCTAssertTrue(app.staticTexts["اشتراك 3 أشهر"].waitForExistence(timeout:5))
        XCTAssertFalse(app.staticTexts["اشتراك شهر واحد"].exists)
        app.buttons["الكل"].tap()
        XCTAssertTrue(app.staticTexts.matching(identifier:"اشتراك شهر واحد").firstMatch.waitForExistence(timeout:5))
        app.tabBars.buttons["الإعلانات"].tap()
        XCTAssertTrue(app.staticTexts["مدرسة تجريبية"].waitForExistence(timeout:5))
        app.tabBars.buttons["الطلاب"].tap()
        XCTAssertTrue(app.staticTexts["طالب تجريبي أول"].waitForExistence(timeout:5))
        app.tabBars.buttons["الرئيسية"].tap()
        XCTAssertTrue(app.staticTexts["نبض المنصة"].waitForExistence(timeout:5))
    }
    func testToolSearchAndBackNavigation() {
        let app=launch()
        app.tabBars.buttons["المزيد"].tap()
        let search=app.textFields["ابحث عن أداة…"]
        XCTAssertTrue(search.waitForExistence(timeout:5))
        search.tap();search.typeText("المرشحات")
        XCTAssertTrue(app.staticTexts["إدارة المرشحات"].exists)
        XCTAssertFalse(app.staticTexts["بنك الأسئلة"].exists)
        app.buttons["مسح البحث"].tap()
        search.typeText("الأسئلة")
        app.staticTexts["بنك الأسئلة"].tap()
        XCTAssertTrue(app.buttons["إضافة سؤال جديد"].waitForExistence(timeout:5))
        XCTAssertTrue(app.navigationBars.buttons.element(boundBy:0).exists)
        app.navigationBars.buttons.element(boundBy:0).tap()
        XCTAssertTrue(app.staticTexts["مساحة الإدارة"].waitForExistence(timeout:5))
    }
    func testPasswordVisibility() {
        let app=launch(true)
        XCTAssertTrue(app.staticTexts["تسجيل الدخول"].waitForExistence(timeout:5))
        XCTAssertFalse(app.buttons["دخول الإدارة"].isEnabled)
        let password=app.secureTextFields["كلمة المرور"]
        password.tap();password.typeText("example123")
        app.buttons["إظهار كلمة المرور"].tap()
        XCTAssertEqual(app.textFields["كلمة المرور"].value as? String,"example123")
        app.buttons["إخفاء كلمة المرور"].tap()
        XCTAssertTrue(app.secureTextFields["كلمة المرور"].exists)
    }
}
