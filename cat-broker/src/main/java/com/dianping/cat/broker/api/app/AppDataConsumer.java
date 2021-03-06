package com.dianping.cat.broker.api.app;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.unidal.helper.Threads;
import org.unidal.helper.Threads.Task;
import org.unidal.lookup.annotation.Inject;

import com.dianping.cat.Cat;
import com.dianping.cat.config.app.AppDataService;
import com.dianping.cat.message.Event;

public class AppDataConsumer implements Initializable, LogEnabled {

	public static final long MINUTE = 60 * 1000L;

	public static final long DURATION = 5 * MINUTE;

	@Inject
	private AppDataService m_appDataService;

	private AppDataQueue m_appDataQueue;

	private volatile long m_dataLoss;

	private Logger m_logger;

	private ConcurrentHashMap<Long, BucketHandler> m_tasks;

	private List<BucketHandler> m_loadTasks;

	private long m_startTime;

	@Override
	public void enableLogging(Logger logger) {
		m_logger = logger;
	}

	public boolean enqueue(AppData appData) {
		return m_appDataQueue.offer(appData);
	}

	@Override
	public void initialize() throws InitializationException {
		m_appDataQueue = new AppDataQueue();
		m_tasks = new ConcurrentHashMap<Long, BucketHandler>();
		AppDataDispatcherThread appDataDispatcherThread = new AppDataDispatcherThread();
		BucketThreadController bucketThreadController = new BucketThreadController();
		m_startTime = System.currentTimeMillis();
		load();

		Threads.forGroup("Cat").start(bucketThreadController);
		Threads.forGroup("Cat").start(appDataDispatcherThread);
	}

	private class AppDataDispatcherThread implements Task {

		private static final String NAME = "AppDataDispatcherThread";

		@Override
		public String getName() {
			return NAME;
		}

		@Override
		public void run() {
			while (true) {
				try {
					AppData appData = m_appDataQueue.poll();

					if (appData != null) {
						long timestamp = appData.getTimestamp();
						timestamp = timestamp - timestamp % DURATION;
						BucketHandler handler = m_tasks.get(timestamp);

						if (handler == null) {
							recordErrorInfo();
						} else {
							boolean success = handler.enqueue(appData);

							if (!success) {
								recordErrorInfo();
							}
						}
					}
				} catch (Exception e) {
					Cat.logError(e);
				}
			}
		}

		private void recordErrorInfo() {
			m_dataLoss++;

			if (m_dataLoss % 1000 == 0) {
				Cat.logEvent("Discard", "BucketHandler", Event.SUCCESS, null);
				m_logger.error("error timestamp in consumer, loss:" + m_dataLoss);
			}
		}

		@Override
		public void shutdown() {
		}
	}

	private class BucketThreadController implements Task {

		private SimpleDateFormat m_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

		private void closeLastTask(long currentDuration) {
			Long last = new Long(currentDuration - 2 * MINUTE - DURATION);
			BucketHandler lastBucketHandler = m_tasks.get(last);

			if (lastBucketHandler != null) {
				lastBucketHandler.shutdown();
				m_logger.info("closed bucket handler ,time " + m_sdf.format(new Date(last)));
			}
		}

		private void removeLoadTasks() {
			if (m_loadTasks != null) {
				long curTime = System.currentTimeMillis();

				if (curTime - m_startTime > DURATION) {
					m_loadTasks = null;
				}
			}
		}

		@Override
		public String getName() {
			return "BucketThreadController";
		}

		private void removeLastLastTask(long currentDuration) {
			Long lastLast = new Long(currentDuration - 2 * DURATION);

			m_tasks.remove(lastLast);
		}

		@Override
		public void run() {
			while (true) {
				long curTime = System.currentTimeMillis();

				try {
					long currentDuration = curTime - curTime % DURATION;
					long currentMinute = curTime - curTime % MINUTE;

					removeLoadTasks();
					closeLastTask(currentDuration);
					startCurrentTask(currentDuration);
					startNextTask(currentDuration);
					removeLastLastTask(currentDuration);
					closeLastTask(currentMinute);
				} catch (Exception e) {
					Cat.logError(e);
				}
				long elapsedTime = System.currentTimeMillis() - curTime;

				try {
					Thread.sleep(MINUTE - elapsedTime);
				} catch (InterruptedException e) {
				}
			}
		}

		@Override
		public void shutdown() {
		}

		private void startCurrentTask(long currentDuration) {
			if (m_tasks.get(currentDuration) == null) {
				BucketHandler curBucketHandler = new BucketHandler(currentDuration, m_appDataService);
				m_logger.info("starting bucket handler ,time " + m_sdf.format(new Date(currentDuration)));
				Threads.forGroup("Cat").start(curBucketHandler);

				m_tasks.put(currentDuration, curBucketHandler);
				m_logger.info("started bucket handler ,time " + m_sdf.format(new Date(currentDuration)));
			}
		}

		private void startNextTask(long currentDuration) {
			Long next = new Long(currentDuration + DURATION);

			if (m_tasks.get(next) == null) {
				BucketHandler nextBucketHandler = new BucketHandler(next, m_appDataService);
				m_logger.info("starting bucket handler ,time " + m_sdf.format(new Date(next)));
				Threads.forGroup("Cat").start(nextBucketHandler);

				m_tasks.put(next, nextBucketHandler);
				m_logger.info("started bucket handler ,time " + m_sdf.format(new Date(next)));
			}
		}
	}

	public void save() {
		for (Entry<Long, BucketHandler> entry : m_tasks.entrySet()) {
			BucketHandler handler = entry.getValue();

			if (handler.isActive()) {
				handler.save();
			}
		}
	}

	public void load() {
		try {
			File path = new File(BucketHandler.SAVE_PATH);
			File[] files = path.listFiles();

			if (files.length > 0) {
				m_loadTasks = new ArrayList<BucketHandler>();

				for (File file : files) {
					long timestamp = Long.parseLong(file.getName());
					BucketHandler handler = new BucketHandler(timestamp, m_appDataService);

					handler.load(file);
					Threads.forGroup("Cat").start(handler);
					handler.shutdown();
					m_loadTasks.add(handler);
				}
			}
		} catch (Exception e) {
			Cat.logError(e);
		}
	}
}
