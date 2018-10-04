/**
 * Copyright (C) 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ninja.build;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static ninja.maven.NinjaMavenPluginConstants.DEFAULT_EXCLUDE_PATTERNS;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.diffblue.deeptestutils.Reflector;

import ninja.build.WatchAndRestartMachine.RuleMatch;
import ninja.build.WatchAndRestartMachine.RuleType;
import ninja.build.WatchAndRestartMachine;

@RunWith(MockitoJUnitRunner.class)
public class WatchAndRestartMachineTest {
    
    @Test
    public void run() throws Exception {
        
        File watchDir = new File("target/fake-watch-dir");
        watchDir.mkdirs();
        
        File assetsDir = new File(watchDir, "assets");
        assetsDir.mkdirs();
        
        // make sure file does not yet exist
        File newTxtFile = new File(watchDir, "test.txt");
        newTxtFile.delete();
        
        // file that would be excluded in assets directory, but we'll include a rule to include it
        File newIncludedTxtFile = new File(assetsDir, "included.txt");
        newIncludedTxtFile.delete();
        
        // make sure file does not yet exist
        File newPngFile = new File(assetsDir, "test.png");
        newPngFile.delete();
        
        DelayedRestartTrigger restartTrigger = mock(DelayedRestartTrigger.class);
        
        final WatchAndRestartMachine watcher = new WatchAndRestartMachine(
            watchDir.toPath(),
            new HashSet<>(Arrays.asList("(.*)included.txt")),
            new HashSet<>(Arrays.asList(DEFAULT_EXCLUDE_PATTERNS)),
            restartTrigger
        );
        
        Thread watcherThread = new Thread(watcher);
        watcherThread.start();
        
        try {  
            // add a new file
            OutputStream os = new FileOutputStream(newTxtFile);
            os.close();

            // trigger should have been called soon
            verify(restartTrigger, timeout(10000)).trigger();
            
            Mockito.reset(restartTrigger);
            
            // modify the file
            os = new FileOutputStream(newTxtFile);
            os.write("Hello!".getBytes());
            os.close();

            // windows may mod a file more than once
            verify(restartTrigger, timeout(10000).atLeast(1)).trigger();
            verify(restartTrigger, atMost(2)).trigger();

            Mockito.reset(restartTrigger);
            
            // use an excluded file and verify restart not called
            
            // add a new file
            os = new FileOutputStream(newPngFile);
            os.close();

            // indicates exclusion rule for new files didn't work
            verify(restartTrigger, Mockito.after(10000).never()).trigger();
            
            // modify the file
            os = new FileOutputStream(newPngFile);
            os.write("Hello!".getBytes());
            os.close();

            // indicates exclusion rule for modified files didn't work
            verify(restartTrigger, Mockito.after(10000).never()).trigger();
            
            // add a new file
            os = new FileOutputStream(newIncludedTxtFile);
            os.close();

            // indicates exclusion rule for new files didn't work
            // windows may do 2 mods so we need to check range
            verify(restartTrigger, timeout(10000).atLeast(1)).trigger();
            verify(restartTrigger, atMost(2)).trigger();
            
        } finally {
            watcher.shutdown();
            watcherThread.interrupt();
        }
    }
    
    
    /**
     * Test of checkIfMatchesPattern method, of class WatchAndRestartMachine.
     */
    @Test
    public void testCheckIfMatchesPatternAndAssetsAreIgnored() {
        
        Set<String> patterns 
                = new HashSet<>(Arrays.asList(DEFAULT_EXCLUDE_PATTERNS));
        
        assertThat(
                WatchAndRestartMachine.checkIfWouldBeExcluded(
                        patterns, 
                        "target" 
                        + File.separator 
                        + "classes" 
                        + File.separator 
                        + "assets" 
                        + File.separator 
                        + "js" 
                        + File.separator 
                        + "script.js"),
                is(true));
        
        assertThat(
                WatchAndRestartMachine.checkIfWouldBeExcluded(
                        patterns, 
                        File.separator 
                        + "assets" 
                        + File.separator),
                is(true));
        
    }
    
    
    @Test
    public void testCheckIfMatchesPatternMachtesOfOtherStuff() {
        
        Set<String> patterns 
                = new HashSet<>(Arrays.asList(DEFAULT_EXCLUDE_PATTERNS));
        
        assertThat(
                WatchAndRestartMachine.checkIfWouldBeExcluded(
                        patterns, 
                        "target" 
                        + File.separator 
                        + "classes" 
                        + File.separator 
                        + "completelyDifferentPath"),
                is(false));

        
    }
    
    
    @Test
    public void testCheckIfMatchesPatternAndFtlHtmlFilesAreIgnored() {

        Set<String> patterns 
                = new HashSet<>(Arrays.asList(DEFAULT_EXCLUDE_PATTERNS));
        
        assertThat(
                WatchAndRestartMachine.checkIfWouldBeExcluded(
                        patterns, 
                        "target" 
                        + File.separator 
                        + "classes" 
                        + File.separator 
                        + "views" 
                        + File.separator 
                        + "ApplicationController" 
                        + File.separator 
                        + "index.ftl.html"),
                is(true));
        
        assertThat(
                WatchAndRestartMachine.checkIfWouldBeExcluded(
                        patterns, 
                        "target" 
                        + File.separator 
                        + "classes" 
                        + File.separator 
                        + "views" 
                        + File.separator 
                        + "ApplicationController" 
                        + File.separator 
                        + "index.ftl.html.bam"),
                is(false));
 
        
    }

        @Rule public ExpectedException thrown = ExpectedException.none();
    /* testedClasses: WatchAndRestartMachine */
    /*
     * Test generated by Diffblue Deeptest.
     * This test case covers:
     *  - conditional line 273 branch to line 273
     */
    @Test
    public void checkIfWouldBeExcludedInput0NotNullOutputFalse() {
      // Arrange
      final java.util.HashSet<String> patterns = new java.util.HashSet<String>();
      final String string = "";
      // Act
      final boolean retval = WatchAndRestartMachine.checkIfWouldBeExcluded(patterns, string);
      // Assert result
      Assert.assertEquals(false, retval);
    }
    /*
     * Test generated by Diffblue Deeptest.
     * This test case covers the entire method.
     */
    @Test
    public void constructorInputNotNullNotNullFalseOutputVoid() {
      // Arrange
      final RuleType type = RuleType.exclude;
      final String pattern = "";
      final boolean proceed = false;
      // Act, creating object to test constructor
      RuleMatch objectUnderTest = new RuleMatch(type, pattern, proceed);
      // Method returns void, testing that no exception is thrown
    }
    /*
     * Test generated by Diffblue Deeptest.
     * This test case covers:
     *  - conditional line 252 branch to line 253
     *  - conditional line 253 branch to line 253
     *  - conditional line 253 branch to line
     *  - conditional line 253 branch to line 260
     *  - conditional line 260 branch to line 261
     *  - conditional line 261 branch to line 261
     *  - conditional line 261 branch to line
     *  - conditional line 261 branch to line 268
     */
    @Test
    public void matchRuleInput00NotNullOutputNotNull() {
      // Arrange
      final java.util.HashSet<String> includes = new java.util.HashSet<String>();
      final java.util.HashSet<String> excludes = new java.util.HashSet<String>();
      final String string = "";
      // Act
      final RuleMatch retval = WatchAndRestartMachine.matchRule(includes, excludes, string);
      // Assert result
      Assert.assertNotNull(retval);
      Assert.assertEquals("", retval.pattern);
      Assert.assertEquals(RuleType.none, retval.type);
      Assert.assertEquals(true, retval.proceed);
    }
    
}
