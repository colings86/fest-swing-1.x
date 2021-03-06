/*
 * Created on Jun 24, 2008
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
 * Copyright @2008-2013 the original author or authors.
 */
package org.fest.swing.input;

import static org.fest.swing.test.awt.TestAWTEvents.singletonAWTEventMock;
import static org.fest.swing.test.awt.Toolkits.newToolkitMock;

import java.awt.AWTEvent;
import java.awt.event.AWTEventListener;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link EventNormalizer#eventDispatched(AWTEvent)}.
 * 
 * @author Alex Ruiz
 */
public class EventNormalizer_eventDispatched_Test extends EventNormalizer_TestCase {
  private DisposedWindowMonitor disposedWindowMonitor;
  private AWTEventListener delegateEventListener;
  private AWTEvent event;
  private EventNormalizer eventNormalizer;

  @Before
  public void setUp() {
    disposedWindowMonitor = createMock(DisposedWindowMonitor.class);
    delegateEventListener = delegateEventListenerMock();
    event = singletonAWTEventMock();
    eventNormalizer = new EventNormalizer(disposedWindowMonitor);
    eventNormalizer.startListening(newToolkitMock(), delegateEventListener, 8);
  }

  @Test
  public void should_delegate_event_if_it_is_not_a_duplicate_dispose() {
    new EasyMockTemplate(disposedWindowMonitor, delegateEventListener) {
      @Override
      protected void expectations() {
        expect(disposedWindowMonitor.isDuplicateDispose(event)).andReturn(false);
        delegateEventListener.eventDispatched(event);
        expectLastCall().once();
      }

      @Override
      protected void codeToTest() {
        eventNormalizer.eventDispatched(event);
      }
    }.run();
  }

  @Test
  public void should_not_delegate_event_if_it_is_a_duplicate_dispose() {
    new EasyMockTemplate(disposedWindowMonitor, delegateEventListener) {
      @Override
      protected void expectations() {
        expect(disposedWindowMonitor.isDuplicateDispose(event)).andReturn(true);
      }

      @Override
      protected void codeToTest() {
        eventNormalizer.eventDispatched(event);
      }
    }.run();
  }
}
