
package net.insertcreativity.sla;

import net.insertcreativity.util.Logger;

/**Class that all tasks the ANDAC executes extends from, carrying with it the basic methods needed for
 * handling the computation of data, and outputting of results.*/
public abstract class Task implements java.io.Serializable
{
	/**The serial ID to ensure proper serialization of the class over network transfers*/
	private static final long serialVersionUID = 4108689577120646453L;
	/**Unique string identifier for this specific task*/
	public final String ID;
	
	/**Creates a new process with the specified ID and provides it with data
	 * @param identifier The identification string to assign this process*/
	public Task(String identifier)
	{
		ID = identifier;//store this tasks identifier
	}
	
	/**Carries out the actual computations or functions of the task
	 * @param Logger 
	 * @throws Exception if the process fails to complete in some way shape or form, specific implementations should
	 * further specify the exceptions they generate*/
	public abstract void process(Logger logger) throws Exception;
	
	/**Used to output or analyze the results after the task has been completed
	 * @throws Exception if the output fails to complete in some way shape or form, specific implementations should
	 * further specify the exceptions they generate*/
	public abstract void handleResults(Logger logger, stream) throws Exception;
	
	/**Compares an object against this task for equality
	 * @param object The object to compare against this task
	 * @return True, only if the object is a task with the same ID as this one*/
	public boolean equals(Object object)
	{
		if(!(object instanceof Task) && ((Task)object).ID.equals(ID)){//if they are both tasks with the same ID
			return true;//return that they are the same
		}
		return false;//return that they are false if they have different IDs or one is not a task
	}
}
