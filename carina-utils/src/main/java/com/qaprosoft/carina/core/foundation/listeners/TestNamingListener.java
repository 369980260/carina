/*******************************************************************************
 * Copyright 2013-2020 QaProSoft (http://www.qaprosoft.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation.listeners;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.IResultListener2;
import org.testng.internal.TestResult;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.utils.Configuration;

public class TestNamingListener implements IResultListener2 {
    private static final Logger LOGGER = Logger.getLogger(TestNamingListener.class);
    
    static final ThreadLocal<String> testName = new ThreadLocal<String>();
    private static final ConcurrentHashMap<String, Integer> testNameInvCounter = new ConcurrentHashMap<String, Integer>();

    @Override
    public void beforeConfiguration(ITestResult result) {
        LOGGER.debug("TestNamingListener->beforeConfiguration");
        setTestName(result);
    }

    @Override
    public void onConfigurationSuccess(ITestResult result) {
        LOGGER.debug("TestNamingListener->onConfigurationSuccess");
    }

    @Override
    public void onConfigurationSkip(ITestResult result) {
        LOGGER.debug("TestNamingListener->onConfigurationSkip");
    }

    @Override
    public void onConfigurationFailure(ITestResult result) {
        LOGGER.debug("TestNamingListener->onConfigurationFailure");
    }

    @Override
    public void onStart(ITestContext context) {
        LOGGER.debug("TestNamingListener->onStart(ITestContext context)");
    }

    @Override
    public void onTestStart(ITestResult result) {
        LOGGER.debug("TestNamingListener->onTestStart");

        // obligatory reset any registered canonical name because for ALREADY_PASSED methods we can't do this in
        // onTestSkipped method
        //TestNamingUtil.releaseTestInfoByThread();
        
        setTestName(result);

    }
    
    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.debug("TestNamingListener->onTestSuccess");
    }
    
    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.debug("TestNamingListener->");
    }
    
    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.debug("TestNamingListener->onTestFailure");
    }

    @Override
    public void onFinish(ITestContext context) {
        LOGGER.debug("TestNamingListener->onFinish");
    }
    
    /**
     * Get full test name based on test class, method and other generic information. It is generated by TestNameListener automatically.
     * 
     * @return String test name
     */    
    public static String getTestName() {
        //TODO: think about returning very simple vaid name if nothing was specified yet! Need ITestResult arg for that!
        if (testName.get() == null) {
            throw new RuntimeException("Unable to detect full test name yet!");
        }
        return testName.get();
    }
    
    /**
     * Get full test name based on test class, method and other generic information. It is generated by TestNameListener automatically.
     * 
     * @param result ITestResult
     * @return String test name
     */    
    public static String getTestName(ITestResult result) {
        if (testName.get() == null) {
            setTestName(result);
        }
        return testName.get();
    }
    
    /**
     * Set any custom full test name.
     * 
     * @param name String
     * @return String test name
     */ 
    public static String setTestName(String name) {
        LOGGER.warn("Overridden testName: " + name);
        testName.set(name);
        return testName.get();
    }

    
    /**
     * Set full test name based on test class, method and other generic information. It is generated based by ITestResult object.
     * 
     * @param ITestResult result
     * @return String test name
     */     
    @SuppressWarnings("unlikely-arg-type")
    private static String setTestName(ITestResult result) {
        String name = "";

        if (result.getTestContext() == null) {
            throw new RuntimeException("Unable to set Test name without testContext!");
        }
        @SuppressWarnings("unchecked")
        Map<Object[], String> testnameMap = (Map<Object[], String>) result.getTestContext().getAttribute(SpecialKeywords.TEST_NAME_ARGS_MAP);

        if (testnameMap != null) {
            String testHash = String.valueOf(Arrays.hashCode(result.getParameters()));
            if (testnameMap.containsKey(testHash)) {
                name = testnameMap.get(testHash);
            }
        }

        if (name.isEmpty()) {
            name = result.getTestContext().getCurrentXmlTest().getName();
        }

        // TODO: find the bext way to calculate TUID/hash
        if (result.getTestContext().getCurrentXmlTest().getAllParameters().containsKey(SpecialKeywords.EXCEL_DS_CUSTOM_PROVIDER) ||
                result.getTestContext().getCurrentXmlTest().getAllParameters().containsKey(SpecialKeywords.DS_CUSTOM_PROVIDER)) {
            // AUTO-274 "Pass"ing status set on emailable report when a test step fails
            String methodUID = "";
            for (int i = 0; i < result.getParameters().length; i++) {
                if (result.getParameters()[i] != null) {
                    if (result.getParameters()[i].toString().contains(SpecialKeywords.TUID + ":")) {
                        methodUID = result.getParameters()[i].toString().replace(SpecialKeywords.TUID + ":", "");
                        break; // first TUID: parameter is used
                    }
                }
            }
            if (!methodUID.isEmpty()) {
                name = methodUID + " - " + name;
            }
        }

        name = name + " - " + getMethodName(result);
        LOGGER.debug("testName: " + name);
        
        // introduce invocation count calculation here as in multi threading mode TestNG doesn't provide valid
        // getInvocationCount() value
        int index = ((TestResult) result).getParameterIndex();
        if (index > 0) {
            // that's a dataprovider line index
            index++; //to make correlation between line and index number
            LOGGER.debug("test: " + name  + "; index: " + index);
            name = name + String.format(SpecialKeywords.DAPAPROVIDER_INDEX, String.format("%04d", index));
        }
        
        int invCount = result.getTestContext().getAllTestMethods()[0].getInvocationCount();
        if (invCount > 1) {
            LOGGER.debug("Detected method '" + result.getMethod().getMethodName() + "' with non zero invocationCount: " + invCount);
            int countIndex = getCurrentInvocationCount(name);
            LOGGER.debug("test: " + name  + "; InvCount index: " + countIndex);
            name = name + String.format(SpecialKeywords.INVOCATION_COUNTER, String.format("%04d", countIndex));
        }   
        
        testName.set(name);
        return testName.get();
    }
    
    /**
     * get Test Method name
     * 
     * @param result ITestResult
     * @return String method name
     */
    public static String getMethodName(ITestResult result) {
        // adjust testName using pattern
        ITestNGMethod m = result.getMethod();
        String name = Configuration.get(Configuration.Parameter.TEST_NAMING_PATTERN);
        name = name.replace(SpecialKeywords.METHOD_NAME, m.getMethodName());
        name = name.replace(SpecialKeywords.METHOD_PRIORITY, String.valueOf(m.getPriority()));
        name = name.replace(SpecialKeywords.METHOD_THREAD_POOL_SIZE, String.valueOf(m.getThreadPoolSize()));

        if (m.getDescription() != null) {
            name = name.replace(SpecialKeywords.METHOD_DESCRIPTION, m.getDescription());
        } else {
            name = name.replace(SpecialKeywords.METHOD_DESCRIPTION, "");
        }

        return name;
    }
    
    /**
     * get Test Package name
     * 
     * @param result ITestResult
     * @return String package name
     */
    public static String getPackageName(ITestResult result) {
        return result.getMethod().getRealClass().getPackage().getName();
    }
    
    /**
     * get InvocationCount number based on test name
     * 
     * @param String test
     * @return int invCount
     */
    private static int getCurrentInvocationCount(String test) {
        /*TODO: reopen https://github.com/cbeust/testng/issues/1758 bug 
         * Explain that appropriate TestNG functionality doesn't work in multi-threading env 
         */
        
        int invCount = 1;
        if (!testNameInvCounter.containsKey(test)) {
            testNameInvCounter.put(test, invCount);
        } else {
            invCount = testNameInvCounter.get(test) + 1;
            testNameInvCounter.put(test, invCount);
        }
        
        return invCount;
    }
    
}
