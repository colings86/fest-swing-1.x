/*
 * Created on Sep 29, 2006
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
 * Copyright @2006-2013 the original author or authors.
 */
package org.fest.swing.core;

import static java.awt.event.InputEvent.BUTTON1_MASK;
import static java.awt.event.InputEvent.BUTTON2_MASK;
import static java.awt.event.InputEvent.BUTTON3_MASK;
import static java.awt.event.KeyEvent.CHAR_UNDEFINED;
import static java.awt.event.KeyEvent.KEY_TYPED;
import static java.awt.event.KeyEvent.VK_UNDEFINED;
import static java.awt.event.WindowEvent.WINDOW_CLOSING;
import static java.lang.System.currentTimeMillis;
import static javax.swing.SwingUtilities.getWindowAncestor;
import static javax.swing.SwingUtilities.isEventDispatchThread;
import static org.fest.swing.awt.AWT.centerOf;
import static org.fest.swing.awt.AWT.visibleCenterOf;
import static org.fest.swing.core.ActivateWindowTask.activateWindow;
import static org.fest.swing.core.ComponentIsFocusableQuery.isFocusable;
import static org.fest.swing.core.ComponentRequestFocusTask.giveFocusTo;
import static org.fest.swing.core.FocusOwnerFinder.focusOwner;
import static org.fest.swing.core.FocusOwnerFinder.inEdtFocusOwner;
import static org.fest.swing.core.InputModifiers.unify;
import static org.fest.swing.core.MouseButton.LEFT_BUTTON;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.fest.swing.core.Scrolling.scrollToVisible;
import static org.fest.swing.core.WindowAncestorFinder.windowAncestorOf;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.exception.ActionFailedException.actionFailure;
import static org.fest.swing.format.Formatting.format;
import static org.fest.swing.format.Formatting.inEdtFormat;
import static org.fest.swing.hierarchy.NewHierarchy.ignoreExistingComponents;
import static org.fest.swing.keystroke.KeyStrokeMap.keyStrokeFor;
import static org.fest.swing.query.ComponentShowingQuery.isShowing;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.swing.util.Modifiers.keysFor;
import static org.fest.swing.util.Modifiers.updateModifierWithKeyCode;
import static org.fest.swing.util.TimeoutWatch.startWatchWithTimeoutOf;
import static org.fest.util.Lists.newArrayList;
import static org.fest.util.Preconditions.checkNotNull;
import static org.fest.util.Strings.concat;

import java.applet.Applet;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

import org.fest.swing.annotation.RunsInCurrentThread;
import org.fest.swing.annotation.RunsInEDT;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ActionFailedException;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.UnexpectedException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.hierarchy.ComponentHierarchy;
import org.fest.swing.hierarchy.ExistingHierarchy;
import org.fest.swing.input.InputState;
import org.fest.swing.lock.ScreenLock;
import org.fest.swing.monitor.WindowMonitor;
import org.fest.swing.util.Pair;
import org.fest.swing.util.TimeoutWatch;
import org.fest.swing.util.ToolkitProvider;
import org.fest.util.VisibleForTesting;

/**
 * Default implementation of {@link Robot}.
 *
 * @author Alex Ruiz
 * @author Yvonne Wang
 * @see Robot
 */
public class BasicRobot implements Robot {
    private static final int POPUP_DELAY = 10000;
    private static final int POPUP_TIMEOUT = 5000;
    private static final int WINDOW_DELAY = 20000;

    private static final ComponentMatcher POPUP_MATCHER = new TypeMatcher(JPopupMenu.class, true);

    @GuardedBy("this")
    private volatile boolean active;

    private static final Runnable EMPTY_RUNNABLE = new Runnable() {
        @Override
        public void run() {
        }
    };

    private static final int BUTTON_MASK = BUTTON1_MASK | BUTTON2_MASK | BUTTON3_MASK;

    private static Toolkit toolkit = ToolkitProvider.instance().defaultToolkit();
    private static WindowMonitor windowMonitor = WindowMonitor.instance();
    private static InputState inputState = new InputState(toolkit);

    private final ComponentHierarchy hierarchy;
    private final Object screenLockOwner;
    private final ComponentFinder finder;
    private final Settings settings;
    private final AWTEventPoster eventPoster;
    private final InputEventGenerator eventGenerator;
    private final UnexpectedJOptionPaneFinder unexpectedJOptionPaneFinder;

    /**
     * Creates a new {@link Robot} with a new AWT hierarchy. The created {@code Robot} will not be able to access any
     * AWT and Swing {@code Component}s that were created before it.
     *
     * @return the created {@code Robot}.
     */
    public static @Nonnull Robot robotWithNewAwtHierarchy() {
        Object screenLockOwner = acquireScreenLock();
        return new BasicRobot(screenLockOwner, ignoreExistingComponents());
    }

    public static @Nonnull Robot robotWithNewAwtHierarchyWithoutScreenLock() {
        return new BasicRobot(null, ignoreExistingComponents());
    }

    /**
     * Creates a new {@link Robot} that has access to all the AWT and Swing {@code Component}s in the AWT hierarchy.
     *
     * @return the created {@code Robot}.
     */
    public static @Nonnull Robot robotWithCurrentAwtHierarchy() {
        Object screenLockOwner = acquireScreenLock();
        return new BasicRobot(screenLockOwner, new ExistingHierarchy());
    }

    // TODO document
    public static @Nonnull Robot robotWithCurrentAwtHierarchyWithoutScreenLock() {
        return new BasicRobot(null, new ExistingHierarchy());
    }

    private static @Nonnull Object acquireScreenLock() {
        Object screenLockOwner = new Object();
        ScreenLock.instance().acquire(screenLockOwner);
        return screenLockOwner;
    }

    @VisibleForTesting
    BasicRobot(@Nullable final Object screenLockOwner, @Nonnull final ComponentHierarchy hierarchy) {
        this.screenLockOwner = screenLockOwner;
        this.hierarchy = hierarchy;
        this.settings = new Settings();
        this.eventGenerator = new RobotEventGenerator(this.settings);
        this.eventPoster = new AWTEventPoster(toolkit, inputState, windowMonitor, this.settings);
        this.finder = new BasicComponentFinder(hierarchy, this.settings);
        this.unexpectedJOptionPaneFinder = new UnexpectedJOptionPaneFinder(this.finder);
        this.active = true;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ComponentPrinter printer() {
        return finder().printer();
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ComponentFinder finder() {
        return this.finder;
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void showWindow(@Nonnull final Window w) {
        showWindow(w, null, true);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void showWindow(@Nonnull final Window w, @Nonnull final Dimension size) {
        showWindow(w, size, true);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void showWindow(@Nonnull final Window w, @Nullable final Dimension size, final boolean pack) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (pack) {
                    packAndEnsureSafePosition(w);
                }
                if (size != null) {
                    w.setSize(size);
                }
                w.setVisible(true);
            }
        });
        waitForWindow(w);
    }

    @RunsInCurrentThread
    private void packAndEnsureSafePosition(@Nonnull final Window w) {
        w.pack();
        w.setLocation(100, 100);
    }

    @RunsInEDT
    private void waitForWindow(@Nonnull final Window w) {
        long start = currentTimeMillis();
        while (!windowMonitor.isWindowReady(w) || !isShowing(w)) {
            long elapsed = currentTimeMillis() - start;
            if (elapsed > WINDOW_DELAY) {
                throw new WaitTimedOutError(concat("Timed out waiting for Window to open (", String.valueOf(elapsed),
                        "ms)"));
            }
            pause();
        }
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void close(@Nonnull final Window w) {
        WindowEvent event = new WindowEvent(w, WINDOW_CLOSING);
        // If the window contains an applet, send the event on the applet's queue instead to ensure a shutdown from the
        // applet's context (assists AppletViewer cleanup).
        Component applet = findAppletDescendent(w);
        EventQueue eventQueue = windowMonitor.eventQueueFor(applet != null ? applet : w);
        checkNotNull(eventQueue).postEvent(event);
        waitForIdle();
    }

    /**
     * Returns the {@code Applet} descendant of the given AWT {@code Container}, if any.
     *
     * @param c the given {@code Container}.
     * @return the {@code Applet} descendant of the given AWT {@code Container}, or {@code null} if none is found.
     */
    @RunsInEDT
    private @Nullable Applet findAppletDescendent(@Nonnull final Container c) {
        List<Component> found = newArrayList(this.finder.findAll(c, new TypeMatcher(Applet.class)));
        if (found.size() == 1) {
            return (Applet) found.get(0);
        }
        return null;
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void focusAndWaitForFocusGain(@Nonnull final Component c) {
        focus(c, true);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void focus(@Nonnull final Component c) {
        focus(c, false);
    }

    @RunsInEDT
    private void focus(@Nonnull final Component target, final boolean wait) {
        Component currentOwner = inEdtFocusOwner();
        if (currentOwner == target) {
            return;
        }
        FocusMonitor focusMonitor = FocusMonitor.attachTo(target);
        // for pointer focus
        moveMouse(target);
        // Make sure the correct window is in front
        activateWindowOfFocusTarget(target, currentOwner);
        giveFocusTo(target);
        try {
            if (wait) {
                TimeoutWatch watch = startWatchWithTimeoutOf(settings().timeoutToBeVisible());
                while (!focusMonitor.hasFocus()) {
                    if (watch.isTimeOut()) {
                        throw actionFailure(concat("Focus change to ", format(target), " failed"));
                    }
                    pause();
                }
            }
        } finally {
            target.removeFocusListener(focusMonitor);
        }
    }

    @RunsInEDT
    private void activateWindowOfFocusTarget(@Nullable final Component target, @Nullable final Component currentOwner) {
        Pair<Window, Window> windowAncestors = windowAncestorsOf(currentOwner, target);
        Window currentOwnerAncestor = windowAncestors.first;
        Window targetAncestor = windowAncestors.second;
        if (currentOwnerAncestor == targetAncestor) {
            return;
        }
        activate(checkNotNull(targetAncestor));
        waitForIdle();
    }

    @RunsInEDT
    private static Pair<Window, Window> windowAncestorsOf(final @Nullable Component one, final @Nullable Component two) {
        return execute(new GuiQuery<Pair<Window, Window>>() {
            @Override
            protected Pair<Window, Window> executeInEDT() throws Throwable {
                return Pair.of(windowAncestor(one), windowAncestor(two));
            }

            private @Nullable Window windowAncestor(final Component c) {
                return c != null ? windowAncestorOf(c) : null;
            }
        });
    }

    /**
     * Activates the given AWT {@code Window}. "Activate" means that the given window gets the keyboard focus.
     *
     * @param w the window to activate.
     */
    @RunsInEDT
    private void activate(@Nonnull final Window w) {
        activateWindow(w);
        moveMouse(w); // For pointer-focus systems
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public synchronized void cleanUp() {
        cleanUp(true);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public synchronized void cleanUpWithoutDisposingWindows() {
        cleanUp(false);
    }

    @RunsInEDT
    private void cleanUp(final boolean disposeWindows) {
        try {
            if (disposeWindows) {
                disposeWindows(this.hierarchy);
            }
            releaseMouseButtons();
        } finally {
            this.active = false;
            releaseScreenLock();
        }
    }

    private void releaseScreenLock() {
        ScreenLock screenLock = ScreenLock.instance();
        if (screenLock.acquiredBy(this.screenLockOwner)) {
            screenLock.release(this.screenLockOwner);
        }
    }

    @RunsInEDT
    private static void disposeWindows(final @Nonnull ComponentHierarchy hierarchy) {
        execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                for (Container c : hierarchy.roots()) {
                    if (c instanceof Window) {
                        dispose(hierarchy, (Window) c);
                    }
                }
            }
        });
    }

    @RunsInCurrentThread
    private static void dispose(final @Nonnull ComponentHierarchy hierarchy, @Nonnull final Window w) {
        hierarchy.dispose(w);
        w.setVisible(false);
        w.dispose();
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Component c) {
        click(c, LEFT_BUTTON);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void rightClick(@Nonnull final Component c) {
        click(c, RIGHT_BUTTON);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Component c, @Nonnull final MouseButton button) {
        click(c, button, 1);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void doubleClick(@Nonnull final Component c) {
        click(c, LEFT_BUTTON, 2);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Component c, @Nonnull final MouseButton button, final int times) {
        Point where = visibleCenterOf(c);
        if (c instanceof JComponent) {
            where = scrollIfNecessary((JComponent) c);
        }
        click(c, where, button, times);
    }

    private @Nonnull Point scrollIfNecessary(@Nonnull final JComponent c) {
        scrollToVisible(this, c);
        return visibleCenterOf(c);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Component c, @Nonnull final Point where) {
        click(c, where, LEFT_BUTTON, 1);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Point where, @Nonnull final MouseButton button, final int times) {
        doClick(null, where, button, times);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void click(@Nonnull final Component c, @Nonnull final Point where, @Nonnull final MouseButton button,
            final int times) {
        doClick(c, where, button, times);
    }

    private void doClick(@Nullable final Component c, @Nonnull final Point where, @Nonnull final MouseButton button,
            final int times) {
        int mask = button.mask;
        int modifierMask = mask & ~BUTTON_MASK;
        mask &= BUTTON_MASK;
        pressModifiers(modifierMask);
        // From Abbot: Adjust the auto-delay to ensure we actually get a multiple click
        // In general clicks have to be less than 200ms apart, although the actual setting is not readable by Java.
        int delayBetweenEvents = this.settings.delayBetweenEvents();
        if (shouldSetDelayBetweenEventsToZeroWhenClicking(times)) {
            this.settings.delayBetweenEvents(0);
        }
        if (c == null) {
            this.eventGenerator.pressMouse(where, mask);
            for (int i = times; i > 1; i--) {
                this.eventGenerator.releaseMouse(mask);
                this.eventGenerator.pressMouse(where, mask);
            }
        } else {
            this.eventGenerator.pressMouse(c, where, mask);
            for (int i = times; i > 1; i--) {
                this.eventGenerator.releaseMouse(mask);
                this.eventGenerator.pressMouse(c, where, mask);
            }
        }
        this.settings.delayBetweenEvents(delayBetweenEvents);
        this.eventGenerator.releaseMouse(mask);
        releaseModifiers(modifierMask);
        waitForIdle();
    }

    private boolean shouldSetDelayBetweenEventsToZeroWhenClicking(final int times) {
        return times > 1 /* FEST-137: && settings.delayBetweenEvents() * 2 > 200 */;
    }

    /** {@inheritDoc} */
    @Override
    public void pressModifiers(final int modifierMask) {
        for (int modifierKey : keysFor(modifierMask)) {
            pressKey(modifierKey);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void releaseModifiers(final int modifierMask) {
        // For consistency, release in the reverse order of press.
        int[] modifierKeys = keysFor(modifierMask);
        for (int i = modifierKeys.length - 1; i >= 0; i--) {
            releaseKey(modifierKeys[i]);
        }
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void moveMouse(@Nonnull final Component c) {
        moveMouse(c, visibleCenterOf(c));
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void moveMouse(@Nonnull final Component c, @Nonnull final Point p) {
        moveMouse(c, p.x, p.y);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void moveMouse(@Nonnull final Component c, final int x, final int y) {
        if (!waitForComponentToBeReady(c, this.settings.timeoutToBeVisible())) {
            throw actionFailure(concat("Could not obtain position of component ", format(c)));
        }
        this.eventGenerator.moveMouse(c, x, y);
        waitForIdle();
    }

    /** {@inheritDoc} */
    @Override
    public void moveMouse(@Nonnull final Point p) {
        moveMouse(p.x, p.y);
    }

    /** {@inheritDoc} */
    @Override
    public void moveMouse(final int x, final int y) {
        this.eventGenerator.moveMouse(x, y);
    }

    /** {@inheritDoc} */
    @Override
    public void pressMouse(@Nonnull final MouseButton button) {
        this.eventGenerator.pressMouse(button.mask);
    }

    /** {@inheritDoc} */
    @Override
    public void pressMouse(@Nonnull final Component c, @Nonnull final Point where) {
        pressMouse(c, where, LEFT_BUTTON);
    }

    /** {@inheritDoc} */
    @Override
    public void pressMouse(@Nonnull final Component c, @Nonnull final Point where, @Nonnull final MouseButton button) {
        jitter(c, where);
        moveMouse(c, where.x, where.y);
        this.eventGenerator.pressMouse(c, where, button.mask);
    }

    /** {@inheritDoc} */
    @Override
    public void pressMouse(@Nonnull final Point where, @Nonnull final MouseButton button) {
        this.eventGenerator.pressMouse(where, button.mask);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void releaseMouse(@Nonnull final MouseButton button) {
        mouseRelease(button.mask);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void releaseMouseButtons() {
        int buttons = inputState.buttons();
        if (buttons == 0) {
            return;
        }
        mouseRelease(buttons);
    }

    /** {@inheritDoc} */
    @Override
    public void rotateMouseWheel(@Nonnull final Component c, final int amount) {
        moveMouse(c);
        rotateMouseWheel(amount);
    }

    /** {@inheritDoc} */
    @Override
    public void rotateMouseWheel(final int amount) {
        this.eventGenerator.rotateMouseWheel(amount);
        waitForIdle();
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void jitter(@Nonnull final Component c) {
        jitter(c, visibleCenterOf(c));
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void jitter(@Nonnull final Component c, @Nonnull final Point where) {
        int x = where.x;
        int y = where.y;
        moveMouse(c, x > 0 ? x - 1 : x + 1, y);
    }

    // Wait the given number of milliseconds for the component to be showing and ready.
    @RunsInEDT
    private boolean waitForComponentToBeReady(@Nonnull final Component c, final long timeout) {
        if (isReadyForInput(c)) {
            return true;
        }
        TimeoutWatch watch = startWatchWithTimeoutOf(timeout);
        while (!isReadyForInput(c)) {
            if (c instanceof JPopupMenu) {
                // wiggle the mouse over the parent menu item to ensure the sub-menu shows
                Pair<Component, Point> invokerAndCenterOfInvoker = invokerAndCenterOfInvoker((JPopupMenu) c);
                Component invoker = invokerAndCenterOfInvoker.first;
                if (invoker instanceof JMenu) {
                    jitter(invoker, invokerAndCenterOfInvoker.second);
                }
            }
            if (watch.isTimeOut()) {
                return false;
            }
            pause();
        }
        return true;
    }

    @RunsInEDT
    private static @Nonnull Pair<Component, Point> invokerAndCenterOfInvoker(final @Nonnull JPopupMenu popupMenu) {
        Pair<Component, Point> result = execute(new GuiQuery<Pair<Component, Point>>() {
            @Override
            protected Pair<Component, Point> executeInEDT() {
                Component invoker = checkNotNull(popupMenu.getInvoker());
                return Pair.of(invoker, centerOf(invoker));
            }
        });
        return checkNotNull(result);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void enterText(@Nonnull final String text) {
        checkNotNull(text);
        if (text.isEmpty()) {
            return;
        }
        for (char character : text.toCharArray()) {
            type(character);
        }
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void type(final char character) {
        KeyStroke keyStroke = keyStrokeFor(character);
        if (keyStroke == null) {
            Component focus = focusOwner();
            if (focus == null) {
                return;
            }
            KeyEvent keyEvent = keyEventFor(focus, character);
            // Allow any pending robot events to complete; otherwise we might stuff the typed event before previous
            // robot-generated events are posted.
            waitForIdle();
            this.eventPoster.postEvent(focus, keyEvent);
            return;
        }
        keyPressAndRelease(keyStroke.getKeyCode(), keyStroke.getModifiers());
    }

    private KeyEvent keyEventFor(final Component c, final char character) {
        return new KeyEvent(c, KEY_TYPED, currentTimeMillis(), 0, VK_UNDEFINED, character);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void pressAndReleaseKey(final int keyCode, @Nonnull final int... modifiers) {
        keyPressAndRelease(keyCode, unify(modifiers));
        waitForIdle();
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void pressAndReleaseKeys(@Nonnull final int... keyCodes) {
        for (int keyCode : keyCodes) {
            keyPressAndRelease(keyCode, 0);
            waitForIdle();
            pause(50); // it seems that even when waiting for idle the events are not completely propagated
        }
    }

    @RunsInEDT
    private void keyPressAndRelease(final int keyCode, final int modifiers) {
        int updatedModifiers = updateModifierWithKeyCode(keyCode, modifiers);
        pressModifiers(updatedModifiers);
        if (updatedModifiers == modifiers) {
            doPressKey(keyCode);
            this.eventGenerator.releaseKey(keyCode);
        }
        releaseModifiers(updatedModifiers);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void pressKey(final int keyCode) {
        doPressKey(keyCode);
        waitForIdle();
    }

    @RunsInEDT
    private void doPressKey(final int keyCode) {
        this.eventGenerator.pressKey(keyCode, CHAR_UNDEFINED);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void releaseKey(final int keyCode) {
        this.eventGenerator.releaseKey(keyCode);
        waitForIdle();
    }

    @RunsInEDT
    private void mouseRelease(final int buttons) {
        this.eventGenerator.releaseMouse(buttons);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void waitForIdle() {
        waitIfNecessary();
        Collection<EventQueue> queues = windowMonitor.allEventQueues();
        if (queues.size() == 1) {
            waitForIdle(checkNotNull(toolkit.getSystemEventQueue()));
            return;
        }
        // FIXME this resurrects dead event queues
        for (EventQueue queue : queues) {
            waitForIdle(checkNotNull(queue));
        }
    }

    private void waitIfNecessary() {
        int delayBetweenEvents = this.settings.delayBetweenEvents();
        int eventPostingDelay = this.settings.eventPostingDelay();
        if (eventPostingDelay > delayBetweenEvents) {
            pause(eventPostingDelay - delayBetweenEvents);
        }
    }

    /**
     * BSmith 29/06/2015 I have changed this method in line with others advice to avoid issues with Java 7 and Custom
     * Event Queues. Without this change every action takes 10 seconds when testing cyberreveal. The new strategy to
     * make sure all events are done is to put a blank event on the queue and wait for it to be actioned
     *
     * @param eventQueue The queue to check
     */
    private void waitForIdle(@Nonnull final EventQueue eventQueue) {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalThreadStateException("Cannot call method from the event dispatcher thread");
        }
        try {
            EventQueue.invokeAndWait(EMPTY_RUNNABLE);
        } catch (Exception e) {
            throw new UnexpectedException("could not invokeAndWait", e);
        }

    }

    // Indicates whether we timed out waiting for the invocation to run
    @RunsInEDT
    private boolean postInvocationEvent(@Nonnull final EventQueue eventQueue, final long timeout) {
        Object lock = new RobotIdleLock();
        synchronized (lock) {
            eventQueue.postEvent(new InvocationEvent(toolkit, EMPTY_RUNNABLE, lock, true));
            long start = currentTimeMillis();
            try {
                // NOTE: on fast linux systems when showing a dialog, if we don't provide a timeout, we're never
                // notified, and
                // the test will wait forever (up through 1.5.0_05).
                lock.wait(timeout);
                return currentTimeMillis() - start >= this.settings.idleTimeout();
            } catch (InterruptedException e) {
            }
            return false;
        }
    }

    private static class RobotIdleLock {
        RobotIdleLock() {
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDragging() {
        return inputState.dragInProgress();
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public @Nonnull JPopupMenu showPopupMenu(@Nonnull final Component invoker) {
        return showPopupMenu(invoker, visibleCenterOf(invoker));
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public @Nonnull JPopupMenu showPopupMenu(@Nonnull final Component invoker, @Nonnull final Point location) {
        if (isFocusable(invoker)) {
            focusAndWaitForFocusGain(invoker);
        }
        click(invoker, location, RIGHT_BUTTON, 1);
        JPopupMenu popup = findActivePopupMenu();
        if (popup == null) {
            throw new ComponentLookupException(concat("Unable to show popup at ", location, " on ",
                    inEdtFormat(invoker)));
        }
        long start = currentTimeMillis();
        while (!isWindowAncestorReadyForInput(popup) && currentTimeMillis() - start > POPUP_DELAY) {
            pause();
        }
        return popup;
    }

    @RunsInEDT
    private boolean isWindowAncestorReadyForInput(final JPopupMenu popup) {
        Boolean result = execute(new GuiQuery<Boolean>() {
            @Override
            protected Boolean executeInEDT() {
                Window ancestor = checkNotNull(getWindowAncestor(popup));
                return isReadyForInput(ancestor);
            }
        });
        return checkNotNull(result);
    }

    /**
     * <p>
     * Indicates whether the given AWT or Swing {@code Component} is ready for input.
     * </p>
     * <p>
     * <b>Note:</b> This method is accessed in the current executing thread. Such thread may or may not be the event
     * dispatch thread (EDT.) Client code must call this method from the EDT.
     * </p>
     *
     * @param c the given {@code Component}.
     * @return {@code true} if the given {@code Component} is ready for input, {@code false} otherwise.
     * @throws ActionFailedException if the given {@code Component} does not have a {@code Window} ancestor.
     */
    @Override
    @RunsInCurrentThread
    public boolean isReadyForInput(@Nonnull final Component c) {
        Window w = windowAncestorOf(c);
        if (w == null) {
            throw actionFailure(concat("Component ", format(c), " does not have a Window ancestor"));
        }
        return c.isShowing() && windowMonitor.isWindowReady(w);
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public @Nullable JPopupMenu findActivePopupMenu() {
        JPopupMenu popup = activePopupMenu();
        if (popup != null || isEventDispatchThread()) {
            return popup;
        }
        TimeoutWatch watch = startWatchWithTimeoutOf(POPUP_TIMEOUT);
        while ((popup = activePopupMenu()) == null) {
            if (watch.isTimeOut()) {
                break;
            }
            pause(100);
        }
        return popup;
    }

    @RunsInEDT
    private @Nullable JPopupMenu activePopupMenu() {
        List<Component> found = newArrayList(finder().findAll(POPUP_MATCHER));
        if (found.size() == 1) {
            return (JPopupMenu) found.get(0);
        }
        return null;
    }

    /** {@inheritDoc} */
    @RunsInEDT
    @Override
    public void requireNoJOptionPaneIsShowing() {
        this.unexpectedJOptionPaneFinder.requireNoJOptionPaneIsShowing();
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull Settings settings() {
        return this.settings;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull ComponentHierarchy hierarchy() {
        return this.hierarchy;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean isActive() {
        return this.active;
    }

    @VisibleForTesting
    final @Nullable Object screenLockOwner() {
        return this.screenLockOwner;
    }
}
