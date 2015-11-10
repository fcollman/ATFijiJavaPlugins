package register_virtual_stack;
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
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import java.awt.Insets;

import java.awt.Panel;
import java.io.File;
import java.io.FileFilter;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;


import register_virtual_stack.Register_Virtual_Stack_FCC.Param;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;
/**
 * @author Forrest Collman <forrest.collman@gmail.com> 2011
 * @version 0.1
 */
public class Register_Two_MultiChannel_VStacks implements PlugIn 
{

	/** transforms directory **/
	public static String targetDirectory="V:\\Mouse\\KDM-SYN-110405\\rawsessions\\stain1";
	/** source directory **/
	public static String sourceDirectory="V:\\Mouse\\KDM-SYN-110405\\rawsessions\\stain2";
	/** output directory **/
	public static String outputDirectory="V:\\Mouse\\KDM-SYN-110405\\registeredsessions\\stain2";
	/** string describing subset of images to analyze (i.e. 1,2,5-10) zero indexed **/
	public static String subsliceString="";
	
	public void run(String arg) 
	{
		GenericDialogPlus gd = new GenericDialogPlus("Register_Two_MultiChannel_VStacks");
		gd.addDirectoryField("Reference directory", targetDirectory, 50);
		gd.addDirectoryField("Input directory", sourceDirectory, 50);
		gd.addDirectoryField("Output directory", outputDirectory, 50);
		gd.addStringField("Slices to analyze i.e. (0,5,6-10) (leave blank for all)",subsliceString);
		gd.showDialog();
		
		// Exit when canceled
		if (gd.wasCanceled()) 
			return;
		
		targetDirectory = gd.getNextString();
		sourceDirectory = gd.getNextString();
		outputDirectory = gd.getNextString();
		subsliceString = gd.getNextString();
		//make sure that a source directory was given and that it exists
		String source_dir = sourceDirectory;
		if (null == source_dir) 
		{
			IJ.error("Error: No source directory was provided.");
			return;
		}
		source_dir = Register_Virtual_Stack_FCC.cleanDirString(source_dir);
		final File source_dir_file=new File(source_dir);
		if(!source_dir_file.exists()){
			IJ.error("Error: Source directory does not exist");
			return;
		}
		
		//make sure a target directory was given and that it exists
		String target_dir = targetDirectory;
		if (null == target_dir) 
		{
			IJ.error("Error: No reference directory was provided.");
			return;
		}
		target_dir = Register_Virtual_Stack_FCC.cleanDirString(target_dir);
		final File target_dir_file=new File(target_dir);
		if(!target_dir_file.exists()){
			IJ.error("Error: reference directory does not exist");
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
		
		final File[] targetChannels_files=ListSubDirectories(target_dir);
		final File[] sourceChannels_files=ListSubDirectories(source_dir);
		final File[] outputChannels_files=ListSubDirectories(output_dir);
		
		int numtargets=targetChannels_files.length;
		int numsources=sourceChannels_files.length;
		int numoutputs=outputChannels_files.length;
		
		//Group the radio buttons.
		Panel controlPanel = new Panel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor=GridBagConstraints.LINE_START;
		c.insets=new Insets(0,0,2,5);
		c.ipadx=0;
		c.gridx=0;
		c.gridy=0;
		c.gridwidth=2;
		//c.fill = GridBagConstraints.HORIZONTAL;
		controlPanel.add(new JLabel("Target Channels"),c);
		c.gridwidth=1;
		c.gridy=1;
		//controlPanel.add(new JLabel("Out?"),c);
	    ButtonGroup targetGroup = new ButtonGroup();
	    JRadioButton []targetButtons=new JRadioButton[numtargets];
	    JCheckBox []targetBoxes = new JCheckBox[numtargets];
	    for (int i=0 ; i<numtargets;i++){
	    	targetButtons[i]=new JRadioButton(targetChannels_files[i].getName());
	    	targetButtons[i].setName(targetChannels_files[i].getName());
	    	targetButtons[i].setActionCommand(targetChannels_files[i].getName());
	    	targetBoxes[i]=new JCheckBox();
	    	targetBoxes[i].setSelected(true);
	    	//targetBoxes[i].setSize(15, 15);
	    	c.gridx=0;
	    	c.gridy=i+2;
	    	c.weightx=.1;
	    	//controlPanel.add(targetBoxes[i],c);
	    	c.gridx=0;
	    	c.weightx=.9;
	    	controlPanel.add(targetButtons[i],c);
	    	targetGroup.add(targetButtons[i]);
	    	
	    }
		//targetPanel.setSize(150,500);
	   
	    c.gridx=3;
		c.gridy=0;
		c.gridwidth=2;
		
	    controlPanel.add(new JLabel("Source Channels"),c);
	    c.gridwidth=1;
	    c.gridy=1;
		controlPanel.add(new JLabel("Out?"),c);
	    ButtonGroup sourceGroup = new ButtonGroup();
	    JRadioButton []sourceButtons=new JRadioButton[numsources];
	    JCheckBox []sourceBoxes = new JCheckBox[numsources];
	    for (int i=0 ; i<numsources;i++){
	    	sourceButtons[i]=new JRadioButton(sourceChannels_files[i].getName());
	    	sourceButtons[i].setName(sourceChannels_files[i].getName());
	    	sourceButtons[i].setActionCommand(sourceChannels_files[i].getName());
	    	sourceBoxes[i]=new JCheckBox();
	    	sourceBoxes[i].setSelected(true);
	    	c.gridx=3;
	    	c.gridy=i+2;
	    	c.weightx=.1;
	    	controlPanel.add(sourceBoxes[i],c);	
	    	c.gridx=4;
	    	c.gridy=i+2;
	    	c.weightx=.9;
	    	controlPanel.add(sourceButtons[i],c);
	    	sourceGroup.add(sourceButtons[i]);
	    }
	  
	    c.gridx=5;
		c.gridy=0;
		c.gridwidth=1;
		c.fill = GridBagConstraints.HORIZONTAL;
	    controlPanel.add(new JLabel("Existing Output Channels"),c);
	    for (int i=0 ; i<numoutputs;i++){    	   	
	    	c.gridy=i+2;
	    	c.weightx=.9;
	    	controlPanel.add(new JLabel(outputChannels_files[i].getName()),c);    	
	    }
	  
	    
	    int []selected=new int[2];
	    AutoSelectCorrespondance(targetButtons,sourceButtons);
	  //  targetButtons[selected[0]].setSelected(true);
	  //  sourceButtons[selected[1]].setSelected(true);
	    
	  
	    
	    
		GenericDialogPlus gd2 = new GenericDialogPlus("Register_Two_MultiChannel_VStacks");
		gd2.setLayout(new FlowLayout());
		gd2.setSize(640,480);
		gd2.addPanel(controlPanel);
		gd2.addCheckbox("Advanced setup", false);	
		
		gd2.showDialog();
		// Exit when canceled
		if (gd2.wasCanceled()) 
			return;
			
		boolean []bool_outputSource=new boolean[numsources];
		for(int i=0;i<numsources;i++){
		    	bool_outputSource[i]=sourceBoxes[i].isSelected();
		    	if (sourceButtons[i].isSelected()) selected[1]=i;
		}
		for(int i=0;i<numtargets;i++){	    	
	    	if (targetButtons[i].isSelected()) selected[1]=i;
		}		    
	
		boolean advanced=gd2.getNextBoolean();
		Param p = new Param();
		Param.featuresModelIndex = 3;
		Param.registrationModelIndex = 3;
		// Show parameter dialogs when advanced option is checked
		if (advanced && !p.showDialog())
			return;
		
		try {
			exec(targetChannels_files[selected[1]],sourceChannels_files,output_dir,selected[0],bool_outputSource,p,subsliceString);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			IJ.error("failed to execute Register Imaging Sessions");
			e.printStackTrace();
			
		}
		
		
	}
	
	 //overloaded function to preserve functionality of not specifying a substring
	 static public boolean exec(
				File targetChannel,
				File[] sourceChannels_files,
				String output_dir,
				int selected,
				boolean[] do_output,
				Param p) throws IOException {	 
		 return exec(targetChannel,sourceChannels_files,output_dir,selected,do_output,p,"");
	 }

	/**
	 * Takes the imaging session listed as a sequence of directories in sourceChannels_files
	 * and register it to the imaging session listed in targetChannels_files 
	 * using the target channel index specified in selected[0] and the source channel index specified in selected[1]
	 * @param targetChannels_files folder with the target channel folders to register
	 * @param sourceChannels_files folder with the source channel folders to register
	 * @param output_dir folder to save the resulting imaging session, using the same subfolder names
	 * @param selected an int[2] which contains the index of the corresponding channels to do the registration on
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	 static public boolean exec(
				File targetChannel,
				File[] sourceChannels_files,
				String output_dir,
				int selected,
				boolean[] do_output,
				Param p,
				String subString) throws IOException {
			
			//make the directory exist if it doesn't exist
			output_dir = Register_Virtual_Stack_FCC.cleanDirString(output_dir);
			final File output_dir_file=new File(output_dir);
			if(!output_dir_file.exists()) output_dir_file.mkdirs();
	
			//make the output directories
			final File [] outputChannels_files = new File[sourceChannels_files.length];
			for (int i=0;i<sourceChannels_files.length;i++){
				outputChannels_files[i]=new File(output_dir+sourceChannels_files[i].getName());
				outputChannels_files[i].mkdir();
			}
						
			IJ.log(String.format("substring length:%d",subString.length()));
			//register the two corresponding channels and saving the transforms in the right output directory
			Register_Virtual_Stack_FCC.exec(
					sourceChannels_files[selected].getAbsolutePath() + File.separator, //take from source dir
					outputChannels_files[selected].getAbsolutePath() + File.separator, //place in output dir
					outputChannels_files[selected].getAbsolutePath() + File.separator, //save transforms in the output
					targetChannel.getAbsolutePath() + File.separator, //register to target directory
					null,   //don't specify a reference file for registration mode
					p,  //pass on the parameters specified
					false,  //non-shrink 
					true,   //do registration mode
					false, //suppress output (do and save the 
					subString); //do the substring of files; 

			
			if (do_output==null){
				IJ.log("transforming all other channels");
				//loop over the channels and apply the transform
				for (int i=0;i<sourceChannels_files.length;i++){				
						IJ.log(getDateTime()+" transforming "+ sourceChannels_files[i].getAbsolutePath());
						if(!Transform_Virtual_Stack_FCC.exec_crop(
								sourceChannels_files[i].getAbsolutePath()+ File.separator,
								outputChannels_files[i].getAbsolutePath()+ File.separator, 
								outputChannels_files[selected].getAbsolutePath()+ File.separator,
								targetChannel.getAbsolutePath()+ File.separator,
								subString))
						{
							IJ.error("Failed to transform source channel:"+sourceChannels_files[i].getAbsolutePath());
							return false;
						}			
				}
			}
			else{
				IJ.log("transforming selected outputs");
				//loop over the other channels and apply the transform
				for (int i=0;i<sourceChannels_files.length;i++){	
					if (do_output[i]){			
						IJ.log(getDateTime()+" transforming "+ sourceChannels_files[i].getAbsolutePath());
						if(!Transform_Virtual_Stack_FCC.exec_crop(
								sourceChannels_files[i].getAbsolutePath()+ File.separator,
								outputChannels_files[i].getAbsolutePath()+ File.separator, 
								outputChannels_files[selected].getAbsolutePath()+ File.separator,
								targetChannel.getAbsolutePath()+ File.separator,
								subString))
						{
							IJ.error("Failed to transform source channel:"+sourceChannels_files[i].getAbsolutePath());
							return false;
						}
		
					}
				}
			}
			return true;
		}

	public static void AutoSelectCorrespondance(JRadioButton [] targetchannels,JRadioButton[] sourcechannels){
		
		targetchannels[0].setSelected(true);
		sourcechannels[0].setSelected(true);
		
		for (int i=0;i<targetchannels.length;i++){
			if (targetchannels[i].getText().toLowerCase().contains("dapi")){
				targetchannels[i].setSelected(true);
			}
		}
		for (int i=0;i<sourcechannels.length;i++){
			if (sourcechannels[i].getText().toLowerCase().contains("dapi")){
				sourcechannels[i].setSelected(true);
			}
		}
		
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

	 private static String getDateTime() {
	        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss yyyy/MM/dd ");
	        Date date = new Date();
	        return dateFormat.format(date);
	    }
}