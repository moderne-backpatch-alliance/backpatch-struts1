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
package org.apache.struts.validator;

import javax.servlet.ServletContext;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.validator.Field;
import org.apache.commons.validator.Form;
import org.apache.commons.validator.FormSet;
import org.apache.commons.validator.ValidatorAction;
import org.apache.commons.validator.ValidatorResources;
import org.apache.commons.validator.ValidatorResults;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.apache.struts.mock.TestMockBase;

/**
 * Unit tests for the page a multi-page validation uses (CVE-2015-0899).
 *
 * @version $Rev$
 */
public class TestValidatorFormPage extends TestMockBase {

    private ActionMapping mapping;

    private ActionServlet servlet;

    public TestValidatorFormPage(String theName) {
        super(theName);
    }

    public static Test suite() {
        return new TestSuite(TestValidatorFormPage.class);
    }

    public void setUp() {
        super.setUp();

        ValidatorAction action = new ValidatorAction();
        action.setName("accept");
        action.setClassname(TestValidatorFormPage.class.getName());
        action.setMethod("validateAccept");
        action.setMethodParams(
            "java.lang.Object,org.apache.commons.validator.Field");

        Form form = new Form();
        form.setName("pageForm");
        form.addField(field("first", 0));
        form.addField(field("second", 1));

        FormSet formSet = new FormSet();
        formSet.addForm(form);

        ValidatorResources resources = new ValidatorResources();
        resources.addValidatorAction(action);
        resources.addFormSet(formSet);
        resources.process();

        context.setAttribute(ValidatorPlugIn.VALIDATOR_KEY, resources);

        mapping = new ActionMapping();
        mapping.setPath("/page");
        mapping.setName("pageForm");

        servlet = new ActionServlet() {
            public ServletContext getServletContext() {
                return context;
            }
        };
    }

    public static boolean validateAccept(Object bean, Field field) {
        return true;
    }

    public void testNegativePageStillValidatesPageZeroFields() {
        assertValidated(validate(-1), true, false);
    }

    public void testNegativePageStillValidatesPageZeroFieldsOfDynaForm() {
        DynaValidatorForm form = new DynaValidatorForm();
        form.setServlet(servlet);
        form.setPage(-1);
        form.validate(mapping, request);

        assertValidated(form.getValidatorResults(), true, false);
    }

    public void testPageSelectsFieldsWithoutAcceptPage() {
        assertValidated(validate(0), true, false);
        assertValidated(validate(1), true, true);
    }

    public void testAcceptPageRaisesLowerPage() {
        mapping.setProperty("acceptPage", "1");

        assertValidated(validate(0), true, true);
        assertValidated(validate(-1), true, true);
    }

    public void testAcceptPageKeepsHigherPage() {
        mapping.setProperty("acceptPage", "0");

        assertValidated(validate(0), true, false);
        assertValidated(validate(1), true, true);
    }

    public void testInvalidAcceptPageValidatesEveryPage() {
        mapping.setProperty("acceptPage", "first");

        assertValidated(validate(0), true, true);
    }

    private ValidatorResults validate(int page) {
        ValidatorForm form = new ValidatorForm();
        form.setServlet(servlet);
        form.setPage(page);
        form.validate(mapping, request);

        return form.getValidatorResults();
    }

    private void assertValidated(ValidatorResults results, boolean first,
        boolean second) {
        assertNotNull("no validation ran", results);
        assertEquals("first validated", first,
            results.getValidatorResult("first") != null);
        assertEquals("second validated", second,
            results.getValidatorResult("second") != null);
    }

    private static Field field(String property, int page) {
        Field field = new Field();
        field.setProperty(property);
        field.setKey(property);
        field.setDepends("accept");
        field.setPage(page);

        return field;
    }
}
