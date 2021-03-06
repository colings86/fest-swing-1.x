/*
 * Created on Nov 18, 2009
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * 
 * Copyright @2009-2013 the original author or authors.
 */
package org.fest.swing.fixture;

import static org.fest.swing.test.builder.JTabbedPanes.tabbedPane;

import javax.swing.JTabbedPane;

import org.fest.swing.driver.JTabbedPaneDriver;
import org.junit.BeforeClass;

/**
 * Test cases for {@link JTabbedPaneFixture}.
 * 
 * @author Yvonne Wang
 * @author Alex Ruiz
 */
public abstract class JTabbedPaneFixture_TestCase extends ComponentFixture_Implementations_TestCase<JTabbedPane> {
  private static JTabbedPane target;

  private JTabbedPaneDriver driver;
  private JTabbedPaneFixture fixture;

  @BeforeClass
  public static void setUpTarget() {
    target = tabbedPane().createNew();
  }

  @Override
  final void onSetUp() {
    driver = createMock(JTabbedPaneDriver.class);
    fixture = new JTabbedPaneFixture(robot(), target);
    fixture.driver(driver);
  }

  @Override
  final JTabbedPaneDriver driver() {
    return driver;
  }

  @Override
  final JTabbedPane target() {
    return target;
  }

  @Override
  final JTabbedPaneFixture fixture() {
    return fixture;
  }
}
