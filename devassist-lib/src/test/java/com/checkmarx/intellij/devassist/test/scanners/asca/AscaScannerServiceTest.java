package com.checkmarx.intellij.devassist.test.scanners.asca;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.devassist.scanners.asca.AscaScannerService;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AscaScannerServiceTest {

    private AscaScannerService service;
    private PsiFile psiFile;
    private VirtualFile virtualFile;

    @BeforeEach
    void setUp() {
        service = new AscaScannerService();
        psiFile = mock(PsiFile.class);
        virtualFile = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getExtension()).thenReturn("java");
    }

    @Test
    @DisplayName("createConfig builds ASCA engine configuration")
    void testCreateConfig() {
        var config = AscaScannerService.createConfig();
        assertEquals("ASCA", config.getEngineName());
        assertNotNull(config.getConfigSection());
        assertNotNull(config.getActivateKey());
    }

    @Test
    @DisplayName("shouldScanFile rejects paths under node_modules")
    void shouldScanFileRejectsNodeModules() {
        assertFalse(service.shouldScanFile("/project/node_modules/Main.java", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile rejects unsupported extensions")
    void shouldScanFileRejectsUnsupportedExtension() {
        when(virtualFile.getExtension()).thenReturn("txt");
        assertFalse(service.shouldScanFile("/project/Main.txt", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile rejects xml extension")
    void shouldScanFileRejectsXmlExtension() {
        when(virtualFile.getExtension()).thenReturn("xml");
        assertFalse(service.shouldScanFile("/project/pom.xml", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts supported extensions")
    void shouldScanFileAcceptsSupportedExtension() {
        assertTrue(service.shouldScanFile("/project/Main.java", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts .cs extension")
    void shouldScanFileAcceptsCsExtension() {
        when(virtualFile.getExtension()).thenReturn("cs");
        assertTrue(service.shouldScanFile("/project/Main.cs", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts .go extension")
    void shouldScanFileAcceptsGoExtension() {
        when(virtualFile.getExtension()).thenReturn("go");
        assertTrue(service.shouldScanFile("/project/main.go", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts .py extension")
    void shouldScanFileAcceptsPyExtension() {
        when(virtualFile.getExtension()).thenReturn("py");
        assertTrue(service.shouldScanFile("/project/main.py", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts .js extension")
    void shouldScanFileAcceptsJsExtension() {
        when(virtualFile.getExtension()).thenReturn("js");
        assertTrue(service.shouldScanFile("/project/app.js", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile accepts .jsx extension")
    void shouldScanFileAcceptsJsxExtension() {
        when(virtualFile.getExtension()).thenReturn("jsx");
        assertTrue(service.shouldScanFile("/project/App.jsx", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile returns false when virtual file is missing")
    void shouldScanFileRejectsWhenVirtualFileNull() {
        PsiFile psiWithoutVirtual = mock(PsiFile.class);
        when(psiWithoutVirtual.getVirtualFile()).thenReturn(null);
        assertFalse(service.shouldScanFile("/project/Main.java", psiWithoutVirtual));
    }

    @Test
    @DisplayName("shouldScanFile returns false when extension is null")
    void shouldScanFileRejectsWhenExtensionNull() {
        when(virtualFile.getExtension()).thenReturn(null);
        assertFalse(service.shouldScanFile("/project/Makefile", psiFile));
    }

    @Test
    @DisplayName("scan returns null when file is not eligible")
    void scanReturnsNullWhenShouldScanFileFalse() {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(false).when(spyService).shouldScanFile(anyString(), eq(psiFile));
        assertNull(spyService.scan(psiFile, "/project/Main.java"));
    }

    @Test
    @DisplayName("installAsca returns true when wrapper reports success")
    void installAscaReturnsTrueOnSuccess() throws Exception {
        try (MockedStatic<CxWrapperFactory> factory =
                     mockStatic(CxWrapperFactory.class)) {
            CxWrapper wrapper = mock(CxWrapper.class);
            ScanResult scanResult = mock(ScanResult.class);
            when(scanResult.getError()).thenReturn(null);
            when(wrapper.ScanAsca(anyString(), eq(true), anyString(), isNull())).thenReturn(scanResult);

            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertTrue(service.installAsca());
        }
    }

    @Test
    @DisplayName("installAsca returns false when scan result has error")
    void installAscaReturnsFalseWhenResultHasError() throws Exception {
        try (MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            CxWrapper wrapper = mock(CxWrapper.class);
            ScanResult scanResult = mock(ScanResult.class, RETURNS_DEEP_STUBS);
            when(scanResult.getError().getDescription()).thenReturn("ASCA engine not found");
            when(wrapper.ScanAsca(anyString(), eq(true), anyString(), isNull())).thenReturn(scanResult);

            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            assertFalse(service.installAsca());
        }
    }

    @Test
    @DisplayName("installAsca returns false when wrapper throws exception")
    void installAscaReturnsFalseOnException() throws Exception {
        try (MockedStatic<CxWrapperFactory> factory =
                     mockStatic(CxWrapperFactory.class)) {
            factory.when(CxWrapperFactory::build)
                    .thenThrow(new RuntimeException("boom"));
            assertFalse(service.installAsca());
        }
    }

    @Test
    @DisplayName("sanitizeFileName strips dangerous characters")
    void sanitizeFileNameRemovesDangerousCharacters() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String sanitized = (String) sanitize.invoke(service, "../..\\evil:name?.java");
        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains("\\"));
        assertFalse(sanitized.contains(":"));
        assertTrue(sanitized.endsWith(".java"));
    }

    @Test
    @DisplayName("sanitizeFileName returns temp_file.tmp for null input")
    void sanitizeFileName_NullInput_ReturnsTempFile() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, (Object) null);
        assertEquals("temp_file.tmp", result);
    }

    @Test
    @DisplayName("sanitizeFileName returns temp_file.tmp for empty input")
    void sanitizeFileName_EmptyInput_ReturnsTempFile() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "   ");
        assertEquals("temp_file.tmp", result);
    }

    @Test
    @DisplayName("sanitizeFileName truncates names longer than 255 chars with extension preserved")
    void sanitizeFileName_LongName_TruncatesWithExtension() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String longName = "a".repeat(300) + ".java";
        String result = (String) sanitize.invoke(service, longName);
        assertTrue(result.length() <= 255, "Sanitized name should be within 255 chars");
        assertTrue(result.endsWith(".java"), "Extension should be preserved after truncation");
    }

    @Test
    @DisplayName("sanitizeFileName handles dot-only names returning temp_file.tmp")
    void sanitizeFileName_DotsOnly_ReturnsTempFile() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "..");
        assertEquals("temp_file.tmp", result);
    }

    @Test
    @DisplayName("sanitizeFileName with normal file name preserves it")
    void sanitizeFileName_NormalName_PreservesName() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "MyClass.java");
        assertEquals("MyClass.java", result);
    }

    // ===== createConfig completeness =====

    @Test
    @DisplayName("createConfig sets disabledMessage and enabledMessage")
    void testCreateConfig_allFields() {
        var config = AscaScannerService.createConfig();
        assertNotNull(config.getDisabledMessage());
        assertNotNull(config.getEnabledMessage());
        assertEquals("ASCA", config.getEngineName());
    }

    // ===== shouldScanFile — additional rejected extensions =====

    @Test
    @DisplayName("shouldScanFile rejects .ts extension (TypeScript not in supported list)")
    void shouldScanFileRejectsTsExtension() {
        when(virtualFile.getExtension()).thenReturn("ts");
        assertFalse(service.shouldScanFile("/project/App.ts", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile rejects .tsx extension")
    void shouldScanFileRejectsTsxExtension() {
        when(virtualFile.getExtension()).thenReturn("tsx");
        assertFalse(service.shouldScanFile("/project/App.tsx", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile rejects .html extension")
    void shouldScanFileRejectsHtmlExtension() {
        when(virtualFile.getExtension()).thenReturn("html");
        assertFalse(service.shouldScanFile("/project/index.html", psiFile));
    }

    // ===== scan — exception path returns null =====

    @Test
    @DisplayName("scan returns null when shouldScanFile returns true but IntelliJ services unavailable")
    void scan_shouldScanFileTrue_exceptionFromRunAscaScan_returnsNull() {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(true).when(spyService).shouldScanFile(anyString(), eq(psiFile));
        // getProject() returns null → NullPointerException caught by catch(Exception)
        when(psiFile.getProject()).thenReturn(null);

        assertNull(spyService.scan(psiFile, "/project/Main.java"));
    }

    // ===== sanitizeFileName — additional special chars =====

    @Test
    @DisplayName("sanitizeFileName replaces asterisk and quote characters")
    void sanitizeFileName_AsteriskAndQuote_AreReplaced() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "file*name\"test.java");
        assertFalse(result.contains("*"), "Asterisk should be removed");
        assertFalse(result.contains("\""), "Quote should be removed");
        assertTrue(result.endsWith(".java"));
    }

    @Test
    @DisplayName("sanitizeFileName replaces angle brackets and pipe")
    void sanitizeFileName_AngleBracketsAndPipe_AreReplaced() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "<output>|file.java");
        assertFalse(result.contains("<"), "< should be removed");
        assertFalse(result.contains(">"), "> should be removed");
        assertFalse(result.contains("|"), "| should be removed");
    }

    @Test
    @DisplayName("sanitizeFileName collapses multiple dots to single dot")
    void sanitizeFileName_MultipleConsecutiveDots_CollapsedToSingle() throws Exception {
        Method sanitize = AscaScannerService.class.getDeclaredMethod("sanitizeFileName", String.class);
        sanitize.setAccessible(true);
        String result = (String) sanitize.invoke(service, "file...name.java");
        assertFalse(result.contains(".."), "Multiple dots should be collapsed");
    }

    // ===== scan() — getFileContent returns null → runAscaScan returns null =====

    @Test
    @DisplayName("scan returns null when getFileContent returns null (no document, no virtual file content)")
    void scan_getFileContentNull_returnsNull() {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(true).when(spyService).shouldScanFile(anyString(), eq(psiFile));

        Project mockProject = mock(Project.class);
        when(psiFile.getProject()).thenReturn(mockProject);

        try (MockedStatic<ApplicationManager> appMgr = mockStatic(ApplicationManager.class);
             MockedStatic<PsiDocumentManager> psiDocMgr = mockStatic(PsiDocumentManager.class)) {

            Application mockApp = mock(Application.class);
            appMgr.when(ApplicationManager::getApplication).thenReturn(mockApp);

            // runReadAction returns null (no document, virtualFile getBytes throws)
            doAnswer(inv -> null).when(mockApp).runReadAction(any(Computable.class));

            assertNull(spyService.scan(psiFile, "/project/Main.java"));
        }
    }

    // ===== scan() — full happy path with mocked CxWrapper =====

    @Test
    @DisplayName("scan returns AscaScanResultAdaptor when CxWrapper succeeds")
    @SuppressWarnings("unchecked")
    void scan_fullHappyPath_returnsAdaptor() throws Exception {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(true).when(spyService).shouldScanFile(anyString(), eq(psiFile));

        Project mockProject = mock(Project.class);
        when(psiFile.getProject()).thenReturn(mockProject);
        when(psiFile.getName()).thenReturn("Main.java");
        when(virtualFile.getPath()).thenReturn("/project/Main.java");
        when(virtualFile.isInLocalFileSystem()).thenReturn(true); // ignoreFiles() check

        try (MockedStatic<ApplicationManager> appMgr = mockStatic(ApplicationManager.class);
             MockedStatic<PsiDocumentManager> psiDocMgrStatic = mockStatic(PsiDocumentManager.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class);
             MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {

            Application mockApp = mock(Application.class);
            appMgr.when(ApplicationManager::getApplication).thenReturn(mockApp);

            // runReadAction: return file content from document
            Document mockDoc = mock(Document.class);
            when(mockDoc.getText()).thenReturn("public class Main {}");
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocMgrStatic.when(() -> PsiDocumentManager.getInstance(mockProject)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mockDoc);

            doAnswer(inv -> ((Computable<?>) inv.getArgument(0)).compute())
                    .when(mockApp).runReadAction(any(Computable.class));

            // CxWrapper mock that returns a scan result
            CxWrapper wrapper = mock(CxWrapper.class);
            factoryMock.when(CxWrapperFactory::build).thenReturn(wrapper);
            ScanResult scanResult = mock(ScanResult.class);
            when(scanResult.getError()).thenReturn(null);
            when(scanResult.getScanDetails()).thenReturn(Collections.emptyList());
            when(wrapper.ScanAsca(anyString(), anyBoolean(), anyString(), any())).thenReturn(scanResult);

            // DevAssistUtils.getIgnoreFilePath
            devUtilsMock.when(() -> DevAssistUtils.getIgnoreFilePath(any())).thenReturn(null);

            // TelemetryService.logScanResults — no-op
            telemetryMock.when(() -> TelemetryService.logScanResults(
                    any(com.checkmarx.intellij.devassist.common.ScanResult.class),
                    any(com.checkmarx.intellij.devassist.utils.ScanEngine.class))).thenAnswer(inv -> null);

            com.checkmarx.intellij.devassist.common.ScanResult<?> result =
                    spyService.scan(psiFile, "/project/Main.java");

            assertNotNull(result);
        }
    }

    // ===== scan() — exception in scanAscaFile =====

    @Test
    @DisplayName("handleScanResult: null scanResult logs warning and returns without throwing")
    void handleScanResult_nullScanResult_logsAndReturns() throws Exception {
        Method method = AscaScannerService.class.getDeclaredMethod("handleScanResult", PsiFile.class, ScanResult.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, psiFile, null));
    }

    @Test
    @DisplayName("handleScanResult: scanResult with error logs warning and returns")
    void handleScanResult_withError_logsAndReturns() throws Exception {
        ScanResult scanResult = mock(ScanResult.class, RETURNS_DEEP_STUBS);
        // getError() returns non-null (deep stubs create a non-null mock automatically)
        when(scanResult.getError().getDescription()).thenReturn("engine error");

        Method method = AscaScannerService.class.getDeclaredMethod("handleScanResult", PsiFile.class, ScanResult.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, psiFile, scanResult));
    }

    @Test
    @DisplayName("handleScanResult: scanResult with findings logs success")
    void handleScanResult_withFindings_logsSuccess() throws Exception {
        ScanResult scanResult = mock(ScanResult.class);
        when(scanResult.getError()).thenReturn(null);
        when(scanResult.getScanDetails()).thenReturn(List.of(mock(com.checkmarx.ast.asca.ScanDetail.class)));

        Method method = AscaScannerService.class.getDeclaredMethod("handleScanResult", PsiFile.class, ScanResult.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, psiFile, scanResult));
    }

    @Test
    @DisplayName("handleScanResult: scanResult with no findings logs no-issues message")
    void handleScanResult_noFindings_logsNoIssues() throws Exception {
        ScanResult scanResult = mock(ScanResult.class);
        when(scanResult.getError()).thenReturn(null);
        when(scanResult.getScanDetails()).thenReturn(Collections.emptyList());

        Method method = AscaScannerService.class.getDeclaredMethod("handleScanResult", PsiFile.class, ScanResult.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, psiFile, scanResult));
    }

    @Test
    @DisplayName("getSecureTempDirectory returns path inside system temp")
    void getSecureTempDirectory_returnsPathInSystemTemp() throws Exception {
        Method method = AscaScannerService.class.getDeclaredMethod("getSecureTempDirectory");
        method.setAccessible(true);
        Path result = (Path) method.invoke(service);
        assertNotNull(result);
        assertTrue(result.toString().contains("CxASCA"));
    }

    @Test
    @DisplayName("saveTempFile creates file with expected content")
    void saveTempFile_validInput_createsFileAndReturnsPath() throws Exception {
        Method method = AscaScannerService.class.getDeclaredMethod("saveTempFile", String.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, "Test.java", "public class Test {}");
        assertNotNull(result);
        assertTrue(result.contains("Test.java"));
        // cleanup
        try { Files.deleteIfExists(Path.of(result)); } catch (IOException ignored) {}
    }

    @Test
    @DisplayName("deleteFile with null path is a no-op")
    void deleteFile_nullPath_isNoOp() throws Exception {
        Method method = AscaScannerService.class.getDeclaredMethod("deleteFile", String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, (String) null));
    }

    @Test
    @DisplayName("deleteFile with empty path is a no-op")
    void deleteFile_emptyPath_isNoOp() throws Exception {
        Method method = AscaScannerService.class.getDeclaredMethod("deleteFile", String.class);
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(service, "   "));
    }

    @Test
    @DisplayName("deleteFile with file in temp directory deletes successfully")
    void deleteFile_fileInTempDir_deletesFile() throws Exception {
        // First create a temp file via saveTempFile to get a valid path inside the temp dir
        Method saveTempFileMethod = AscaScannerService.class.getDeclaredMethod("saveTempFile", String.class, String.class);
        saveTempFileMethod.setAccessible(true);
        String filePath = (String) saveTempFileMethod.invoke(service, "ToDelete.java", "content");
        assertNotNull(filePath);
        assertTrue(Files.exists(Path.of(filePath)));

        Method deleteMethod = AscaScannerService.class.getDeclaredMethod("deleteFile", String.class);
        deleteMethod.setAccessible(true);
        deleteMethod.invoke(service, filePath);

        assertFalse(Files.exists(Path.of(filePath)));
    }

    @Test
    @DisplayName("scan returns null when CxWrapper throws exception")
    @SuppressWarnings("unchecked")
    void scan_cxWrapperThrows_returnsNull() throws Exception {
        AscaScannerService spyService = spy(new AscaScannerService());
        doReturn(true).when(spyService).shouldScanFile(anyString(), eq(psiFile));

        Project mockProject = mock(Project.class);
        when(psiFile.getProject()).thenReturn(mockProject);
        when(psiFile.getName()).thenReturn("Main.java");
        when(virtualFile.getPath()).thenReturn("/project/Main.java");
        when(virtualFile.isInLocalFileSystem()).thenReturn(true);

        try (MockedStatic<ApplicationManager> appMgr = mockStatic(ApplicationManager.class);
             MockedStatic<PsiDocumentManager> psiDocMgrStatic = mockStatic(PsiDocumentManager.class);
             MockedStatic<CxWrapperFactory> factoryMock = mockStatic(CxWrapperFactory.class);
             MockedStatic<DevAssistUtils> devUtilsMock = mockStatic(DevAssistUtils.class)) {

            Application mockApp = mock(Application.class);
            appMgr.when(ApplicationManager::getApplication).thenReturn(mockApp);

            Document mockDoc = mock(Document.class);
            when(mockDoc.getText()).thenReturn("public class Main {}");
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocMgrStatic.when(() -> PsiDocumentManager.getInstance(mockProject)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mockDoc);

            doAnswer(inv -> ((Computable<?>) inv.getArgument(0)).compute())
                    .when(mockApp).runReadAction(any(Computable.class));

            factoryMock.when(CxWrapperFactory::build).thenThrow(new RuntimeException("wrapper error"));
            devUtilsMock.when(() -> DevAssistUtils.getIgnoreFilePath(any())).thenReturn(null);

            assertNull(spyService.scan(psiFile, "/project/Main.java"));
        }
    }
}
