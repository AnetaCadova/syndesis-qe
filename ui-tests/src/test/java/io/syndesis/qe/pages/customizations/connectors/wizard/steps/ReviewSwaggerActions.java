package io.syndesis.qe.pages.customizations.connectors.wizard.steps;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;

import org.openqa.selenium.By;

import com.codeborne.selenide.SelenideElement;

import io.syndesis.qe.pages.SyndesisPageObject;
import io.syndesis.qe.logic.common.wizard.WizardPhase;

public class ReviewSwaggerActions extends SyndesisPageObject implements WizardPhase {

    private static class Button {
        public static By NEXT = By.xpath("//button[contains(.,'Next')]");
    }

    private static class Element {
        public static By ROOT = By.cssSelector("syndesis-api-connector-review");
    }

    @Override
    public void goToNextWizardPhase() {
        $(Button.NEXT).shouldBe(visible).click();
    }

    @Override
    public SelenideElement getRootElement() {
        return $(Element.ROOT).should(exist);
    }

    @Override
    public boolean validate() {
        return getRootElement().exists();
    }
}