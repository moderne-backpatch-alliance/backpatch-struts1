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

package org.apache.struts.util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.validator.Field;
import org.apache.commons.validator.ValidatorResults;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.RequestUtils;
import org.apache.struts.Globals;
import org.apache.struts.mock.TestMockBase;
import org.apache.struts.mock.MockFormBean;
import org.apache.struts.mock.MockMultipartRequestHandler;
import org.apache.struts.upload.MultipartRequestHandler;
import org.apache.struts.validator.ValidatorForm;

/**
 * Unit tests for the RequestUtil's <code>populate</code> method.
 *
 * @version $Rev$
 */
public class TestRequestUtilsPopulate extends TestMockBase {

    /**
     * Defines the testcase name for JUnit.
     *
     * @param theName the testcase's name.
     */
    public TestRequestUtilsPopulate(String theName) {
        super(theName);
    }

    /**
     * Start the tests.
     *
     * @param theArgs the arguments. Not used
     */
    public static void main(String[] theArgs) {
        junit.awtui.TestRunner.main(
            new String[] { TestRequestUtilsPopulate.class.getName()});
    }

    /**
     * @return a test suite (<code>TestSuite</code>) that includes all methods
     *         starting with "test"
     */
    public static Test suite() {
        // All methods starting with "test" will be executed in the test suite.
        return new TestSuite(TestRequestUtilsPopulate.class);
    }

    public void setUp() {
        super.setUp();
    }

    public void tearDown() {
        super.tearDown();
    }

    /**
     * Ensure that the getMultipartRequestHandler cannot be seen in
     * a subclass of ActionForm.
     *
     * The purpose of this test is to ensure that Bug #38534 is fixed.
     *
     */
    public void testMultipartVisibility() throws Exception {

        String mockMappingName = "mockMapping";
        String stringValue     = "Test";

        MockFormBean  mockForm = new MockFormBean();
        ActionMapping mapping  = new ActionMapping();
        mapping.setName(mockMappingName);

        // Set up the mock HttpServletRequest
        request.setMethod("POST");
        request.setContentType("multipart/form-data");
        request.setAttribute(Globals.MULTIPART_KEY, MockMultipartRequestHandler.class.getName());
        request.setAttribute(Globals.MAPPING_KEY, mapping);

        request.addParameter("stringProperty", stringValue);
        request.addParameter("multipartRequestHandler.mapping.name", "Bad");

        // Check the Mapping/ActionForm before
        assertNull("Multipart Handler already set",    mockForm.getMultipartRequestHandler());
        assertEquals("Mapping name not set correctly", mockMappingName, mapping.getName());

        // Try to populate
        try {
            RequestUtils.populate(mockForm, request);
        } catch(ServletException se) {
            // Expected BeanUtils.populate() to throw a NestedNullException
            // which gets wrapped in RequestUtils in a ServletException
            assertEquals("Unexpected type of Exception thrown", "BeanUtils.populate", se.getMessage());
        }

        // Check the Mapping/ActionForm after
        assertNotNull("Multipart Handler Missing", mockForm.getMultipartRequestHandler());
        assertEquals("Mapping name has been modified", mockMappingName, mapping.getName());

    }

    /**
     * Parameters whose name has a "class" segment (CVE-2014-0114) never
     * reach the bean, while other nested names still do.
     */
    public void testClassSegmentIsNotPopulated() throws Exception {
        NestedForm form = new NestedForm();

        request.setMethod("GET");
        request.addParameter("values.class", "Bad");
        request.addParameter("values.CLASS", "Bad");
        request.addParameter("items[0].class", "Bad");
        request.addParameter("values.key", "Good");

        RequestUtils.populate(form, request);

        assertNull("values.class populated", form.getValues().get("class"));
        assertNull("values.CLASS populated", form.getValues().get("CLASS"));
        assertNull("items[0].class populated", form.getItems()[0].get("class"));
        assertNotNull("values.key not populated", form.getValues().get("key"));
    }

    /**
     * A "multipartRequestHandler" segment (CVE-2016-1181) never reaches the
     * handler a concurrent request has set on a session-scoped form.
     */
    public void testMultipartRequestHandlerSegmentIsNotPopulated()
        throws Exception {
        ActionMapping mapping = new ActionMapping();
        mapping.setName("mockMapping");

        MockMultipartRequestHandler otherHandler =
            new MockMultipartRequestHandler();
        otherHandler.setMapping(mapping);

        SharedHandlerForm form = new SharedHandlerForm(otherHandler);

        request.setMethod("GET");
        request.addParameter("multipartRequestHandler.mapping.name", "Bad");
        request.addParameter("stringProperty", "Good");

        RequestUtils.populate(form, request);

        assertEquals("Mapping name has been modified", "mockMapping",
            mapping.getName());
        assertEquals("stringProperty not populated", "Good",
            form.getStringProperty());
    }

    /**
     * A "validatorResults" segment (CVE-2016-1182) never reaches the
     * validation results or the validator configuration they point to.
     */
    public void testValidatorResultsSegmentIsNotPopulated() throws Exception {
        Field field = new Field();
        field.setProperty("name");
        field.setKey("name");

        ValidatorForm form = validatedForm(field, new MockFormBean("Good"));

        request.setMethod("GET");
        request.addParameter("validatorResults.validatorResult(name).field.page",
            "5");
        request.addParameter("page", "1");

        RequestUtils.populate(form, request);

        assertEquals("Field page has been modified", 0, field.getPage());
        assertEquals("page not populated", 1, form.getPage());
    }

    /**
     * A "resultValueMap" segment (CVE-2016-1182) never reaches the values
     * the validators returned.
     */
    public void testResultValueMapSegmentIsNotPopulated() throws Exception {
        Field field = new Field();
        field.setProperty("name");
        field.setKey("name");

        MockFormBean value = new MockFormBean("Good");
        ValidatorForm form = validatedForm(field, value);

        request.setMethod("GET");
        request.addParameter("resultValueMap.name.stringProperty", "Bad");
        request.addParameter("page", "1");

        RequestUtils.populate(form, request);

        assertEquals("Result value has been modified", "Good",
            value.getStringProperty());
        assertEquals("page not populated", 1, form.getPage());
    }

    private ValidatorForm validatedForm(Field field, Object resultValue) {
        ValidatorResults results = new ValidatorResults();
        results.add(field, "required", true, resultValue);

        ValidatorForm form = new ValidatorForm();
        form.setValidatorResults(results);

        return form;
    }

    public static class SharedHandlerForm extends MockFormBean {
        private final MultipartRequestHandler sharedHandler;

        public SharedHandlerForm(MultipartRequestHandler sharedHandler) {
            this.sharedHandler = sharedHandler;
        }

        public MultipartRequestHandler getMultipartRequestHandler() {
            return sharedHandler;
        }
    }

    public static class NestedForm {
        private final Map values = new HashMap();
        private final Map[] items = new Map[] { new HashMap() };

        public Map getValues() {
            return values;
        }

        public Map[] getItems() {
            return items;
        }
    }

}
