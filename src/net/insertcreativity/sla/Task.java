
package net.insertcreativity.sla;

import java.io.OutputStream;

/**Class that all tasks the ANDAC executes extends from, carrying with it the basic methods needed for
 * handling the computation of data, and outputting of results.*/
public abstract class Task implements java.io.Serializable
{
	/**Serial ID for serializing this task across networks*/
	private static final long serialVersionUID = -8932153562286540678L;
	/**Unique string identifier for this specified task*/
	public final String ID;
	
	/**Creates a new task with the specified ID*/
	public Task(String identifier)
	{
		ID = identifier;//set this tasks ID
	}
	
	/**This method should encapsulate the actual work or functionality of the task, this is what's called by the worker threads
	 * @param OutputStream  An output-stream that the task can use to log it's activity to
	 * @throws Exception If the task fails to complete properly*/
	public abstract void process(OutputStream logStream) throws Exception;
	
	/**Compares an object against this task for equality
	 * @param object The object to compare against this task
	 * @return True, only if the object is a task with the same ID as this one*/
	public final boolean equals(Object object)
	{
		if((object instanceof Task) && ((Task)object).ID.equals(ID)){//if they are both tasks and have the same ID
			return true;//return that they are equal
		}
		return false;//return that they are false otherwise
	}
}
