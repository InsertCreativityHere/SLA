
package net.insertcreativity.sla;

import java.io.OutputStream;

/**Class that tasks which yield results should extend from; in addition to all the normal task methods, this also features
 * getResult() which will supply the caller with the results from this task*/
public abstract class ResultTask extends Task
{
	/**Serial ID for serializing this task across networks*/
	private static final long serialVersionUID = -1401678467721350806L;

	/**Creates a new result yielding task*/
	public ResultTask(String identifier)
	{
		super(identifier);//set this result's ID
	}
	
	/**This method should encapsulate the actual work or functionality of the task; this is run on the clients
	 * @param OutputStream  An output-stream that the task can use to log it's activity to
	 * @throws Exception If the task fails to complete properly*/
	public abstract void process(OutputStream logStream) throws Exception;
	
	/**This method should return the results generated from the task's execution; this is run on the server
	 * @return A byte array encoding the results of the task*/
	public abstract byte[] getResults();
}
