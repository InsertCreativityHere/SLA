
package net.insertcreativity.sla;

import java.io.OutputStream;
import java.io.PrintStream;

public class Testing
{
	public static void main(String args[])
	{
		PrintStream sysOut = System.out;
		System.out.println("HELLO!");
		sysOut.println("THERE!");
		
		System.setOut(new PrintStream(new Thing(sysOut)));
		System.out.println("HELLO???");
		
		try{Thread.sleep(1000);}catch(Exception e){}
		//sysOut.println("STILL ALIVE!");
	}
	
	private static class Thing extends OutputStream
	{
		private PrintStream print;
		
		public Thing(PrintStream printer)
		{
			print = printer;
		}
		
		public void write(int b)
		{
			print.println(b);
			System.err.println("GOT: " + (char)b);
		}
	}
}
