package com.wolfox.gps.util;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.Assert.*;

public class UiAuditTextTemplatesTest {
    @Test public void utf8ArabicTemplatesPreserveSymbolsAndNewLines() throws Exception {
        byte[] bytes = "logging_enable=تفعيل الـ Log\nsearch_error=×  %s\n".getBytes(StandardCharsets.UTF_8);
        Properties values = WFUiText.readTemplates(new ByteArrayInputStream(bytes));
        assertEquals("تفعيل الـ Log", values.getProperty("logging_enable"));
        assertEquals("×  %s", values.getProperty("search_error"));
    }

    @Test public void errorAndSuccessFormattingRetainsSelectedLocation() {
        assertEquals("×  خطأ", WFUiText.format(null, "search_error", "خطأ"));
        String success = WFUiText.format(null, "search_success", "إحداثيات", "\nالرياض", "24.713600, 46.675300");
        assertEquals("✓  إحداثيات\nالرياض\n24.713600, 46.675300", success);
        assertEquals("تفعيل الـ Log", WFUiText.text(null, "logging_enable"));
    }

    @Test public void textTemplateInputHasAHardSizeLimit() throws Exception {
        try {
            WFUiText.readTemplates(new ByteArrayInputStream(new byte[8193]));
            fail("Unbounded template input must be refused");
        } catch (java.io.IOException expected) { }
    }
}
