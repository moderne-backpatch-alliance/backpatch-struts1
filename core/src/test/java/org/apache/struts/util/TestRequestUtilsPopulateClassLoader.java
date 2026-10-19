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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.struts.mock.TestMockBase;

/**
 * CVE-2014-0114 on a real bean: "class.classLoader.*" request parameters
 * must not reach the class loader of the bean being populated.
 */
public class TestRequestUtilsPopulateClassLoader extends TestMockBase {
    public TestRequestUtilsPopulateClassLoader(String name) {
        super(name);
    }

    // Without its own suite(), JUnit 3 runs TestMockBase's inherited one.
    public static Test suite() {
        return new TestSuite(TestRequestUtilsPopulateClassLoader.class);
    }

    public void testClassLoaderOfPopulatedBeanIsNotReached()
        throws Exception {
        RecordingClassLoader loader =
            new RecordingClassLoader(getClass().getClassLoader());
        Object bean = loader.loadClass(ProbeBean.class.getName()).newInstance();

        assertSame(loader, bean.getClass().getClassLoader());

        request.setMethod("POST");
        request.addParameter("class.classLoader.probe", "reached");
        request.addParameter("name", "acme");

        RequestUtils.populate(bean, request);

        assertNull("class.classLoader.probe reached the class loader",
            loader.getProbe());
        assertEquals("acme", PropertyUtils.getProperty(bean, "name"));
    }

    public static class ProbeBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // Defines ProbeBean itself, so the bean's getClass().getClassLoader()
    // is this loader and a setter call on it can be observed.
    public static class RecordingClassLoader extends ClassLoader {
        private String probe;

        public RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public String getProbe() {
            return probe;
        }

        public void setProbe(String probe) {
            this.probe = probe;
        }

        protected synchronized Class loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
            if (!ProbeBean.class.getName().equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class c = findLoadedClass(name);

            if (c == null) {
                byte[] bytes = readClass(name);

                c = defineClass(name, bytes, 0, bytes.length);
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }

        private byte[] readClass(String name) throws ClassNotFoundException {
            InputStream in =
                getParent().getResourceAsStream(name.replace('.', '/')
                    + ".class");

            if (in == null) {
                throw new ClassNotFoundException(name);
            }

            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;

                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }

                return out.toByteArray();
            } catch (IOException e) {
                throw new ClassNotFoundException(name);
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // nothing to do
                }
            }
        }
    }
}
