package com.checkmarx.intellij.ui.utils;

import org.intellij.lang.annotations.Language;

public class Xpath {
    @Language("XPath")
    public static final String SETTINGS_ACTION = "//div[@myicon='settings.svg']";
    @Language("XPath")
    public static final String SETTINGS_BUTTON = "//div[@text='Open Settings']";
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
    static final String CONNECT_BUTTON = "//div[@class='JButton' and @text='Connect to Checkmarx']";
    @Language("XPath")
    public
    static final String STATE_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='TO_VERIFY']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='CONFIRMED']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='URGENT']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    public
    static final String SEVERITY_COMBOBOX_ARROW = "//div[@class='ComboBox'][.//div[@visible_text='MEDIUM']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='HIGH']]//div[@class='BasicArrowButton']|//div[@class='ComboBox'][.//div[@visible_text='LOW']]//div[@class='BasicArrowButton']";
    @Language("XPath")
    public
    static final String SCAN_FIELD = "//div[@class='TextFieldWithProcessing'][1]";
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
    static final String ASCA_INSTALL_SUCCESS = "//div[@class='JBLabel' and @accessiblename='AI Secure Coding Assistant Engine started.']";
    @Language("XPath")
    public
    static final String LOGOUT_BUTTON = "//div[@text='Log out']";
    @Language("XPath")
    public
    static final String LOGOUT_CONFIRM_YES = "//div[@text='Yes']";
    @Language("XPath")
    public
    static final String API_KEY_RADIO = "//div[@text='API Key' or @visible_text='API Key']";
    @Language("XPath")
    public
    static final String WELCOME_CLOSE_BUTTON = "//div[@text='Close']";
    @Language("XPath")
    public
    static final String WELCOME_TITLE = "//div[@text.key='WELCOME_TITLE']";
    @Language("XPath")
    public
    static final String WELCOME_ASSIST_TITLE = "//div[@visible_text_keys='WELCOME_ASSIST_TITLE']";
    @Language("XPath")
    public
    static final String CODE_SMART_CHECKBOX = "//div[@class='JBCheckBox']";
    @Language("XPath")
    public
    static final String WELCOME_PAGE_IMAGE = "//div[@defaulticon='welcomePageScanner.svg']";
    static final String EXPAND_ALL_FOLDER = "//div[@myicon='expandall.svg']";
    @Language("XPath")
    public
    static final String RUN_SCAN_LOCAL = "//div[@class='LinkLabel']";
    @Language("XPath")
    public
    static final String CLOSE_RUN_SCAN_LOCAL_NOTIFICATION_WINDOW = "//div[@myicon='close.svg']";
    public
    static final String HELP_PLUGIN_LINK = "//div[@visible_text_keys='HELP_JETBRAINS']";
    @Language("XPath")
    public
    static final String OAUTH_RADIO = "//div[@text='OAuth']";
    @Language("XPath")
    public
    static final String OAUTH_POPUP_CANCEL_BUTTON = "//div[@class='JPanel'][.//div[@class='InplaceButton']]//div[@text='Cancel']";
    @Language("XPath")
    public static final String INVALID_BASE_URL_ERROR =
            "//div[@accessiblename='Please check the server address of your Checkmarx One environment.' and @class='JBLabel' and @text='<html>Please check the server address of your Checkmarx One environment.</html>']";
    @Language("XPath")
    public static final String INVALID_TENANT_ERROR =
            "//div[@accessiblename='Tenant \"invalid-tenant\" not found. Please check your tenant name.' and @class='JBLabel' and @text='<html>Tenant \"invalid-tenant\" not found. Please check your tenant name.</html>']";
    //CxOne Assist Page XPaths
    @Language("XPath")
    public static final String GO_TO_CXONE_ASSIST_LINK = "//div[@mytext='Go to Checkmarx One Assist']";
    @Language("XPath")
    public static final String CXONE_SETTINGS_MENU_BUTTON = "//div[@class='MyTree' and contains(@visible_text,'Checkmarx One')]";
    @Language("XPath")
    public static final String CXONE_ASSIST_BREADCRUMB = "//div[@class='Breadcrumbs']";
    @Language("XPath")
    public static final String CXONE_ASSIST_BACK_BUTTON = "//div[@myicon='back.svg']";
    @Language("XPath")
    public static final String ASSIST_ASCA_LABLE = "//div[@accessiblename='Checkmarx AI Secure Coding Assistant (ASCA): Activate ASCA:' and @class='JBLabel' and @text='<html>Checkmarx AI Secure Coding Assistant (ASCA): <b>Activate ASCA:</b></html>']";
    @Language("XPath")
    public static final String ASCA_ENGINE_SELECTION_CHECKBOX = "//div[@text='Scan your file as you code']";
    @Language("XPath")
    public static final String ASSIST_OSS_REALTIME_LABEL = "//div[@accessiblename.key='OSS_REALTIME_TITLE']";
    @Language("XPath")
    public static final String OSS_REALTIME_ENGINE_CHECKBOX = "//div[@visible_text_keys='OSS_REALTIME_CHECKBOX']";
    @Language("XPath")
    public static final String ASSIST_SECRET_DETECTION_LABEL = "//div[@accessiblename.key='SECRETS_REALTIME_TITLE']";
    @Language("XPath")
    public static final String SECRET_DETECTION_ENGINE_CHECKBOX = "//div[@visible_text_keys='SECRETS_REALTIME_CHECKBOX']";
    @Language("XPath")
    public static final String ASSIST_CONTAINER_REALTIME_LABEL = "//div[@accessiblename.key='CONTAINERS_REALTIME_TITLE']";
    @Language("XPath")
    public static final String CONTAINER_REALTIME_ENGINE_CHECKBOX = "//div[@visible_text_keys='CONTAINERS_REALTIME_CHECKBOX']";
    @Language("XPath")
    public static final String ASSIST_IAC_REALTIME_LABEL = "//div[@accessiblename.key='IAC_REALTIME_TITLE']";
    @Language("XPath")
    public static final String IAC_REALTIME_ENGINE_CHECKBOX = "//div[@visible_text_keys='IAC_REALTIME_CHECKBOX']";
    @Language("XPath")
    public static final String ASSIST_CONTAINER_MANAGEMENT_DD_LABLE = "//div[@accessiblename.key='IAC_REALTIME_SCANNER_PREFIX']";
    @Language("XPath")
    public static final String CONTAINER_MANAGEMENT_DD_DESCRIPTION = "//div[@visible_text_keys='CONTAINERS_TOOL_DESCRIPTION']";
    @Language("XPath")
    public static final String CONTAINER_MANAGEMENT_DD = "//div[@visible_text_keys='CONTAINERS_TOOL_DESCRIPTION']";
    @Language("XPath")
    public static final String CHECKMARX_MCP_LABEL = "//div[@visible_text_keys='CONTAINERS_TOOL_DESCRIPTION']";
    @Language("XPath")
    public static final String MCP_DESCRIPTION = "//div[@visible_text_keys='MCP_DESCRIPTION']";
    @Language("XPath")
    public static final String INSTALL_MCP_LINK = "//div[@mytext='Install MCP']";
    @Language("XPath")
    public static final String EDIT_MCP_LINK = "//div[@mytext='Edit in mcp.json']";

    // Project Selection Pannel
    @Language("XPath")
    public static final String SELECTED_PROJECT_NAME_NONE = "//div[@visible_text='Project: none']";
    @Language("XPath")
    public static final String SELECTED_BRANCH_NAME_NONE = "//div[@visible_text='Branch: none']";
    @Language("XPath")
    public static final String SELECTED_SCAN_ID_NONE = "//div[@visible_text='Scan: none']";
    @Language("XPath")
    public static final String RESET_PROJECT_SELECTION = "//div[@myicon='refresh.svg']";
    @Language("XPath")
    public static final String START_SCAN_BTN = "//div[contains(@myaction.key, 'START_SCAN_ACTION')]";
    @Language("XPath")
    public static final String CANCEL_SCAN_BTN = "//div[@myaction.key='CANCEL_SCAN_ACTION']";
    @Language("XPath")
    public static final String SEVERITY_CRITICAL_ICON = "//div[@myicon='critical.svg']";
    @Language("XPath")
    public static final String SEVERITY_HIGH_ICON = "//div[@myicon='high.svg']";
    @Language("XPath")
    public static final String SEVERITY_MEDIUM_ICON = "//div[@myicon='medium.svg']";
    @Language("XPath")
    public static final String SEVERITY_LOW_ICON = "//div[@myicon='low.svg']";
    @Language("XPath")
    public static final String EXPAND_ACTION = "//div[@tooltiptext='Expand all']";
    @Language("XPath")
    public static final String COLLAPSE_ACTION = "//div[@tooltiptext='Collapse all']";
    @Language("XPath")
    public static final String FILTER_BY_ACTION = "//div[@myicon='filter.svg']";
    @Language("XPath")
    public static final String GROUP_BY_ACTION = "//div[@myicon='groupBy.svg']";
    @Language("XPath")
    public static final String PROJECT_NAME_NULL = "//div[@visible_text='Project: ...']";

    //Realtime Scan XPaths CxOne Assist
    @Language("XPath")
    public static final String GETTING_RESULT_TEXT = "//div[@class='Tree' and contains(@visible_text,'Getting results')]";
    @Language("XPath")
    public static final String FILE_SCAN_PROGRESS_BAR = "//div[@class='TextPanel' and contains(@accessiblename,'Checkmarx is Scanning File')]";
    @Language("XPath")
    public static final String CX_ASSIST_FINDING_TAB = "//div[@class='ContentTabLabel' and contains(@accessiblename,'Checkmarx One Assist Findings')]";
    @Language("XPath")
    public static final String SCAN_PROGRESS_BAR = "//div[@mytext.key='STARTING_CHECKMARX_SCAN']";
    @Language("XPath")
    public static final String FINDINGS_TREE_XPATH = "//div[@class='SimpleTree']";
    @Language("XPath")
    public static final String SCAN_RESULTS_TAB = "//div[@text='Scan Results']";
}
