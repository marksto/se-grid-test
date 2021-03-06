/**
 * Copyright 2014, Grid Dynamics International, Inc.
 * Licensed under the Apache License, Version 2.0.
 * Classification level: Public
 */
package com.griddynamics.cd.selenium

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

@ContextConfiguration("classpath:/com/griddynamics/cd/selenium/app-context.xml")
class SeNodesSmokeTest extends Specification {
    @Value('${TEST_PAGE_HOST}') String testPageUrlToAccess
    @Value('${EXPECTED_NUMBER_OF_NODES}') int numberOfNodes
    @Value('${BROWSERS_TO_CHECK}') String[] browsersToTest
    @Autowired Server server
    @Autowired WebDriverFactory driverFactory
    @Shared WebDriver[] webDrivers

    def setup() {
        if (!webDrivers) {
            webDrivers = createWds(numberOfNodes)
        }
    }

    def "one browser on every node should open a test page"() {
        given: "Given $numberOfNodes sessions are established with Grid"
            numberOfNodes == webDrivers.size()
        when: "Accessing test HTML page from the test: $testPageAddress"
            webDrivers.each { it.get(testPageAddress) }
        then: "Some basic elements like h1 are visible on the test HTML page"
            webDrivers.every { !it.findElements(By.cssSelector("h1")).empty }
    }

    def "one browser on every node should be able to use JavaScript"() {
        given: "Test Page is open: $testPageAddress"
            webDrivers.each { it.get(testPageAddress) }
        when: "Executing JavaScript to make hidden elements visible"
            webDrivers.each {
                ((JavascriptExecutor) it).executeScript('document.getElementById("to-show").style.display = "block"')
            }
        then: "The elements become visible"
            webDrivers.every { it.findElement(By.id("to-show")).displayed }
    }

    def "one browser on every node should be able to access external Internet resources"() {
        when: "Test Page is open: $testPageAddress"
            webDrivers.each { it.get(testPageAddress) }
        then: "JQuery which is loaded from external source should find an element"
            webDrivers.every { ((JavascriptExecutor) it).executeScript('return $("#to-show").attr("id");') == "to-show"}
    }

    def "one browser on every node should be able to execute native events"() {
        given: "Test Page is open: $testPageAddress"
            webDrivers.each { it.get(testPageAddress) }
        when: "Dragging & Dropping UI element on page"
            for (WebDriver driver : webDrivers) {
                WebElement draggable = driver.findElement(By.id("draggable"))
                WebElement droppable = driver.findElement(By.id("droppable"))
                new Actions(driver).dragAndDrop(draggable, droppable).perform();
            };
        then: "The element should get dropped"
            webDrivers.every {
                (new WebDriverWait(it, 10))//otherwise JS doesn't catch up with WebDriver and old value is seen
                        .until(ExpectedConditions.textToBePresentInElementLocated(By.id("dropped-text"), "Dropped!"))
            }
    }

    def cleanupSpec() {
        webDrivers.each {
            try {
                it.quit()
            } catch (ignored) {//do nothing but let other browsers to close
            }
        }
    }

    /**
     * Combines hostname and port in order to build a URL to access test page. While hostname is set in the
     * configuration or passed as a parameter, the port of the test page is dynamic and changes from run to run in
     * order to protect from port conflicts.
     * @return a built URL to access test page
     */
    private String getTestPageAddress() {
        return "http://${testPageUrlToAccess}:${((SelectChannelConnector) server.connectors[0]).localPort}"
    }

    /**
     * Creating multiple Web Driver sessions. Creates only once for all the test methods.
     * @param numberOfBrowsers number of WD sessions to open
     * @return all the created WD sessions
     */
    private List<WebDriver> createWds(int numberOfBrowsers) {
        List<WebDriver> webDrivers = []
        for(String browser: browsersToTest) {
            (1..numberOfBrowsers).each {
                println "Starting $browser #$it of $numberOfBrowsers"
                webDrivers.add(driverFactory.createDriver(new DesiredCapabilities(browserName: browser)))
            }
        }
        return webDrivers
    }
}
