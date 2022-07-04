package com.liferay.petra.process;

/**
 * @author Hai Yu
 */
public interface ProcessLog4jLog {

	public String getName();

	public String getLevel();

	public String getMessage();

	public Throwable getThrowable();
}
