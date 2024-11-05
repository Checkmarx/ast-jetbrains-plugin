package com.checkmarx.intellij.ui.utils;

import org.intellij.lang.annotations.Language;

public class Xpath {
    @Language("XPath")
    public static final String SETTINGS_ACTION = "//div[@myicon='settings.svg']";
    @Language("XPath")
    public static final String SETTINGS_BUTTON = "//div[@text='Open Settings']";
    @Language("XPath")
    public
    static final String EXPAND_ACTION = "//div[@tooltiptext='Expand all']";
    @Language("XPath")
    public
    static final String COLLAPSE_ACTION = "//div[@tooltiptext='Collapse all']";
    @Language("XPath")
    public
    static final String FILTER_BY_ACTION = "//div[@myicon='filter.svg']";
    @Language("XPath")
    public
    static final String GROUP_BY_ACTION = "//div[@myicon='groupBy.svg']";
    @Language("XPath")
    public
    static final String CLONE_BUTTON = "//div[@text='Clone']";
    @Language("XPath")
    public
    static final String FIELD_NAME = "//div[@name='%s']";
    @Language("XPath")
    public
    static final String CHANGES_COMMENT = "//div[@accessiblename='%s' and @class='JLabel' and @text='<html>%s</html>']";
    @Language("XPath")
    public
    static final String VALIDATE_BUTTON = "//div[@class='JButton' and @text='Validate connection']";
    @Language("XPath")
    public
    static final String STATE_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='TO_VERIFY']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='URGENT']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    public
    static final String SEVERITY_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='MEDIUM']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='HIGH']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='LOW']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    public
    static final String SCAN_FIELD = "//div[@class='TextFieldWithProcessing']";
    @Language("XPath")
    public
    static final String TREE = "//div[@class='Tree']";
    @Language("XPath")
    public
    static final String LINK_LABEL = "//div[@class='CxLinkLabel']";
    @Language("XPath")
    public
    static final String NO_INFORMATION = "//div[@text.key='NO_INFORMATION']";
    @Language("XPath")
    public
    static final String EDITOR = "//div[@class='EditorComponentImpl']";
    @Language("XPath")
    public
    static final String JLIST = "//div[@class='JList']";
    @Language("XPath")
    public
    static final String START_SCAN_BTN = "//div[contains(@myaction.key, 'START_SCAN_ACTION')]";
    @Language("XPath")
    public
    static final String CANCEL_SCAN_BTN = "//div[@myaction.key='CANCEL_SCAN_ACTION']";
    @Language("XPath")
    public
    static final String MY_LIST = "//div[@class='MyList']";
    @Language("XPath")
    public
    static final String FLAT_WELCOME_FRAME = "//div[@class='FlatWelcomeFrame']";
    @Language("XPath")
    public
    static final String FROM_VCS_TAB = "//div[@defaulticon='fromVCSTab.svg']";
    @Language("XPath")
    public
    static final String BORDERLESS_TEXT_FIELD = "//div[@class='BorderlessTextField']";
    @Language("XPath")
    public
    static final String BASE_LABEL = "//div[@class='BaseLabel']";
    @Language("XPath")
    public
    static final String VISIBLE_TEXT = "//div[@visible_text='%s']";
    @Language("XPath")
    public
    static final String TRUST_PROJECT = "//div[@text='Trust Project']";
    @Language("XPath")
    public
    static final String CHECKMARX_STRIPE_BTN = "//div[@text='Checkmarx' and @class='StripeButton']";
    @Language("XPath")
    public
    static final String VALIDATING_CONNECTION = "//div[@accessiblename='Validating...']";
    @Language("XPath")
    public
    static final String SUCCESS_CONNECTION = "//div[@accessiblename.key='VALIDATE_SUCCESS']";
    @Language("XPath")
    public
    static final String OK_BTN = "//div[@text='OK']";
    @Language("XPath")
    public
    static final String NO_PROJECT_SELECTED = "//div[@class='ActionButtonWithText' and @visible_text='Project: none']";
    @Language("XPath")
    public
    static final String NO_BRANCH_SELECTED = "//div[@class='ActionButtonWithText' and @visible_text='Branch: none']";
    @Language("XPath")
    public
    static final String NO_SCAN_SELECTED = "//div[@class='ActionButtonWithText' and @visible_text='Scan: none']";
    @Language("XPath")
    public
    static final String CLEAR_BTN = "//div[@myicon='refresh.svg']";
    @Language("XPath")
    public
    static final String TRIAGE_LOW = "//div[@class='ComboBox'][.//div[@visible_text='LOW']]";
    @Language("XPath")
    public
    static final String TRIAGE_CONFIRMED = "//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]";
    @Language("XPath")
    public
    static final String TRIAGE_COMMENT = "//div[@class='JTextField']";
    @Language("XPath")
    public
    static final String UPDATE_BTN = "//div[@text='Update']";
    @Language("XPath")
    public
    static final String TAB_CHANGES = "//div[@text='Changes']";
    @Language("XPath")
    public
    static final String TAB_CHANGES_CONTENT = "//div[@accessiblename='Changes' and @accessiblename.key='changes.default.changelist.name CHANGES' and @class='JBTabbedPane']//div[@class='JPanel']";
    @Language("XPath")
    public
    static final String TAB_LEARN_MORE = "//div[@text.key='LEARN_MORE']";
    @Language("XPath")
    public
    static final String TAB_RISK = "//div[@accessiblename.key='RISK']";
    @Language("XPath")
    public
    static final String CAUSE = "//div[@accessiblename.key='CAUSE']";
    @Language("XPath")
    public
    static final String TAB_RECOMMENDATIONS = "//div[@accessiblename.key='GENERAL_RECOMMENDATIONS']";
    @Language("XPath")
    public
    static final String TAB_RECOMMENDATIONS_EXAMPLES = "//div[@text.key='REMEDIATION_EXAMPLES']";
    @Language("XPath")
    public
    static final String AUTO_REMEDIATION = "//div[@tooltiptext.key='AUTO_REMEDIATION_TOOLTIP']";
    @Language("XPath")
    public
    static final String MAGIC_RESOLVE = "//div[@defaulticon='magicResolve.svg']";
    @Language("XPath")
    public
    static final String PROJECT_DOES_NOT_MATCH = "//div[@accessiblename.key='PROJECT_DOES_NOT_MATCH_TITLE']";
    @Language("XPath")
    public
    static final String BRANCH_DOES_NOT_MATCH = "//div[@accessiblename.key='BRANCH_DOES_NOT_MATCH_TITLE']";
    @Language("XPath")
    public
    static final String SCAN_FINISHED = "//div[@accessiblename.key='SCAN_FINISHED']";
    @Language("XPath")
    public
    static final String LOAD_RESULTS = "//div[@class='LinkLabel']";
    @Language("XPath")
    public
    static final String LATEST_SCAN = "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']";
    @Language("XPath")
    public static final String HAS_SELECTION = "//div[@class='ActionButtonWithText' and starts-with(@visible_text,'%s: ')]";
    @Language("XPath")
    public static final String SCAN_ID_SELECTION = "//div[@class='ActionButtonWithText' and substring(@visible_text, string-length(@visible_text) - string-length('%s') + 1)  = '%s']";
    @Language("XPath")
    public
    static final String ASCA_INSTALL_SUCCESS = "//div[@class='JBLabel' and @accessiblename='AI Secure Coding Assistant started.']";
    @Language("XPath")
    public
    static final String ASCA_CHECKBOX = "//div[@class='JBCheckBox' and @text='Scan your file as you code']";
            ;

}
