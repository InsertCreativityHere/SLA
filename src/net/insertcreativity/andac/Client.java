
package net.insertcreativity.andac;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Client
{
	public static void main(String args[]) throws IOException
	{
		try(ServerSocket serverSocket = new ServerSocket(7812))
		{
			Socket socket = serverSocket.accept();
			System.out.println("Got socket");
			socket.getOutputStream().write("hello!\n".getBytes());
			System.out.println("helloed");
			try{Thread.sleep(30000);}catch(Exception exception){}
			socket.getOutputStream().write("still there?\n".getBytes());
			System.out.println("stilled");
			socket.close();
		}
	}
}
