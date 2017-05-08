
package net.insertcreativity.sla;

public class Logger
{
	/**Logs a string with a date-stamp
	 * @param s The string to be logged
	 * @param print Flag for whether this should also be printed to the user*/
	public final void log(String s, boolean print)
	{
		logRaw("<" + System.currentTimeMillis() + ">" + s, print);//log the string with a date-stamp
	}
	
	/**Logs a throwable, first printing the message to the user with a date-stamp, and then logging the throwable's
	 * stack trace elements
	 * @param throwable The throwable to be logged
	 * @param print Flag for whether the throwable's stack trace should also be printed to the user*/
	public final void log(Throwable throwable, boolean print)
	{
		log(throwable.toString() + '\n', true);//print the exception's message with a date-stamp
		logRaw(getThrowableString(throwable), print);//log the throwable's stack trace
	}
	
	/**Constructs a string representation of a throwable's full stack trace
	 * @param throwable The throwable who's stack trace should be retrieved
	 * @return A string containing the full stack trace and message of the throwable*/
	private final String getThrowableString(Throwable throwable)
	{
		Throwable cause = throwable.getCause();//get the cause of the throwable
		String causeString = "";//create a string for storing the cause's error message
		if(cause != null){//if the original throwable did have a cause
			causeString = "Caused by:" + cause.toString() + '\n' + getThrowableString(cause);//store the cause's string
		}
		int total = causeString.length();//create a variable for storing the total number of characters
		StackTraceElement[] stackTraceElements = throwable.getStackTrace();//get all the elements of the stack trace
		String[] stackElementStrings = new String[stackTraceElements.length];//create an array for storing all the stack trace strings
		for(int counter = 0; counter < stackTraceElements.length; counter++){//iterate through all the stack trace elements
			stackElementStrings[counter] = "\tat " + stackTraceElements[counter].toString() + '\n';//store the stack element's string value
			total += stackElementStrings[counter].length();//add the number of chars in the string to the total
		}
		char[] characters = new char[total];//create a char array for holding all the string's data
		int offsetValue = 0;//create a variable for keeping track of the current offset in the character array
		for(String stackElementString : stackElementStrings){//iterate through all the stack element's strings
			stackElementString.getChars(0, stackElementString.length(), characters, offsetValue);//copy the stack element's string into the array
			offsetValue += stackElementString.length();//add the stack trace's length to the total character count
		}
		causeString.getChars(0, causeString.length(), characters, offsetValue);//copy in the cause
		return new String(characters);//return a new string made of the character array
	}
	
	/**Logs a string exactly as it's given
	 * @param s The string to log
	 * @param print Flag for whether the throwable's stack trace should also be printed to the user*/
	public void logRaw(String s, boolean print)
	{
		if(print){//TODO work on this so it's not crap
			System.err.print(s);//TODO also you gotta make this logger thing all
		} else{//self-repairing, so things can still keep the same reference, ya feel?
			System.out.println(s);
		}
	}
}
