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

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.messaging.DestinationNames;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.MessageListenerException;
import com.liferay.portal.kernel.scheduler.JobState;
import com.liferay.portal.kernel.scheduler.JobStateSerializeUtil;
import com.liferay.portal.kernel.scheduler.SchedulerEngine;
import com.liferay.portal.kernel.scheduler.SchedulerEngineHelper;
import com.liferay.portal.kernel.scheduler.SchedulerEngineHelperUtil;
import com.liferay.portal.kernel.scheduler.SchedulerEntry;
import com.liferay.portal.kernel.scheduler.SchedulerEntryImpl;
import com.liferay.portal.kernel.scheduler.SchedulerException;
import com.liferay.portal.kernel.scheduler.StorageType;
import com.liferay.portal.kernel.scheduler.Trigger;
import com.liferay.portal.kernel.scheduler.TriggerState;
import com.liferay.portal.kernel.test.CaptureHandler;
import com.liferay.portal.kernel.test.JDKLoggerTestUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
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

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		_testMessageListener2 = new TestMessageListener2();

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
	public void testReceiveWithSchedulerDisableAndDeleteJobThrowException() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		_testMessage1.put(SchedulerEngine.DISABLE, true);

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setMessageListener(
			new MessageListener() {

				@Override
				public void receive(Message message) {
					message.setResponse(message);
				}
			}
		);

		String name = SchedulerEventMessageListenerWrapper.class.getName();

		setUpSchedulerEngineHelperUtil();

		try(CaptureHandler captureHandler =
				JDKLoggerTestUtil.configureJDKLogger(
					name, Level.INFO)) {

			schedulerEventMessageListenerWrapper.receive(_testMessage1);

			List<LogRecord> logRecords = captureHandler.getLogRecords();

			Assert.assertEquals(
				logRecords.toString(), 2, logRecords.size());

			LogRecord logRecord1 = logRecords.get(0);
			LogRecord logRecord2 = logRecords.get(1);

			Assert.assertEquals(
				"Unable to delete job null in group null",
				logRecord1.getMessage());
			Assert.assertEquals(
				"Unable to send audit message",
				logRecord2.getMessage());

			captureHandler.resetLogLevel(Level.WARNING);

			schedulerEventMessageListenerWrapper.receive(_testMessage1);

			logRecords = captureHandler.getLogRecords();

			Assert.assertEquals(
				logRecords.toString(), 0, logRecords.size());
		}
		catch (Exception e) {
			Assert.fail("Should not throw Exception");
		}
	}

	@Test
	public void testReceiveWithSchedulerDisable() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		_testMessage1.put(
			SchedulerEngine.DESTINATION_NAME,
			DestinationNames.SCHEDULER_DISPATCH);
		_testMessage1.put(SchedulerEngine.DISABLE, true);
		_testMessage1.put(SchedulerEngine.JOB_NAME, "job1");
		_testMessage1.put(SchedulerEngine.GROUP_NAME, "group1");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		Map<String, SchedulerEventMessageListenerWrapper>
			removeMessageListener =
				new HashMap<String, SchedulerEventMessageListenerWrapper>();

		String destinationName = SchedulerEngine.DESTINATION_NAME;

		removeMessageListener.put(
			destinationName, schedulerEventMessageListenerWrapper);

		Registry registry = RegistryUtil.getRegistry();

		registry.registerService(
			MessageBus.class,
			(MessageBus)ProxyUtil.newProxyInstance(
				MessageBus.class.getClassLoader(),
				new Class<?>[] {MessageBus.class},
				(proxy, method, args) -> {
					removeMessageListener.remove(destinationName);

					return true;
				}));

		Trigger trigger = getTrigger("job1", "group1", 1);

		SchedulerEntry schedulerEntry = new SchedulerEntryImpl("com.test", trigger);

		schedulerEventMessageListenerWrapper.setSchedulerEntry(schedulerEntry);

		schedulerEventMessageListenerWrapper.setMessageListener(
			new MessageListener() {

				@Override
				public void receive(Message message) {
					message.setResponse(message);
				}
			}
		);

		try {
			schedulerEventMessageListenerWrapper.receive(_testMessage1);
		}
		catch (Exception e) {
			Assert.fail("Should not throw Exception");
		}

		Assert.assertSame(
			"Message is not processed", _testMessage1,
			_testMessage1.getResponse());
		Assert.assertEquals(
			removeMessageListener.toString(), 0, removeMessageListener.size());

		_testMessage2.put(SchedulerEngine.DISABLE, true);

		try {
			schedulerEventMessageListenerWrapper.receive(_testMessage2);
		}
		catch (Exception e) {
			Assert.fail("Should not throw Exception");
		}

		Assert.assertSame(
			"Message is not processed", _testMessage2,
			_testMessage2.getResponse());
	}

	@Test
	public void testReceiveWithMessageListenerThrowException() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		JobState jobState = new JobState(TriggerState.NORMAL);

		_testMessage1.put(
			SchedulerEngine.JOB_STATE, jobState);

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		schedulerEventMessageListenerWrapper.setMessageListener(
			new MessageListener() {

				@Override
				public void receive(Message message)
					throws MessageListenerException {

					throw new MessageListenerException();
				}
			}
		);

		try {
			schedulerEventMessageListenerWrapper.receive(_testMessage1);

			Assert.fail("Should throw MessageListenerException");
		}
		catch (Exception e) {
			Assert.assertTrue(e instanceof MessageListenerException);
		}

		schedulerEventMessageListenerWrapper.setMessageListener(
			new MessageListener() {

				@Override
				public void receive(Message message)
					throws MessageListenerException {

					throw new NullPointerException();
				}
			}
		);

		try {
			schedulerEventMessageListenerWrapper.receive(_testMessage2);

			Assert.fail("Should throw MessageListenerException");
		}
		catch (Exception e) {
			Assert.assertTrue(e instanceof MessageListenerException);
			Assert.assertTrue(e.getCause() instanceof NullPointerException);
		}
	}

	@Test
	public void testReceive() {
		PropsTestUtil.setProps(
			PropsKeys.SCHEDULER_EVENT_MESSAGE_LISTENER_LOCK_TIMEOUT, "0");

		Message testMessage1 = new Message();

		testMessage1.put(
			SchedulerEngine.DESTINATION_NAME,
			DestinationNames.SCHEDULER_DISPATCH);

		testMessage1.put(SchedulerEngine.JOB_NAME, "job1");
		testMessage1.put(SchedulerEngine.GROUP_NAME, "group1");

		Message testMessage2 = new Message();

		testMessage2.put(
			SchedulerEngine.DESTINATION_NAME,
			DestinationNames.SCHEDULER_DISPATCH);

		testMessage2.put(SchedulerEngine.JOB_NAME, "job2");
		testMessage2.put(SchedulerEngine.GROUP_NAME, "group1");

		Message testMessage3 = new Message();

		testMessage3.put(
			SchedulerEngine.DESTINATION_NAME,
			DestinationNames.SCHEDULER_DISPATCH);

		testMessage3.put(SchedulerEngine.JOB_NAME, "job1");
		testMessage3.put(SchedulerEngine.GROUP_NAME, "group2");

		SchedulerEventMessageListenerWrapper
			schedulerEventMessageListenerWrapper =
				new SchedulerEventMessageListenerWrapper();

		Trigger trigger = getTrigger("job1", "group1", 1);

		SchedulerEntry schedulerEntry = new SchedulerEntryImpl("com.test", trigger);

		schedulerEventMessageListenerWrapper.setSchedulerEntry(schedulerEntry);
	
		schedulerEventMessageListenerWrapper.setMessageListener(
			_testMessageListener2);

		setUpSchedulerEngineHelperUtil();

		try {
			SchedulerEngineHelperUtil.schedule(
				trigger, StorageType.MEMORY_CLUSTERED, "",
				DestinationNames.SCHEDULER_DISPATCH, testMessage1, 0);

			schedulerEventMessageListenerWrapper.receive(testMessage1);

			Assert.assertSame(
				"Message is not processed", testMessage1,
				testMessage1.getResponse());

			SchedulerEngineHelperUtil.schedule(
				trigger, StorageType.MEMORY_CLUSTERED, "",
				DestinationNames.SCHEDULER_DISPATCH, testMessage2, 0);

			schedulerEventMessageListenerWrapper.receive(testMessage2);

			Assert.assertNull(testMessage2.getResponse());

			SchedulerEngineHelperUtil.schedule(
				trigger, StorageType.MEMORY_CLUSTERED, "",
				DestinationNames.SCHEDULER_DISPATCH, testMessage3, 0);

			schedulerEventMessageListenerWrapper.receive(testMessage3);

			Assert.assertNull(testMessage2.getResponse());
		}
		catch(Exception mle) {

		}
	}

	protected static Trigger getTrigger(
		String jobName, String groupName, int interval) {

		return new MockTrigger(
			jobName, groupName, null, null, interval, TimeUnit.MINUTES);
	}

	protected void setUpSchedulerEngineHelperUtil() {
		final Method schedule = ReflectionTestUtil.getMethod(
			SchedulerEngineHelper.class, "schedule",
			new Class<?>[] {
				Trigger.class, StorageType.class, String.class, String.class,
				Message.class, int.class});
		final Method auditSchedulerJobs = ReflectionTestUtil.getMethod(
			SchedulerEngineHelper.class, "auditSchedulerJobs",
			new Class<?>[] {Message.class, TriggerState.class});
		final Method delete = ReflectionTestUtil.getMethod(
			SchedulerEngineHelper.class, "delete",
			new Class<?>[] {String.class, String.class, StorageType.class});

		SchedulerEngineHelper schedulerEngineHelper = 
			(SchedulerEngineHelper)ProxyUtil.newProxyInstance(
				SchedulerEventMessageListenerWrapperTest.class.getClassLoader(),
				new Class[] {SchedulerEngineHelper.class},
				new InvocationHandler() {

				@Override
				public Object invoke(
						Object proxy, Method method, Object[] args)
					throws PortalException {

					if (method.equals(schedule)) {
						return null;
					}
					else if (method.equals(delete)) {
						throw new SchedulerException();
					}
					else if (method.equals(auditSchedulerJobs)) {
						throw new UnsupportedOperationException();
					}

					throw new UnsupportedOperationException();
				}

			});


		RegistryUtil.setRegistry(new BasicRegistryImpl());

		Registry registry = RegistryUtil.getRegistry();

		registry.registerService(
			SchedulerEngineHelper.class, schedulerEngineHelper);
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
	private TestMessageListener2 _testMessageListener2;

	private static class MockTrigger implements Trigger {

		public MockTrigger(
			String jobName, String groupName, Date startDate, Date endDate,
			int interval, TimeUnit timeUnit) {

			_jobName = jobName;
			_groupName = groupName;

			if (startDate != null) {
				_startDate = startDate;
			}
			else {
				_startDate = new Date();
			}

			_endDate = endDate;
			_interval = interval;
			_timeUnit = timeUnit;
		}

		@Override
		public Date getEndDate() {
			return _endDate;
		}

		@Override
		public Date getFireDateAfter(Date date) {
			return null;
		}

		@Override
		public String getGroupName() {
			return _groupName;
		}

		public int getInterval() {
			return _interval;
		}

		@Override
		public String getJobName() {
			return _jobName;
		}

		@Override
		public Date getStartDate() {
			return _startDate;
		}

		public TimeUnit getTimeUnit() {
			return _timeUnit;
		}

		@Override
		public Serializable getWrappedTrigger() {
			return null;
		}

		private final Date _endDate;
		private final String _groupName;
		private final int _interval;
		private final String _jobName;
		private final Date _startDate;
		private final TimeUnit _timeUnit;

	}

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

	private class TestMessageListener2 implements MessageListener {

		@Override
		public void receive(Message message) {
			try {
				message.setResponse(message);
			}
			catch (Exception ie) {
			}
		}
	}

}