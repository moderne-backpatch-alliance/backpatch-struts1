/*
 * $Id$
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.struts.upload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.config.ModuleConfig;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.mock.MockHttpServletRequest;

public class TestCommonsMultipartRequestHandler extends TestCase {
    private static final String BOUNDARY = "acmeBoundary";

    private ModuleConfig moduleConfig;

    private ByteArrayOutputStream body;

    private CommonsMultipartRequestHandler handler;

    public TestCommonsMultipartRequestHandler(String name) {
        super(name);
    }

    protected void setUp() {
        moduleConfig = new ModuleConfigImpl("");
        moduleConfig.getControllerConfig().setMemFileSize("1M");
        body = new ByteArrayOutputStream();
    }

    public void testTextFieldOverConfiguredLimitIsSkippedAndFlagged()
        throws Exception {
        moduleConfig.getControllerConfig().setProperty("maxStringLength", "1K");
        addTextPart("over", 1025);
        addTextPart("under", 1024);
        addFilePart("upload", "acme.txt", 2048);

        MultipartRequestWrapper request = handle();

        assertEquals(Boolean.TRUE,
            request.getAttribute(MultipartRequestHandler.ATTRIBUTE_MAX_LENGTH_EXCEEDED));
        assertNull(request.getParameter("over"));
        assertFalse(handler.getTextElements().containsKey("over"));
        assertFalse(handler.getAllElements().containsKey("over"));
        assertEquals(1024, request.getParameter("under").length());
        assertEquals(2048,
            ((FormFile) handler.getFileElements().get("upload")).getFileSize());
    }

    public void testTextFieldsWithinConfiguredLimitAreNotFlagged()
        throws Exception {
        moduleConfig.getControllerConfig().setProperty("maxStringLength", "1K");
        addTextPart("first", 1024);
        addTextPart("second", 10);

        MultipartRequestWrapper request = handle();

        assertNull(request.getAttribute(MultipartRequestHandler.ATTRIBUTE_MAX_LENGTH_EXCEEDED));
        assertEquals(1024, request.getParameter("first").length());
        assertEquals(10, request.getParameter("second").length());
    }

    public void testDefaultLimitIs256K() throws Exception {
        addTextPart("over", 256 * 1024 + 1);
        addTextPart("under", 256 * 1024);

        MultipartRequestWrapper request = handle();

        assertEquals(Boolean.TRUE,
            request.getAttribute(MultipartRequestHandler.ATTRIBUTE_MAX_LENGTH_EXCEEDED));
        assertNull(request.getParameter("over"));
        assertEquals(256 * 1024, request.getParameter("under").length());
    }

    private MultipartRequestWrapper handle() throws Exception {
        write("--" + BOUNDARY + "--\r\n");

        MultipartRequestWrapper request =
            new MultipartRequestWrapper(new BodyRequest(body.toByteArray()));

        request.setAttribute(Globals.MODULE_KEY, moduleConfig);
        handler = new CommonsMultipartRequestHandler();
        handler.handleRequest(request);

        return request;
    }

    private void addTextPart(String name, int length) throws IOException {
        write("--" + BOUNDARY + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(repeat(length));
        write("\r\n");
    }

    private void addFilePart(String name, String fileName, int length)
        throws IOException {
        write("--" + BOUNDARY + "\r\n");
        write("Content-Disposition: form-data; name=\"" + name
            + "\"; filename=\"" + fileName + "\"\r\n");
        write("Content-Type: text/plain\r\n\r\n");
        write(repeat(length));
        write("\r\n");
    }

    private void write(String text) throws IOException {
        body.write(text.getBytes("ISO-8859-1"));
    }

    private static String repeat(int length) {
        StringBuffer buffer = new StringBuffer(length);

        for (int i = 0; i < length; i++) {
            buffer.append('a');
        }

        return buffer.toString();
    }

    private static class BodyRequest extends MockHttpServletRequest {
        private final byte[] content;

        BodyRequest(byte[] content) {
            this.content = content;
            setMethod("POST");
            setContentType("multipart/form-data; boundary=" + BOUNDARY);
        }

        public String getCharacterEncoding() {
            return null;
        }

        public int getContentLength() {
            return content.length;
        }

        public ServletInputStream getInputStream() {
            final InputStream in = new ByteArrayInputStream(content);

            return new ServletInputStream() {
                public int read() throws IOException {
                    return in.read();
                }
            };
        }
    }
}
