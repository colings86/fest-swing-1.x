/*
 * Created on May 23, 2009
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
package org.fest.swing.driver;

import org.junit.Test;

/**
 * Tests for {@link JTableStopCellEditingTask#stopEditing(javax.swing.table.TableCellEditor)}.
 * 
 * @author Alex Ruiz
 */
public class JTableStopCellEditingTask_stopEditing_withEditor_Test {
  @Test
  public void shouldNotThrowErrorIfCellEditorIsNull() {
    JTableStopCellEditingTask.stopEditing(null);
  }
}
