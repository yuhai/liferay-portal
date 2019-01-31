/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.kernel.scheduler.messaging;

import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.scheduler.SchedulerEntry;
import com.liferay.portal.kernel.test.CaptureHandler;
import com.liferay.portal.kernel.test.JDKLoggerTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.CodeCoverageAssertor;
import com.liferay.portal.kernel.test.rule.NewEnv;
import com.liferay.portal.kernel.test.rule.NewEnvTestRule;
import com.liferay.portal.kernel.test.util.PropsTestUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.registry.BasicRegistryImpl;
import com.liferay.registry.Registry;
import com.liferay.registry.RegistryUtil;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Tina Tian
 */
@NewEnv(type = NewEnv.Type.CLASSLOADER)
public class SchedulerEventMessageListenerWrapperTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			CodeCoverageAssertor.INSTANCE, NewEnvTestRule.INSTANCE);

	@Before
	public void setUp() {
		RegistryUtil.setRegistry(new BasicRegistryImpl());

		_testMessageListener = new TestMessageListener();

		_testMessage1 = new Message();

		_testMessage1.setPayload("Test Message 1");

		_testMessage2 = new Message();

		_testMessage2.setPayload("Test Message 2");

		_testMessage3 = new Message();

		_testMessage3.setPayload("Test Message 3");
	}

	@Test
	public void testGetSchedulerEntry() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		SchedulerEntry schedulerEntry = Mockito.mock(SchedulerEntry.class);

		schedulerEventMessageListenerWrapper.setSchedulerEntry(schedulerEntry);

		Assert.assertEquals(
			schedulerEntry,
			schedulerEventMessageListenerWrapper.getSchedulerEntry());
	}

	@Test
	public void testConcurrentReceiveWithoutTimeout() throws Exception {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setMessageListener(
			_testMessageListener);

		FutureTask<Void> futureTask1 = _startThread(
			schedulerEventMessageListenerWrapper, "Thread1", _testMessage1);

		_testMessageListener.waitUntilBlock();

		FutureTask<Void> futureTask2 = _startThread(
			schedulerEventMessageListenerWrapper, "Thread2", _testMessage2);

		try {
			futureTask2.get(1000, TimeUnit.MICROSECONDS);

			Assert.fail("Should throw TimeoutException");
		}
		catch (Exception e) {
			Assert.assertTrue(e instanceof TimeoutException);
		}

		_testMessageListener.unblock();

		futureTask1.get();
		futureTask2.get();

		Assert.assertSame(
			"Message is not processed", _testMessage1,
			_testMessage1.getResponse());
		Assert.assertSame(
			"Message is not processed", _testMessage2,
			_testMessage2.getResponse());
	}

	@Test
	public void testConcurrentReceiveWithTimeout() throws Exception {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "1000");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setMessageListener(
			_testMessageListener);

		Registry registry = RegistryUtil.getRegistry();

		Message[] receivedMessage = new Message[1];

		registry.registerService(
			MessageBus.class,
			(MessageBus)ProxyUtil.newProxyInstance(
				MessageBus.class.getClassLoader(),
				new Class<?>[] {MessageBus.class},
				(proxy, method, args) -> {
					receivedMessage[0] = (Message)args[1];

					return null;
				}));

		FutureTask<Void> futureTask1 = _startThread(
			schedulerEventMessageListenerWrapper, "Thread1", _testMessage1);

		_testMessageListener.waitUntilBlock();

		FutureTask<Void> futureTask2 = _startThread(
			schedulerEventMessageListenerWrapper, "Thread2", _testMessage2);

		futureTask2.get();

		Assert.assertNull(_testMessage2.getResponse());
		Assert.assertSame(_testMessage2, receivedMessage[0]);

		_testMessageListener.unblock();

		futureTask1.get();

		Assert.assertSame(
			"Message is not processed", _testMessage1,
			_testMessage1.getResponse());
	}

	@Test
	public void testConcurrentReceiveWithTimeoutAndInterrupted()
		throws Exception {

		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "1000");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setMessageListener(
			_testMessageListener);

		FutureTask<Void> futureTask1 = _startThread(
			schedulerEventMessageListenerWrapper, "Thread1", _testMessage1);

		_testMessageListener.waitUntilBlock();

		TestCallable testCallable = new TestCallable(
			true, _testMessage2, schedulerEventMessageListenerWrapper);

		FutureTask<Void> futureTask2 = new FutureTask<>(testCallable);

		Thread thread2 = new Thread(
			futureTask2,
			"SchedulerEventMessageListenerWrapperTest_startThread_Thread2");

		thread2.start();

		thread2.interrupt();

		futureTask2.get();

		Assert.assertNull(_testMessage2.getResponse());

		TestCallable testCallable3 = new TestCallable(
			false, _testMessage3, schedulerEventMessageListenerWrapper);

		FutureTask<Void> futureTask3 = new FutureTask<>(testCallable3);

		Thread thread3 = new Thread(
			futureTask3,
			"SchedulerEventMessageListenerWrapperTest_startThread_Thread3");

		thread3.start();

		thread3.interrupt();

		futureTask3.get();

		Assert.assertNull(_testMessage3.getResponse());

		_testMessageListener.unblock();

		futureTask1.get();

		Assert.assertSame(
			"Message is not processed", _testMessage1,
			_testMessage1.getResponse());
	}

	@Test
	public void testMisc() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setGroupName("groupName");
		schedulerEventMessageListenerWrapper.setJobName("jobName");
	}

	private FutureTask<Void> _startThread(
		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper,
		String threadName, Message message) {

		FutureTask<Void> futureTask = new FutureTask<>(
			() -> {
				schedulerEventMessageListenerWrapper.receive(message);

				return null;
			});

		Thread thread = new Thread(
			futureTask,
			"SchedulerEventMessageListenerWrapperTest_startThread_" +
				threadName);

		thread.start();

		return futureTask;
	}

	private Message _testMessage1;
	private Message _testMessage2;
	private Message _testMessage3;
	private TestMessageListener _testMessageListener;

	private class TestCallable implements Callable<Void> {

		@Override
		public Void call() throws Exception {
			String name = SchedulerEventMessageListenerWrapper.class.getName();

			if (_enableLog) {
				try (CaptureHandler captureHandler =
						JDKLoggerTestUtil.configureJDKLogger(
							name, Level.INFO)) {

					_schedulerEventMessageListenerWrapper.receive(_testMessage);

					List<LogRecord> logRecords = captureHandler.getLogRecords();

					Assert.assertEquals(
						logRecords.toString(), 1, logRecords.size());

					LogRecord logRecord = logRecords.get(0);

					int timeout = GetterUtil.getInteger(
						PropsUtil.get(
							PropsKeys.
								SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT));

					Assert.assertEquals(
						"Unable to wait " + timeout +
						" milliseconds before retry", logRecord.getMessage());
				}
				catch (Exception e) {
					Assert.assertTrue(
						e instanceof IllegalMonitorStateException);
				}
			}
			else {
				try (CaptureHandler captureHandler =
						JDKLoggerTestUtil.configureJDKLogger(
							name, Level.WARNING)) {

					_schedulerEventMessageListenerWrapper.receive(_testMessage);

					List<LogRecord> logRecords = captureHandler.getLogRecords();

					Assert.assertEquals(
						logRecords.toString(), 0, logRecords.size());
				}
				catch (Exception e) {
					Assert.assertTrue(e instanceof IllegalMonitorStateException);
				}
			}
			

			return null;
		}

		private TestCallable(
			boolean enableLog, Message message,
			SchedulerEventMessageListenerWrapper
				schedulerEventMessageListenerWrapper) {

			_enableLog = enableLog;
			_testMessage = message;
			_schedulerEventMessageListenerWrapper =
				schedulerEventMessageListenerWrapper;
		}

		private boolean _enableLog;
		private final SchedulerEventMessageListenerWrapper
			_schedulerEventMessageListenerWrapper;
		private final Message _testMessage;

	}

	private class TestMessageListener implements MessageListener {

		@Override
		public void receive(Message message) {
			_lock.lock();

			try {
				_waitCountDownLatch.countDown();

				_blockCountDownLatch.await();

				message.setResponse(message);
			}
			catch (InterruptedException ie) {
			}
			finally {
				_lock.unlock();
			}
		}

		public void unblock() {
			_blockCountDownLatch.countDown();
		}

		public void waitUntilBlock() throws InterruptedException {
			_waitCountDownLatch.await();
		}

		private final CountDownLatch _blockCountDownLatch = new CountDownLatch(
			1);
		private final Lock _lock = new ReentrantLock();
		private final CountDownLatch _waitCountDownLatch = new CountDownLatch(
			1);

	}

}