/*
 * Created on Jul 30, 2009
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
package org.fest.swing.util;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.util.OSIdentifierStub.solaris;
import static org.fest.swing.util.OSIdentifierStub.windowsXP;

import org.junit.Test;

/**
 * Tests for {@link Platform#isSolaris()}.
 * 
 * @author Alex Ruiz
 */
public class Platform_isSolaris_Test extends Platform_TestCase {
  @Test
  public void should_return_true_if_OS_is_Solaris() {
    Platform.initialize(solaris(), toolkitProvider);
    assertThat(Platform.isSolaris()).isTrue();
  }

  @Test
  public void should_return_false_if_OS_is_not_Solaris() {
    Platform.initialize(windowsXP(), toolkitProvider);
    assertThat(Platform.isSolaris()).isFalse();
  }
}
