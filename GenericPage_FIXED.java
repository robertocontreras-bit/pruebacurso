package com.pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.RandomStringGenerator;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.database.ConfigurationJson;
import com.database.EntSkuJson;
import com.elements.BSElements;
import com.elements.GenElements;
import com.github.javafaker.Faker;
import com.utils.ControlDeErrores;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.ios.IOSDriver;

public class GenericPage extends ActionsPage {

    private GenElements genElements;
    private BSElements bsElements;

    protected final VariablesGlobales varGlb;
    protected final Faker faker;
    protected final ConfigurationJson json;

    public GenericPage() {
        super();

        this.varGlb = new VariablesGlobales();
        this.faker = new Faker();
        this.json = new ConfigurationJson();
    }

    public GenElements getGenElements() {

        if (genElements == null) {
            genElements = new GenElements(this.driver);
        }

        return genElements;
    }

    private BSElements getBsElements() {

        if (bsElements == null) {
            bsElements = new BSElements(this.driver);
        }

        return bsElements;
    }

    public GenericPage searchArticles(String tipo) throws Exception {

        EntSkuJson objRes = json.getSkuJson(tipo);

        String sku = objRes.getSku();
        String skuName = objRes.getNombre();

        openSearchBox();

        wait(2, true);

        if (params.getTienda().equalsIgnoreCase("liverpool")) {
            dismissTooltipIfPresent(getGenElements().lblToolTipMainSearch);
        } else {
            dismissTooltipIfPresent(getGenElements().lblSbToolTipMainSearch);
        }

        sendKeys(getGenElements().SearchBar_txtSearchBoxEdit, sku);

        makeSearch();

        varGlb.setskuSearch(tipo);
        varGlb.setskuNameSearch(skuName);

        wait(2, true);

        if (params.getTienda().equalsIgnoreCase("liverpool")) {
            dismissTooltipIfPresent(getGenElements().lblToolTipPDPMain);
        }

        wait(5, true);

        return this;
    }

    public void searchProductWithWord(String product) {

        if (product == null || product.trim().isEmpty()) {
            notifyWhenFail("❌ La palabra para realizar la búsqueda está vacía.");
            return;
        }

        IOSDriver iosDriver = (IOSDriver) this.driver;

        try {
            closeInitialSuburbiaPopups();

            WebElement searchField = openSearchBox();

            if (searchField == null) {
                notifyWhenFail("❌ No fue posible obtener el campo de búsqueda.");
                return;
            }

            safeClick(iosDriver, searchField);

            try {
                searchField.clear();
            } catch (Exception ignored) {
                System.out.println("⚠️ El campo de búsqueda no permitió clear().");
            }

            try {
                searchField.sendKeys(Keys.chord(Keys.COMMAND, "a"));
                searchField.sendKeys(Keys.DELETE);
            } catch (Exception ignored) {
                System.out.println("⚠️ No fue posible limpiar con Command + A.");
            }

            searchField.sendKeys(product);
            System.out.println("✅ Texto escrito en el buscador: " + product);

            String pageSourceBeforeSubmit = safePageSource(iosDriver);

            if (!submitSearch(iosDriver, searchField)) {
                notifyWhenFail("❌ Se escribió el producto, pero no fue posible ejecutar la búsqueda.");
                return;
            }

            waitForSearchResults(iosDriver, searchField, pageSourceBeforeSubmit);
            System.out.println("✅ FIX RCZ SEARCH V3: resultados o pantalla PLP detectados para: "
                    + product);

        } catch (Exception exception) {
            printVisibleInteractiveElements(iosDriver);
            notifyWhenFail("❌ Error al buscar '" + product + "': " + exception.getMessage());
        }
    }

    /**
     * Espera la transición desde el campo de búsqueda hacia resultados/PLP.
     *
     * La app Liverpool actual no siempre expone CollectionView o Cell. Por eso
     * se acepta también la combinación real observada en BrowserStack:
     * botón Back + bolsa + desaparición/invalidez del campo activo, o un cambio
     * significativo en el árbol de accesibilidad.
     */
    private void waitForSearchResults(IOSDriver iosDriver, WebElement originalSearchField,
            String pageSourceBeforeSubmit) {

        System.out.println("🔧 FIX RCZ SEARCH V3: esperando transición a resultados...");

        WebDriverWait wait = new WebDriverWait(iosDriver, Duration.ofSeconds(45));
        wait.pollingEvery(Duration.ofMillis(500));

        wait.until(currentDriver -> {
            try {
                boolean collectionDisplayed = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "type == 'XCUIElementTypeCollectionView' AND visible == 1"));

                boolean cellsDisplayed = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "type == 'XCUIElementTypeCell' AND visible == 1"));

                boolean resultTextDisplayed = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "visible == 1 AND ("
                                        + "name CONTAINS[c] 'resultado' OR "
                                        + "label CONTAINS[c] 'resultado' OR "
                                        + "name CONTAINS[c] 'producto' OR "
                                        + "label CONTAINS[c] 'producto')"));

                boolean productActionDisplayed = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "visible == 1 AND ("
                                        + "name CONTAINS[c] 'Agregar a bolsa' OR "
                                        + "label CONTAINS[c] 'Agregar a bolsa' OR "
                                        + "name CONTAINS[c] 'Agregar a mi bolsa' OR "
                                        + "label CONTAINS[c] 'Agregar a mi bolsa' OR "
                                        + "name CONTAINS[c] 'Comprar ahora' OR "
                                        + "label CONTAINS[c] 'Comprar ahora')"));

                boolean noResultsDisplayed = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "visible == 1 AND ("
                                        + "name CONTAINS[c] 'sin resultados' OR "
                                        + "label CONTAINS[c] 'sin resultados' OR "
                                        + "name CONTAINS[c] 'no encontramos' OR "
                                        + "label CONTAINS[c] 'no encontramos')"));

                boolean backVisible = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "type == 'XCUIElementTypeButton' AND visible == 1 "
                                        + "AND (name == 'Back' OR label == 'Back' "
                                        + "OR name CONTAINS[c] 'atrás' "
                                        + "OR label CONTAINS[c] 'atrás')"));

                boolean bagVisible = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "type == 'XCUIElementTypeButton' AND visible == 1 "
                                        + "AND (name == 'buttonBagHome' "
                                        + "OR name CONTAINS[c] 'bag' "
                                        + "OR label CONTAINS[c] 'shopping bag' "
                                        + "OR label CONTAINS[c] 'bolsa')"));

                boolean wishlistVisible = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "type == 'XCUIElementTypeButton' AND visible == 1 "
                                        + "AND (name CONTAINS[c] 'wishlist' "
                                        + "OR label CONTAINS[c] 'wishlist' "
                                        + "OR name CONTAINS[c] 'love' "
                                        + "OR label CONTAINS[c] 'love')"));

                boolean originalFieldGone = isElementGone(originalSearchField);

                boolean activeSearchStillVisible = hasVisibleElements(currentDriver,
                        AppiumBy.iOSNsPredicateString(
                                "visible == 1 AND enabled == 1 AND ("
                                        + "type == 'XCUIElementTypeSearchField' OR "
                                        + "type == 'XCUIElementTypeTextField') AND ("
                                        + "name CONTAINS[c] 'search' OR "
                                        + "name CONTAINS[c] 'buscar' OR "
                                        + "value CONTAINS[c] 'buscar')"));

                String currentPageSource = safePageSource((IOSDriver) currentDriver);
                boolean pageChanged = pageSourceChangedSignificantly(
                        pageSourceBeforeSubmit, currentPageSource);

                boolean navigationShellDisplayed = backVisible && bagVisible
                        && (originalFieldGone || !activeSearchStillVisible
                                || wishlistVisible || pageChanged);

                boolean resultDetected = collectionDisplayed || cellsDisplayed
                        || resultTextDisplayed || productActionDisplayed
                        || noResultsDisplayed || navigationShellDisplayed;

                if (resultDetected) {
                    System.out.println("✅ FIX RCZ SEARCH V3: transición detectada. "
                            + "collection=" + collectionDisplayed
                            + ", cells=" + cellsDisplayed
                            + ", text=" + resultTextDisplayed
                            + ", productAction=" + productActionDisplayed
                            + ", noResults=" + noResultsDisplayed
                            + ", back=" + backVisible
                            + ", bag=" + bagVisible
                            + ", wishlist=" + wishlistVisible
                            + ", fieldGone=" + originalFieldGone
                            + ", pageChanged=" + pageChanged);
                }

                return resultDetected;

            } catch (Exception transientException) {
                System.out.println("⚠️ El árbol iOS cambió durante la espera de resultados: "
                        + transientException.getMessage());
                return false;
            }
        });
    }

    private boolean hasVisibleElements(org.openqa.selenium.WebDriver currentDriver, By locator) {
        try {
            List<WebElement> elements = currentDriver.findElements(locator);

            for (WebElement element : elements) {
                try {
                    if (element.isDisplayed()) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // El árbol cambió mientras se inspeccionaba el elemento.
                }
            }
        } catch (Exception ignored) {
            // El locator todavía no está disponible.
        }

        return false;
    }

    private boolean isElementGone(WebElement element) {
        if (element == null) {
            return true;
        }

        try {
            return !element.isDisplayed();
        } catch (Exception ignored) {
            return true;
        }
    }

    private String safePageSource(IOSDriver iosDriver) {
        try {
            String source = iosDriver.getPageSource();
            return source == null ? "" : source;
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean pageSourceChangedSignificantly(String before, String after) {
        if (before == null || after == null || before.isBlank() || after.isBlank()) {
            return false;
        }

        if (before.equals(after)) {
            return false;
        }

        int lengthDifference = Math.abs(before.length() - after.length());
        int minimumDifference = Math.max(250, before.length() / 20);

        boolean searchFieldRemoved = before.contains("textFieldSearch")
                && !after.contains("textFieldSearch");
        boolean backAppeared = !before.contains("Back")
                && after.contains("Back");
        boolean bagAppeared = !before.contains("buttonBagHome")
                && after.contains("buttonBagHome");

        return lengthDifference >= minimumDifference
                || searchFieldRemoved || backAppeared || bagAppeared;
    }

    /**
     * Busca y abre el campo de búsqueda para Liverpool o Suburbia.
     *
     * Se usa un nombre diferente a clickOnSearchBox() para evitar un conflicto con NavegacionPage,
     * que ya contiene un método con ese nombre y retorno void.
     *
     * @return campo de búsqueda visible o null cuando no se encuentra.
     */
    public WebElement openSearchBox() {

        IOSDriver iosDriver = (IOSDriver) this.driver;
        String tienda = getCurrentStore();

        System.out.println("🔎 Buscando control de búsqueda para: " + tienda);

        if (isSuburbia()) {
            closeInitialSuburbiaPopups();
            waitForSuburbiaHome(Duration.ofSeconds(20));
        }

        WebElement alreadyOpenField = findFirstVisibleElement(
                iosDriver,
                getActiveSearchFieldLocators(),
                Duration.ofSeconds(2));

        if (alreadyOpenField != null) {
            String type = safeAttribute(alreadyOpenField, "type");

            if ("XCUIElementTypeSearchField".equals(type)
                    || "XCUIElementTypeTextField".equals(type)) {
                System.out.println("✅ El campo de búsqueda ya estaba abierto.");
                return alreadyOpenField;
            }
        }

        WebElement searchTrigger = findFirstVisibleElement(
                iosDriver,
                getSearchTriggerLocators(),
                Duration.ofSeconds(6));

        if (searchTrigger == null) {
            searchTrigger = findTopNavigationSearchButton(iosDriver);
        }

        if (searchTrigger == null) {
            printVisibleInteractiveElements(iosDriver);
            notifyWhenFail("❌ No se encontró el botón del buscador para " + tienda);
            return null;
        }

        System.out.println("✅ Control del buscador localizado.");
        printElementAttributes(searchTrigger);

        String triggerType = safeAttribute(searchTrigger, "type");
        safeClick(iosDriver, searchTrigger);

        if ("XCUIElementTypeSearchField".equals(triggerType)
                || "XCUIElementTypeTextField".equals(triggerType)) {
            return searchTrigger;
        }

        WebElement activeField = findFirstVisibleElement(
                iosDriver,
                getActiveSearchFieldLocators(),
                Duration.ofSeconds(15));

        if (activeField == null) {
            try {
                safeClick(iosDriver, searchTrigger);
                activeField = findFirstVisibleElement(
                        iosDriver,
                        getActiveSearchFieldLocators(),
                        Duration.ofSeconds(8));
            } catch (Exception ignored) {
                // Continúa con el diagnóstico.
            }
        }

        if (activeField == null) {
            printVisibleInteractiveElements(iosDriver);
            notifyWhenFail("❌ Se pulsó el buscador de " + tienda
                    + ", pero no apareció el campo para escribir.");
            return null;
        }

        System.out.println("✅ Campo de búsqueda activo.");
        printElementAttributes(activeField);

        return activeField;
    }

    private void makeSearch() {

        if (params.getTienda().equalsIgnoreCase("liverpool")) {
            keyboardClickActions("Go");
        } else {
            keyboardClickActions("Done");
        }
    }

    private String generateCardNumber(String typeCard) {

        if (typeCard.contains("existente")) {
            return "5232323435656576";

        } else if (typeCard.contains("visa")) {
            return "415327" + faker.number().digits(10);

        } else if (typeCard.contains("mastercard")) {
            return "530756" + faker.number().digits(10);

        } else if (typeCard.contains("American Express")) {
            return "343434" + faker.number().digits(9);

        } else {
            return "55563344" + faker.number().digits(8);
        }
    }

    public String generateStringsRandom(String tipo, int longitud) {

        String cadena = null;

        try {

            switch (tipo.toLowerCase()) {

                case "alphabetics":

                    cadena = new RandomStringGenerator.Builder().withinRange('a', 'z')
                            .withinRange('A', 'Z').get().generate(longitud);

                    break;

                case "numerics":

                    cadena = new RandomStringGenerator.Builder().withinRange('0', '9').get()
                            .generate(longitud);

                    break;

                case "alphanumerics":

                    cadena = new RandomStringGenerator.Builder().withinRange('0', '9')
                            .withinRange('a', 'z').withinRange('A', 'Z').get().generate(longitud);

                    break;

                default:

                    Assert.fail("\u001B[31m❌ No se encontró el tipo: " + tipo);
            }

            return cadena;

        } catch (Exception ex) {

            Assert.fail("\033[1;31m❌ Ocurrió un error en generateStringsRandom: "
                    + ex.getLocalizedMessage());
        }

        return cadena;
    }

    protected String generateRandomAliasCard() {

        String aliasTarjeta = faker.name().fullName() + faker.number().digits(2);

        aliasTarjeta = aliasTarjeta.replaceAll("\\.", "");

        return aliasTarjeta.substring(0, 1).toUpperCase() + aliasTarjeta.substring(1);
    }

    public void processToLogOut() throws Exception {

        if (params.getTienda().equalsIgnoreCase("liverpool")) {

            List<WebElement> elementsToWaitAndClick =
                    Arrays.asList(getGenElements().btnLowerTabMyAccount,
                            getGenElements().btnPreferences, getGenElements().btnCloseSession);

            for (WebElement element : elementsToWaitAndClick) {
                clickOnElement(element, 15);
            }

            clickOnElement(getGenElements().btnContinue, 15);

            waitForPresence(getGenElements().btnInicio, 25);

        } else {

            clickOnElement(getGenElements().btnLowerTabMyAccount, 15);

            clickOnElement(getGenElements().btnConfigurarCuenta, 15);

            waitForPresence(getGenElements().btnCloseSessionSB, 15);

            clickOnElement(getGenElements().btnCloseSessionSB, 15);

            if (returnFlagWhenTextFounded("Continue", 7)) {
                clickOnText("Continue", 15);
            }
        }
    }

    public GenericPage fillFieldsCard(String typeCard) throws Exception {

        String aliasTarjeta = generateRandomAliasCard();

        String nombreCompleto = faker.name().fullName().replaceAll("[.'\\\\]", "");

        String numeroDeTarjeta = generateCardNumber(typeCard);

        sendKeysUsingTheLabel("Alias de tarjeta*", aliasTarjeta, 20);

        sendKeysUsingTheLabel("Nombre completo*", nombreCompleto, 20);

        if (params.getTienda().equalsIgnoreCase("suburbia")) {
            swipeUpFromCenter(1);
        }

        sendKeysUsingTheLabel("Número de tarjeta*", numeroDeTarjeta, 15);

        clickOnElement(getGenElements().btnKeyboardAccept);

        varGlb.setPersistencia(aliasTarjeta + " *" + numeroDeTarjeta.substring(12));

        if (typeCard.equals("American Express")) {

            if (returnFlagWhenElementFounded(getGenElements().mmaaField, 5)) {

                fillExpiryDate();

                if (params.getTienda().equalsIgnoreCase("liverpool")) {

                    sendKeys(getGenElements().cvvField, "1234");

                } else {

                    sendKeys(getGenElements().cvvField, "12366");
                }

                clickOnText("Aceptar", 15);
            }

        } else {

            if (returnFlagWhenElementFounded(getGenElements().mmaaField, 5)) {

                fillExpiryDate();
                fillCvv();
            }
        }

        return this;
    }

    public GenericPage validateMessage(String message) throws ControlDeErrores {

        By messageToFind = By.xpath(String.format(
                "//XCUIElementTypeStaticText[" + "contains(@name, '%s') "
                        + "or contains(@label, '%s') " + "or contains(@value, '%s')]",
                message, message, message));

        isFlashElementDisplayed(messageToFind, 15);

        return this;
    }

    public GenericPage validateMessageTransaction(String message) throws ControlDeErrores {

        if (returnFlagWhenElementFounded(getGenElements().msgTransaction, 4)) {

            verifyIfElementIsPresent(getGenElements().msgTransaction, 1);

        } else {

            By messageToFind = By.xpath(String.format(
                    "//XCUIElementTypeStaticText[" + "contains(@name, '%s') "
                            + "or contains(@label, '%s') " + "or contains(@value, '%s')]",
                    message, message, message));

            isFlashElementDisplayed(messageToFind, 15);
        }

        return this;
    }

    private void fillExpiryDate() throws Exception {

        sendKeys(getGenElements().mmaaField, "0528");

        clickOnText("Aceptar", 15);
    }

    private void fillCvv() throws Exception {

        sendKeys(getGenElements().cvvField, "123");

        clickOnText("Aceptar", 15);
    }

    public void clickOnBtnClickAndCollect() throws Exception {

        if (params.getTienda().equalsIgnoreCase("liverpool")) {

            clickOnElement(getGenElements().btnStoresClickAndCollect);

        } else {

            loading();

            waitForPresence(getGenElements().btnSbStoresClickAndCollect, 20);

            clickOnElement(getGenElements().btnSbStoresClickAndCollect);
        }
    }

    public void clickBtnContinue() throws Exception {

        clickOnElement(getGenElements().btnContinueAddCard);
    }

    public void openUrl(String option) throws InterruptedException {

        try {

            ((InteractsWithApps) driver).activateApp("com.apple.mobilesafari");

        } catch (Exception ex) {

            notifyWhenFail("Failed to launch app: " + ex.getMessage());

            ex.printStackTrace();
        }

        driver.get(option);

        Thread.sleep(4000);
    }

    public void navigatesBack() throws Exception {

        returnPreviousPage();
    }

    private String getCurrentStore() {

        String tienda = System.getProperty("tienda", "").trim().toLowerCase();

        /*
         * Como respaldo se usa params cuando la propiedad tienda no fue enviada por Maven.
         */
        if (tienda.isEmpty() && params != null && params.getTienda() != null) {

            tienda = params.getTienda().trim().toLowerCase();
        }

        return tienda;
    }

    private boolean isLiverpool() {

        return "liverpool".equals(getCurrentStore());
    }

    private boolean isSuburbia() {

        return "suburbia".equals(getCurrentStore());
    }

    private List<By> getSearchTriggerLocators() {

        List<By> locators = new ArrayList<>();

        if (isSuburbia()) {
            locators.add(AppiumBy.iOSClassChain(
                    "**/XCUIElementTypeNavigationBar"
                            + "[`name == 'appClienteQA.HomeHosting'`]"
                            + "/XCUIElementTypeButton[1]"));

            locators.add(By.xpath(
                    "//XCUIElementTypeNavigationBar"
                            + "[@name='appClienteQA.HomeHosting']"
                            + "/XCUIElementTypeButton[1]"));

            locators.add(AppiumBy.iOSClassChain(
                    "**/XCUIElementTypeNavigationBar"
                            + "[`name CONTAINS 'HomeHosting'`]"
                            + "/XCUIElementTypeButton[1]"));

            locators.add(By.xpath(
                    "//XCUIElementTypeNavigationBar"
                            + "[contains(@name,'HomeHosting')]"
                            + "/XCUIElementTypeButton[1]"));

            locators.add(By.xpath(
                    "//XCUIElementTypeStaticText"
                            + "[@name='textFieldSearchHome']"
                            + "/parent::XCUIElementTypeButton"));

            locators.add(AppiumBy.iOSNsPredicateString(
                    "type == 'XCUIElementTypeButton' "
                            + "AND enabled == 1 "
                            + "AND visible == 1 "
                            + "AND x >= 10 "
                            + "AND x <= 25 "
                            + "AND y >= 55 "
                            + "AND y <= 75 "
                            + "AND width >= 250 "
                            + "AND width <= 290"));
        } else {
            locators.add(AppiumBy.accessibilityId("textFieldSearchHome"));
            locators.add(AppiumBy.accessibilityId("textFieldSearch"));

            locators.add(AppiumBy.iOSNsPredicateString(
                    "name == 'textFieldSearchHome' OR name == 'textFieldSearch'"));

            locators.add(AppiumBy.iOSClassChain(
                    "**/XCUIElementTypeNavigationBar"
                            + "[`name == 'appCliente.HomeView'`]"
                            + "/XCUIElementTypeButton[1]"));

            // Las versiones nuevas de Liverpool cambian el nombre interno del Home.
            locators.add(AppiumBy.iOSClassChain(
                    "**/XCUIElementTypeNavigationBar"
                            + "[`name CONTAINS[c] 'Home'`]"
                            + "/XCUIElementTypeButton[1]"));

            locators.add(By.xpath(
                    "//XCUIElementTypeNavigationBar[contains(@name,'Home')]"
                            + "/XCUIElementTypeButton[1]"));

            // Último respaldo: primer botón del NavigationBar de la pantalla Home.
            locators.add(AppiumBy.iOSClassChain(
                    "**/XCUIElementTypeNavigationBar/XCUIElementTypeButton[1]"));

            locators.add(By.xpath(
                    "(//XCUIElementTypeNavigationBar/XCUIElementTypeButton)[1]"));
        }

        locators.add(AppiumBy.accessibilityId("Buscar"));
        locators.add(AppiumBy.accessibilityId("Search"));

        locators.add(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeSearchField' "
                        + "AND visible == 1 "
                        + "AND enabled == 1"));

        locators.add(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeTextField' "
                        + "AND visible == 1 "
                        + "AND enabled == 1 AND ("
                        + "name CONTAINS[c] 'buscar' OR "
                        + "label CONTAINS[c] 'buscar' OR "
                        + "value CONTAINS[c] 'buscar' OR "
                        + "name CONTAINS[c] 'search' OR "
                        + "label CONTAINS[c] 'search' OR "
                        + "value CONTAINS[c] 'search')"));

        return locators;
    }

    private List<By> getActiveSearchFieldLocators() {

        List<By> locators = new ArrayList<>();

        locators.add(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeSearchField' "
                        + "AND visible == 1 "
                        + "AND enabled == 1"));

        locators.add(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeTextField' "
                        + "AND visible == 1 "
                        + "AND enabled == 1"));

        locators.add(AppiumBy.iOSClassChain(
                "**/XCUIElementTypeSearchField"
                        + "[`visible == 1 AND enabled == 1`]"));

        locators.add(AppiumBy.iOSClassChain(
                "**/XCUIElementTypeTextField"
                        + "[`visible == 1 AND enabled == 1`]"));

        locators.add(By.xpath(
                "//XCUIElementTypeSearchField"
                        + "[@visible='true' and @enabled='true']"));

        locators.add(By.xpath(
                "//XCUIElementTypeTextField"
                        + "[@visible='true' and @enabled='true']"));

        locators.add(AppiumBy.iOSNsPredicateString(
                "visible == 1 "
                        + "AND enabled == 1 AND ("
                        + "name CONTAINS[c] 'buscar' OR "
                        + "label CONTAINS[c] 'buscar' OR "
                        + "value CONTAINS[c] 'buscar' OR "
                        + "name CONTAINS[c] 'search' OR "
                        + "label CONTAINS[c] 'search' OR "
                        + "value CONTAINS[c] 'search')"));

        return locators;
    }

    private WebElement findTopNavigationSearchButton(IOSDriver iosDriver) {

        try {
            List<WebElement> buttons = iosDriver.findElements(
                    AppiumBy.iOSNsPredicateString(
                            "type == 'XCUIElementTypeButton' AND visible == 1 AND enabled == 1"));

            WebElement bestCandidate = null;
            int bestScore = Integer.MIN_VALUE;

            for (WebElement button : buttons) {
                try {
                    String name = safeAttribute(button, "name").toLowerCase();
                    String label = safeAttribute(button, "label").toLowerCase();
                    int x = button.getRect().getX();
                    int y = button.getRect().getY();
                    int width = button.getRect().getWidth();
                    int height = button.getRect().getHeight();

                    if (y < 35 || y > 190 || width < 20 || height < 20) {
                        continue;
                    }

                    if (name.contains("back") || label.contains("back")
                            || name.contains("bag") || label.contains("bag")
                            || name.contains("love") || label.contains("love")
                            || name.contains("inicio") || label.contains("inicio")
                            || name.contains("explorar") || label.contains("explorar")
                            || name.contains("cuenta") || label.contains("cuenta")) {
                        continue;
                    }

                    int score = 0;
                    if (name.contains("buscar") || label.contains("buscar")
                            || name.contains("search") || label.contains("search")) score += 100;
                    if (name.isBlank() && label.isBlank()) score += 40;
                    if (x < 330) score += 10;
                    if (width <= 100) score += 10;

                    if (score > bestScore) {
                        bestScore = score;
                        bestCandidate = button;
                    }
                } catch (Exception ignored) {
                    // El árbol pudo cambiar durante la evaluación.
                }
            }

            if (bestCandidate != null) {
                System.out.println("✅ Buscador localizado por posición en NavigationBar.");
                printElementAttributes(bestCandidate);
            }

            return bestCandidate;
        } catch (Exception exception) {
            System.out.println("⚠️ No fue posible aplicar el respaldo visual del buscador: "
                    + exception.getMessage());
            return null;
        }
    }

    private WebElement findFirstVisibleElement(IOSDriver iosDriver, List<By> locators,
            Duration timeoutPerLocator) {

        for (By locator : locators) {

            try {

                WebDriverWait wait = new WebDriverWait(iosDriver, timeoutPerLocator);

                WebElement element = wait.until(currentDriver -> {

                    List<WebElement> elements = currentDriver.findElements(locator);

                    for (WebElement candidate : elements) {

                        try {

                            if (candidate.isDisplayed() && candidate.isEnabled()) {

                                return candidate;
                            }

                        } catch (Exception ignored) {

                            System.out.println(
                                    "⚠️ El árbol iOS cambió mientras se validaba un elemento.");
                        }
                    }

                    return null;
                });

                if (element != null) {

                    System.out.println("✅ Elemento encontrado con: " + locator);

                    printElementAttributes(element);

                    return element;
                }

            } catch (Exception exception) {

                System.out.println("⚠️ No encontrado con: " + locator);
            }
        }

        return null;
    }

    private String safeAttribute(WebElement element, String attribute) {

        try {

            String value = element.getAttribute(attribute);

            return value == null ? "" : value;

        } catch (Exception ignored) {

            return "";
        }
    }

    private void printElementAttributes(WebElement element) {

        System.out.println("type=" + safeAttribute(element, "type") + " | name="
                + safeAttribute(element, "name") + " | label=" + safeAttribute(element, "label")
                + " | value=" + safeAttribute(element, "value") + " | rect=" + element.getRect());
    }

    private void safeClick(IOSDriver iosDriver, WebElement element) {

        try {

            element.click();
            return;

        } catch (Exception normalClickException) {

            System.out.println("⚠️ Falló click normal. Se intentará mobile: tap.");
        }

        try {

            int x = element.getRect().getX() + element.getRect().getWidth() / 2;

            int y = element.getRect().getY() + element.getRect().getHeight() / 2;

            iosDriver.executeScript("mobile: tap", Map.of("x", x, "y", y));

        } catch (Exception tapException) {

            notifyWhenFail("❌ Se encontró el buscador, pero no se pudo pulsar: "
                    + tapException.getMessage());
        }
    }

    private void printVisibleInteractiveElements(IOSDriver iosDriver) {

        try {

            List<WebElement> elements =
                    iosDriver.findElements(AppiumBy.iOSNsPredicateString("visible == 1 "
                            + "AND enabled == 1 AND (" + "type == 'XCUIElementTypeButton' OR "
                            + "type == 'XCUIElementTypeSearchField' OR "
                            + "type == 'XCUIElementTypeTextField' OR "
                            + "type == 'XCUIElementTypeOther')"));

            System.out.println("========== ELEMENTOS VISIBLES ==========");

            int maximum = Math.min(elements.size(), 100);

            for (int index = 0; index < maximum; index++) {

                WebElement element = elements.get(index);

                System.out.println("[" + index + "] type=" + safeAttribute(element, "type")
                        + " | name=" + safeAttribute(element, "name") + " | label="
                        + safeAttribute(element, "label") + " | value="
                        + safeAttribute(element, "value") + " | rect=" + element.getRect());
            }

            System.out.println("========================================");

        } catch (Exception exception) {

            System.out.println("⚠️ No se pudo imprimir el árbol iOS: " + exception.getMessage());
        }
    }


    private boolean submitSearch(IOSDriver iosDriver, WebElement searchField) {

        try {
            searchField.sendKeys(Keys.ENTER);
            System.out.println("✅ Búsqueda enviada con ENTER.");
            return true;
        } catch (Exception ignored) {
            System.out.println("⚠️ ENTER no ejecutó la búsqueda.");
        }

        try {
            iosDriver.executeScript(
                    "mobile: performEditorAction",
                    Map.of("action", "search"));
            System.out.println("✅ Búsqueda enviada con editorAction search.");
            return true;
        } catch (Exception ignored) {
            System.out.println("⚠️ editorAction search no funcionó.");
        }

        try {
            keyboardClickActions("Done");
            System.out.println("✅ Búsqueda enviada con Done.");
            return true;
        } catch (Exception ignored) {
            System.out.println("⚠️ Done no funcionó.");
        }

        try {
            keyboardClickActions("Go");
            System.out.println("✅ Búsqueda enviada con Go.");
            return true;
        } catch (Exception ignored) {
            System.out.println("⚠️ Go no funcionó.");
        }

        return false;
    }

    private void closeInitialSuburbiaPopups() {

        if (!isSuburbia()) {
            return;
        }

        IOSDriver iosDriver = (IOSDriver) this.driver;

        List<By> popupLocators = Arrays.asList(
                AppiumBy.accessibilityId("Aceptar"),
                AppiumBy.accessibilityId("Saltar"),
                AppiumBy.accessibilityId("Allow"),
                AppiumBy.accessibilityId("Permitir"),
                AppiumBy.accessibilityId("Continuar"),
                AppiumBy.accessibilityId("Ahora no"),
                AppiumBy.iOSNsPredicateString(
                        "type == 'XCUIElementTypeButton' "
                                + "AND visible == 1 "
                                + "AND enabled == 1 AND ("
                                + "name == 'Aceptar' OR label == 'Aceptar' OR "
                                + "name == 'Saltar' OR label == 'Saltar' OR "
                                + "name == 'Allow' OR label == 'Allow' OR "
                                + "name == 'Permitir' OR label == 'Permitir' OR "
                                + "name == 'Continuar' OR label == 'Continuar' OR "
                                + "name == 'Ahora no' OR label == 'Ahora no')"));

        for (int attempt = 1; attempt <= 6; attempt++) {
            boolean popupClosed = false;

            for (By locator : popupLocators) {
                try {
                    List<WebElement> elements = iosDriver.findElements(locator);

                    for (WebElement element : elements) {
                        if (element.isDisplayed() && element.isEnabled()) {
                            System.out.println("✅ Cerrando popup Suburbia: "
                                    + safeAttribute(element, "name"));
                            safeClick(iosDriver, element);
                            popupClosed = true;
                            wait(800);
                            break;
                        }
                    }

                    if (popupClosed) {
                        break;
                    }
                } catch (Exception ignored) {
                    // El popup no está presente.
                }
            }

            if (!popupClosed) {
                break;
            }
        }
    }

    private void waitForSuburbiaHome(Duration timeout) {

        if (!isSuburbia()) {
            return;
        }

        IOSDriver iosDriver = (IOSDriver) this.driver;

        List<By> homeLocators = Arrays.asList(
                AppiumBy.iOSNsPredicateString(
                        "type == 'XCUIElementTypeNavigationBar' "
                                + "AND name == 'appClienteQA.HomeHosting' "
                                + "AND visible == 1"),
                By.xpath(
                        "//XCUIElementTypeNavigationBar"
                                + "[@name='appClienteQA.HomeHosting' "
                                + "and @visible='true']"),
                AppiumBy.accessibilityId("Inicio"),
                AppiumBy.accessibilityId("Explorar"),
                AppiumBy.accessibilityId("Mi cuenta"));

        long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < endTime) {
            for (By locator : homeLocators) {
                try {
                    List<WebElement> elements = iosDriver.findElements(locator);

                    for (WebElement element : elements) {
                        if (element.isDisplayed()) {
                            System.out.println("✅ Home Suburbia disponible.");
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    // El árbol de iOS puede estar actualizándose.
                }
            }

            wait(400);
        }

        System.out.println("⚠️ Home Suburbia no se confirmó dentro del tiempo.");
    }

}
