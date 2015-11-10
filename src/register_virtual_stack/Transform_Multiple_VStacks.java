/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package register_virtual_stack;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;

//import Register_Virtual_Stack_FCC
/**
 * @author Forrest Collman <forrest.collman@gmail.com> 2011
 * @version 0.1
 */

public class Transform_Multiple_VStacks implements PlugIn
{
	
	/** input directory **/
	public static String inputDirectory="";
	/** output directory **/
	public static String outputDirectory="";
	/** transforms directory **/
	public static String transformsDirectory="";
	public static String transformFile="";
	/** interpolate? **/
	public static String templateDirectory="";
	public static boolean interpolate=true;
	public static int resolutionOutput=128;
	JCheckBox []channelCheckBoxes;
	Color defaultBackColor;
	

	
	public void run(String arg) 
	{
		GenericDialogPlus gd = new GenericDialogPlus("Transform Multiple Virtual Stacks");
		gd.addDirectoryField("Input directory", inputDirectory, 50);	
		gd.addDirectoryField("Output directory", outputDirectory, 50);

		//add instructional panel
		Panel instructions=new Panel();
		instructions.setLayout(new BoxLayout(instructions,BoxLayout.PAGE_AXIS));
		instructions.add(new Label("Select either a directory of transforms, or a single transform file."));
		instructions.add(new Label("The directory option will expect that there are a set of" +
				".xml transform files that should be applied to the corresponding set of images in the input directory."));
		instructions.add(new Label("The single transform file option will apply a single .xml transform file to every image in the input directory"));
		gd.addPanel(instructions);
		
		gd.addDirectoryField("Transforms directory", transformsDirectory, 50);
		gd.addFileField("Transforms file", transformFile, 50);
		gd.addCheckbox("interpolate", interpolate );
		gd.addNumericField( "resolution :", resolutionOutput, 0 );
		Panel instructions2=new Panel();
		
		//add instructional panel
		instructions2.setLayout(new BoxLayout(instructions2,BoxLayout.PAGE_AXIS));
		instructions2.add(new Label("Input a reference directory if you wish to have the resulting "));
		instructions2.add(new Label("tranformed files to be cropped to a specific set of sizes"));
		instructions2.add(new Label("the output images will be cropped to the size of the corresponding image in the reference directory"));
		gd.addPanel(instructions2);
		
		gd.addDirectoryField("Reference directory", templateDirectory,50);
		
		gd.showDialog();
		// Exit when canceled
		if (gd.wasCanceled()) 
			return;
		
		inputDirectory = gd.getNextString();
		outputDirectory = gd.getNextString();
		transformsDirectory = gd.getNextString();
		transformFile =gd.getNextString();
		interpolate= gd.getNextBoolean();
		resolutionOutput=(int) gd.getNextNumber();
		templateDirectory=gd.getNextString();
		
		
		//make sure a input directory was given and that it exists
		String input_dir = inputDirectory;
		if (null == input_dir) 
		{
			IJ.error("Error: No input directory was provided.");
			return;
		}
		input_dir = Register_Virtual_Stack_FCC.cleanDirString(input_dir);
		final File input_dir_file=new File(input_dir);
		if(!input_dir_file.exists()){
			IJ.error("Error: input directory does not exist");
			return;
		}
		
		//make sure output directory was given 
		String output_dir = outputDirectory;
		if (null == output_dir) 
		{
			IJ.error("Error: No output directory was provided.");
			return;
		}
		//make the directory exist if it doesn't exist
		output_dir = Register_Virtual_Stack_FCC.cleanDirString(output_dir);
		final File output_dir_file=new File(output_dir);
		if(!output_dir_file.exists()) output_dir_file.mkdirs();
	
		String transf_dir = transformsDirectory;
		if(!transf_dir.isEmpty()) transf_dir = Register_Virtual_Stack_FCC.cleanDirString(transf_dir);	
		String transf_file = transformFile;		
		if (transf_file == null && transf_dir == null)
		{
			IJ.error("no transform file or transforms directory given");
			return;
		}
		String tempcrop_dir = templateDirectory;
		if (tempcrop_dir.length()>0)
		{
			tempcrop_dir = Register_Virtual_Stack_FCC.cleanDirString(tempcrop_dir);
		}
	
		
		File[] inputChannels_files=ListSubDirectories(input_dir);
		
		int numinputs=inputChannels_files.length;
		
		Panel controlPanel = new Panel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor=GridBagConstraints.LINE_START;
		c.insets=new Insets(0,0,2,5);
		c.ipadx=0;
		c.gridx=0;
		c.gridy=0;
		
		controlPanel.add(new JLabel("Input Channels"),c);		
		channelCheckBoxes= new JCheckBox[numinputs];
		for(int i=0;i<numinputs;i++){
			channelCheckBoxes[i]=new JCheckBox(inputChannels_files[i].getName());	
			defaultBackColor=channelCheckBoxes[0].getBackground();
			channelCheckBoxes[i].setSelected(true);
			c.gridx=1;
			c.gridy=i;
			controlPanel.add(channelCheckBoxes[i],c);
		}
	
		GenericDialogPlus gd2 = new GenericDialogPlus("Transform Multiple Virtual Stacks");
		gd2.setLayout(new FlowLayout());
		gd2.setSize(640,480);
		gd2.addPanel(controlPanel);		
		gd2.showDialog();	
		
		// Exit when canceled
		if (gd2.wasCanceled()) 
			return;
//		
		boolean []bool_outputSources=new boolean[numinputs];
		for(int i=0;i<numinputs;i++){
				bool_outputSources[i]=channelCheckBoxes[i].isSelected();
		}
	
	
		try {
			exec(inputChannels_files,output_dir,transf_dir,transf_file,tempcrop_dir,bool_outputSources);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}
	
	public static boolean exec(File [] inputChannels_files,
			String output_dir,
			String transf_dir,
			String transf_file,
			String tempcrop_dir,
			boolean[] bool_outputSources) throws IOException {
		// TODO Auto-generated method stub
		//figure out whether to run it in directory mode or not
		
		output_dir=Register_Virtual_Stack_FCC.cleanDirString(output_dir);
		IJ.log("output_dir:"+output_dir+"|tempcrop_dir:"+tempcrop_dir);
		
		boolean directory_mode=true;
		if (transf_file.isEmpty() && !transf_dir.isEmpty())
		{
			directory_mode=true;
		}
		if (!transf_file.isEmpty() && transf_dir.isEmpty())
		{
			directory_mode=false;
		}
		if (!transf_file.isEmpty() && !transf_dir.isEmpty())
		{
			directory_mode=true;
		}
		
		boolean crop_mode=false;
		if (tempcrop_dir.length()>0) crop_mode=true;
		
		IJ.log(getDateTime()+ " started Transform_Multiple_Vstacks with crop mode:" + crop_mode + " directory mode:" + directory_mode );
		
		for(int i=0;i<inputChannels_files.length;i++){
			if (bool_outputSources[i]){		
				File output_channel_file=new File(output_dir + inputChannels_files[i].getName() + File.separator);
				if (!output_channel_file.exists()) output_channel_file.mkdirs();
				if(crop_mode){
					if(directory_mode){
						if(!Transform_Virtual_Stack_FCC.exec_crop(
								inputChannels_files[i].getAbsolutePath() + File.separator,
								output_channel_file.getAbsolutePath() + File.separator,
								transf_dir,
								tempcrop_dir)) IJ.error("failed to transform " + inputChannels_files[i].getAbsolutePath() );
					}
					else{
						if(!Transform_Virtual_Stack_FCC.exec_crop_singletransform(
								inputChannels_files[i].getAbsolutePath() + File.separator,
								output_channel_file.getAbsolutePath() + File.separator,
								transf_file,
								tempcrop_dir)) IJ.error("failed to transform " + inputChannels_files[i].getAbsolutePath() );
					}
				}
				else{
					if(directory_mode){
						if(!Transform_Virtual_Stack_FCC.exec_expand(
								inputChannels_files[i].getAbsolutePath() + File.separator, 
								output_channel_file.getAbsolutePath() + File.separator,
								transf_dir)) IJ.error("failed to transform " + inputChannels_files[i].getAbsolutePath() );
					}
					else{
						if(!Transform_Virtual_Stack_FCC.exec_expand_singletransform(
								inputChannels_files[i].getAbsolutePath() + File.separator,  
								output_channel_file.getAbsolutePath() + File.separator,
								transf_file)) IJ.error("failed to transform " + inputChannels_files[i].getAbsolutePath() );
					}
				}
			}
		}
	
		IJ.log(getDateTime()+ " ended Transform_Multiple_VStacks");
		return true;
	}

	static public File[] ListSubDirectories(String directory){
			
			File dir = new File(directory);

			// This filter only returns directories
			FileFilter fileFilter = new FileFilter() {
			    public boolean accept(File file) {
			        return file.isDirectory();
			    }
			};
		
			File[] files = dir.listFiles(fileFilter);

			Arrays.sort(files);
			return files;
			
		}
	static public File[] ListFilesOfType(String directory,final String exts){
		
		File dir = new File(directory);

		// This filter only returns directories
		FileFilter fileFilter = new FileFilter() {
			public boolean accept(File file) 
			{
				int idot = file.getName().lastIndexOf('.');
				if (-1 == idot) return false;
				return exts.contains(file.getName().substring(idot).toLowerCase());
			}
		};
	
		File[] files = dir.listFiles(fileFilter);

		Arrays.sort(files);
		return files;
		
	}
	                   
	               
	
	
	private static String getDateTime() {
	        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss yyyy/MM/dd ");
	        Date date = new Date();
	        return dateFormat.format(date);
	    }
	 public static void copyFile(File sourceFile, File destFile) throws IOException {
		 if(!destFile.exists()) {
		  destFile.createNewFile();
		 }

		 FileChannel source = null;
		 FileChannel destination = null;
		 try {
		  source = new FileInputStream(sourceFile).getChannel();
		  destination = new FileOutputStream(destFile).getChannel();
		  destination.transferFrom(source, 0, source.size());
		 }
		 finally {
		  if(source != null) {
		   source.close();
		  }
		  if(destination != null) {
		   destination.close();
		  }
		}
	 }
}