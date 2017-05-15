
package net.insertcreativity.util;

import java.io.File;

/**Utility class with myriad helper functions*/
public final class Util
{
	/**Deletes the specified file, also deleting any files listed under a sub-directory of the provided file
	 * @param file Path of the file or folder to be deleted
	 * @return True if the entire file/folder was deleted successfully, false otherwise*/
	public static final boolean delete(File file)
	{
		boolean success = true;//create a variable for storing whether all the deletions are successful
		File[] children = file.listFiles();//get a list of all the children in this file
		if(children != null){//if this is a non-empty directory
			for(File child : children){//iterate through all the children in this directory
				success &= delete(child);//attempt to delete the child, storing whether it was successful
			}
		}
		file.delete();//delete the actual file
		return !file.exists() && success;//return false if the file still exists or any of the deletions failed
	}
}
