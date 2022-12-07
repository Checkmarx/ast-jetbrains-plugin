package com.checkmarx.intellij.ui;

import org.intellij.lang.annotations.Language;

public class Xpath {
    @Language("XPath")
    protected static final String SETTINGS_ACTION = "//div[@myicon='settings.svg']";
    @Language("XPath")
    protected static final String SETTINGS_BUTTON = "//div[@text='Open Settings']";
    @Language("XPath")
    protected static final String EXPAND_ACTION = "//div[@tooltiptext='Expand all']";
    @Language("XPath")
    protected static final String COLLAPSE_ACTION = "//div[@tooltiptext='Collapse all']";
    @Language("XPath")
    protected static final String FILTER_BY_ACTION = "//div[@myicon='filter.svg']";
    @Language("XPath")
    protected static final String GROUP_BY_ACTION = "//div[@myicon='groupBy.svg']";
    @Language("XPath")
    protected static final String CLONE_BUTTON = "//div[@text='Clone']";
    @Language("XPath")
    protected static final String FIELD_NAME = "//div[@name='%s']";
    @Language("XPath")
    protected static final String CHANGES_COMMENT = "//div[@accessiblename='%s' and @class='JLabel' and @text='<html>%s</html>']";
    @Language("XPath")
    protected static final String VALIDATE_BUTTON = "//div[@class='JButton' and @text='Validate connection']";
    @Language("XPath")
    protected static final String STATE_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='TO_VERIFY']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='URGENT']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    protected static final String SEVERITY_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='MEDIUM']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='HIGH']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='LOW']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    protected static final String SCAN_FIELD = "//div[@class='TextFieldWithProcessing']";
    @Language("XPath")
    protected static final String TREE = "//div[@class='Tree']";
    @Language("XPath")
    protected static final String LINK_LABEL = "//div[@class='CxLinkLabel']";
    @Language("XPath")
    protected static final String EDITOR = "//div[@class='EditorComponentImpl']";
    @Language("XPath")
    protected static final String JLIST = "//div[@class='JList']";
    @Language("XPath")
    protected static final String START_SCAN_BTN = "//div[contains(@myaction.key, 'START_SCAN_ACTION')]";
    @Language("XPath")
    protected static final String CANCEL_SCAN_BTN = "//div[@myaction.key='CANCEL_SCAN_ACTION']";
}
