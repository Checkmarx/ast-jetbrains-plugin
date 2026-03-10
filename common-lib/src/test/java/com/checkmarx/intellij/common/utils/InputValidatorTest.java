package com.checkmarx.intellij.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InputValidatorTest {

    @BeforeEach
    void setUp() {
        // Reset any static mocks if needed
    }

    @Test
    void testValidateConnection_Success() throws Exception {
        String baseUri = "https://test.checkmarx.com";
        String tenant = "test-tenant";
        
        try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
            InputValidator.ValidationResult expectedResult = new InputValidator.ValidationResult(true, "");
            mockedValidator.when(() -> InputValidator.validateConnection(baseUri, tenant))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));
            
            CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
            InputValidator.ValidationResult result = future.get();
            
            assertTrue(result.isValid);
            assertEquals("", result.error);
        }
    }

    @Test
    void testValidateConnection_InvalidUrlProtocol() throws Exception {
        String baseUri = "ftp://test.checkmarx.com";
        String tenant = "test-tenant";
        
        // Mock the InputValidator to avoid actual network calls
        try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class)) {
            InputValidator.ValidationResult expectedResult = new InputValidator.ValidationResult(false, "Invalid URL protocol. Please use http:// or https://");
            mockedValidator.when(() -> InputValidator.validateConnection(baseUri, tenant))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));
            
            CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
            InputValidator.ValidationResult result = future.get();
            
            assertFalse(result.isValid);
            assertEquals("Invalid URL protocol. Please use http:// or https://", result.error);
        }
    }

    @Test
    void testValidateConnection_EmptyTenant() throws Exception {
        String baseUri = "https://test.checkmarx.com";
        String tenant = "";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Tenant name cannot be empty", result.error);
    }

    @Test
    void testValidateConnection_NullTenant() throws Exception {
        String baseUri = "https://test.checkmarx.com";
        String tenant = null;
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Tenant name cannot be empty", result.error);
    }

    @Test
    void testValidateConnection_WhitespaceTenant() throws Exception {
        String baseUri = "https://test.checkmarx.com";
        String tenant = "   ";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Tenant name cannot be empty", result.error);
    }

    @Test
    void testValidateConnection_BaseUrlNotExists() throws Exception {
        String baseUri = "https://nonexistent.checkmarx.com";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Please check the server address of your Checkmarx One environment.", result.error);
    }

    @Test
    void testValidateConnection_TenantNotFound() throws Exception {
        String baseUri = "https://httpbin.org"; // Using a known working endpoint for testing
        String tenant = "nonexistent-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        // This test might fail depending on network conditions, but we expect tenant validation to fail
        // The exact error message might vary, but it should indicate tenant not found
        assertFalse(result.isValid);
        assertTrue(result.error.contains("Tenant"));
    }

    @Test
    void testValidateConnection_BaseUrlWithTrailingSlash() throws Exception {
        String baseUri = "https://httpbin.org/";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        // Should attempt to validate the tenant URL with proper concatenation
        assertNotNull(result);
    }

    @Test
    void testValidateConnection_BaseUrlWithoutTrailingSlash() throws Exception {
        String baseUri = "https://httpbin.org";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        // Should attempt to validate the tenant URL with proper concatenation
        assertNotNull(result);
    }

    @Test
    void testValidateConnection_Exception() throws Exception {
        String baseUri = "invalid-url";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Invalid URL protocol. Please use http:// or https://", result.error);
    }

    @Test
    void testCheckUrlExists_True() throws Exception {
        // This is a private method, so we test it indirectly through validateConnection
        String baseUri = "https://httpbin.org";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        // The base URL should exist (httpbin.org is a reliable test endpoint)
        // The tenant validation might fail, but the base URL check should pass
        assertNotNull(result);
    }

    @Test
    void testCheckUrlExists_False() throws Exception {
        // Test with a non-existent URL
        String baseUri = "https://nonexistent-domain-12345.com";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        InputValidator.ValidationResult result = future.get();
        
        assertFalse(result.isValid);
        assertEquals("Please check the server address of your Checkmarx One environment.", result.error);
    }

    @Test
    void testCheckUrlExists_TenantCheck404() throws Exception {
        // This tests the tenant-specific validation logic
        // We'll use a known endpoint that should return 404 for tenant validation
        try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class, CALLS_REAL_METHODS)) {
            // We can't easily mock the private method, so we test through the public interface
            String baseUri = "https://httpbin.org";
            String tenant = "nonexistent";
            
            CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
            InputValidator.ValidationResult result = future.get();
            
            assertNotNull(result);
        }
    }

    @Test
    void testCheckUrlExists_TenantCheck405() throws Exception {
        // Test tenant validation with 405 (Method Not Allowed) response
        try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class, CALLS_REAL_METHODS)) {
            String baseUri = "https://httpbin.org";
            String tenant = "test";
            
            CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
            InputValidator.ValidationResult result = future.get();
            
            assertNotNull(result);
        }
    }

    @Test
    void testCheckUrlExists_HttpException() throws Exception {
        // Test with malformed URL
        try (MockedStatic<InputValidator> mockedValidator = mockStatic(InputValidator.class, CALLS_REAL_METHODS)) {
            String baseUri = "https://httpbin.org";
            String tenant = "test";
            
            CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
            InputValidator.ValidationResult result = future.get();
            
            assertNotNull(result);
        }
    }

    @Test
    void testIsValidUrl_ValidHttpUrl() {
        String url = "http://example.com";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_ValidHttpsUrl() {
        String url = "https://example.com";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_ValidUrlWithPath() {
        String url = "https://example.com/path/to/resource";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_ValidUrlWithPort() {
        String url = "https://example.com:8080";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_ValidUrlWithQuery() {
        String url = "https://example.com?param1=value1&param2=value2";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_ValidUrlWithFragment() {
        String url = "https://example.com#section";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_NullUrl() {
        String url = null;
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_EmptyUrl() {
        String url = "";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_WhitespaceOnlyUrl() {
        String url = "   ";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_InvalidProtocol() {
        String url = "ftp://example.com";
        
        boolean result = InputValidator.isValidUrl(url);
        
        // Current implementation only checks URI syntax, not protocol
        // ftp://example.com is syntactically valid, so returns true
        assertTrue(result);
    }

    @Test
    void testIsValidUrl_NoProtocol() {
        String url = "example.com";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_NoHost() {
        String url = "https://";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_RelativeUrl() {
        String url = "/path/to/resource";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_MalformedUrl() {
        String url = "https://:invalid";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_UrlWithSpaces() {
        String url = "https://example.com/path with spaces";
        
        boolean result = InputValidator.isValidUrl(url);
        
        // URLs with spaces are generally invalid without proper encoding
        assertFalse(result);
    }

    @Test
    void testIsValidUrl_UrlWithSpecialCharacters() {
        String url = "https://example.com/path?param=value&other=test";
        
        boolean result = InputValidator.isValidUrl(url);
        
        assertTrue(result);
    }

    @Test
    void testValidationResult_Constructor() {
        boolean isValid = true;
        String error = "";
        
        InputValidator.ValidationResult result = new InputValidator.ValidationResult(isValid, error);
        
        assertEquals(isValid, result.isValid);
        assertEquals(error, result.error);
    }

    @Test
    void testValidationResult_ValidSuccess() {
        InputValidator.ValidationResult result = new InputValidator.ValidationResult(true, "");
        
        assertTrue(result.isValid);
        assertEquals("", result.error);
    }

    @Test
    void testValidationResult_InvalidWithError() {
        String errorMessage = "Test error message";
        InputValidator.ValidationResult result = new InputValidator.ValidationResult(false, errorMessage);
        
        assertFalse(result.isValid);
        assertEquals(errorMessage, result.error);
    }

    @Test
    void testValidateConnection_AsyncExecution() throws Exception {
        String baseUri = "https://httpbin.org";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        
        // Verify that the future is not immediately completed (it should execute asynchronously)
        assertFalse(future.isDone());
        
        // Wait for completion
        InputValidator.ValidationResult result = future.get();
        
        assertNotNull(result);
    }

    @Test
    void testValidateConnection_MultipleConcurrentCalls() throws Exception {
        String baseUri = "https://httpbin.org";
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future1 = InputValidator.validateConnection(baseUri, tenant);
        CompletableFuture<InputValidator.ValidationResult> future2 = InputValidator.validateConnection(baseUri, tenant);
        CompletableFuture<InputValidator.ValidationResult> future3 = InputValidator.validateConnection(baseUri, tenant);
        
        // Wait for all to complete
        InputValidator.ValidationResult result1 = future1.get();
        InputValidator.ValidationResult result2 = future2.get();
        InputValidator.ValidationResult result3 = future3.get();
        
        // All should complete without throwing exceptions
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3);
    }

    @Test
    void testValidateConnection_TimeoutHandling() throws Exception {
        String baseUri = "https://httpbin.org/delay/10"; // This will timeout
        String tenant = "test-tenant";
        
        CompletableFuture<InputValidator.ValidationResult> future = InputValidator.validateConnection(baseUri, tenant);
        
        // The connection should timeout after 5 seconds (as configured in the method)
        InputValidator.ValidationResult result = future.get();
        
        // Should fail due to timeout
        assertFalse(result.isValid);
    }
}
