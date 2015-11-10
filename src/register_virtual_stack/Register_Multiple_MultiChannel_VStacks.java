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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import register_virtual_stack.Register_Virtual_Stack_FCC.Param;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;
/**
 * @author Forrest Collman <forrest.collman@gmail.com> 2011
 * @version 0.1
 */
public class Register_Multiple_MultiChannel_VStacks implements PlugIn, ItemListener
{

	JCheckBox [][]channelCheckBoxes;
	JRadioButton []sessionRadioButtons;
	JCheckBox []sessionCheckBoxes;
	JRadioButton [][]channelRadioButtons;
	Color defaultBackColor;
	
	/** transforms directory **/
	public static String inputDirectory="";

	/** output directory **/
	public static String outputDirectory="";
	
	/** string describing subset of images to analyze (i.e. 1,2,5-10) zero indexed **/
	public static String subsliceString="";
	
	public void run(String arg) 
	{
		GenericDialogPlus gd = new GenericDialogPlus("Register Multiple MultiChannel Virtual Stacks");
		gd.addDirectoryField("Input directory", inputDirectory, 50);	
		gd.addDirectoryField("Output directory", outputDirectory, 50);
		
		gd.showDialog();
		
		// Exit when canceled
		if (gd.wasCanceled()) 
			return;
		
		inputDirectory = gd.getNextString();
		outputDirectory = gd.getNextString();

		
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
	
		
		File[] inputSessions_files=ListSubDirectories(input_dir);
		
		
		int numinputs=inputSessions_files.length;
		
		File[][] inputChannels_files=new File[numinputs][];
		channelRadioButtons=new JRadioButton[numinputs][];
		channelCheckBoxes= new JCheckBox[numinputs][];
		for(int i=0;i<numinputs;i++){
			String subdir_string;	
			subdir_string=Register_Virtual_Stack_FCC.cleanDirString(inputSessions_files[i].getAbsolutePath());
			inputChannels_files[i]=ListSubDirectories(subdir_string);	
			channelRadioButtons[i]=new JRadioButton[inputChannels_files[i].length];
			channelCheckBoxes[i]=new JCheckBox[inputChannels_files[i].length];
		}	
		
		//Group the radio buttons.
		int rowbreak=6;
		Panel controlPanel = new Panel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor=GridBagConstraints.LINE_START;
		c.insets=new Insets(0,0,2,5);
		c.ipadx=0;
		c.gridx=0;
		c.gridy=0;
		controlPanel.add(new JLabel("Reference Session"),c);
		c.gridy=2;		
		controlPanel.add(new JLabel("Output Channels"),c);
		c.gridy=3;
		controlPanel.add(new JLabel("With Checks"),c);
		c.gridx=0;
		c.gridy=1;
		c.gridwidth=(int) Math.ceil(numinputs/rowbreak);
		JSeparator jsHoriz=new JSeparator(SwingConstants.HORIZONTAL);
		jsHoriz.setSize(50,5);
		controlPanel.add(jsHoriz,c);
		controlPanel.add(Box.createVerticalStrut(5),c);
		c.gridwidth=1;
		
		ButtonGroup sessionButtonGroup = new ButtonGroup();
		sessionRadioButtons=new JRadioButton[numinputs];
		ButtonGroup []channelButtonGroups= new ButtonGroup[numinputs];
		sessionCheckBoxes = new JCheckBox[numinputs];
		
		
		
		for(int i=0;i<numinputs;i++){		
			sessionRadioButtons[i]=new JRadioButton(inputSessions_files[i].getName());
			defaultBackColor=sessionRadioButtons[i].getBackground();
			sessionRadioButtons[i].addItemListener(this);
			sessionCheckBoxes[i]=new JCheckBox();
			sessionCheckBoxes[i].setSelected(true);
			sessionCheckBoxes[i].addItemListener(this);
			
			int row=(int) Math.floor(i/rowbreak);
			int col=i%rowbreak;
			if (col==0 && row >0){
				c.gridy=8*row;
				c.gridx=0;
				controlPanel.add(new JLabel("new row"),c);
			}
			c.gridx=2*col+1;
			c.gridy=9*row;
			c.weightx=.1;
			controlPanel.add(sessionCheckBoxes[i],c);
			
			c.gridx=2*col+2;
			c.weightx=.9;
			sessionButtonGroup.add(sessionRadioButtons[i]);
			controlPanel.add(sessionRadioButtons[i],c);
					
			channelButtonGroups[i]=new ButtonGroup();
			for(int j=0;j<inputChannels_files[i].length;j++){
				
				channelRadioButtons[i][j]=new JRadioButton(inputChannels_files[i][j].getName());
				channelCheckBoxes[i][j]=new JCheckBox();
				channelCheckBoxes[i][j].setSelected(true);
				channelButtonGroups[i].add(channelRadioButtons[i][j]);
				c.gridx=2*col+1;
				c.gridy=9*row+j+2;
				c.weightx=.1;
				controlPanel.add(channelCheckBoxes[i][j],c);
				c.gridx=2*col+2;
				c.weightx=.9;
				controlPanel.add(channelRadioButtons[i][j],c);	
			}
		}
		
		sessionRadioButtons[0].setSelected(true);
		
		for(int i=1;i<numinputs;i++){
			Register_Two_MultiChannel_VStacks.AutoSelectCorrespondance(
					channelRadioButtons[0],channelRadioButtons[i]);
		}
		
		GenericDialogPlus gd2 = new GenericDialogPlus("Register Imaging Sessions VS");
		//gd2.setLayout(new FlowLayout());
		gd2.setSize(640,480);
		gd2.addPanel(controlPanel);
		gd2.addStringField("Optional subslices (0,1,4-8)",subsliceString);
		gd2.addCheckbox("Advanced", false);	
		gd2.showDialog();	
		
		// Exit when canceled
		if (gd2.wasCanceled()) 
			return;
		
		subsliceString = gd2.getNextString();
		boolean advanced=gd2.getNextBoolean();
		
		Param p = new Param();
		Param.featuresModelIndex = 3;
		Param.registrationModelIndex = 3;
		
		// Show parameter dialogs when advanced option is checked
		if (advanced && !p.showDialog())
			return;
		
		boolean [][]bool_outputSources=new boolean[numinputs][];
		boolean []bool_analyzeInputs=new boolean[numinputs];
		int referenceSession=0;
		int []referenceChannels=new int[numinputs];
		for(int i=0;i<numinputs;i++){
				bool_analyzeInputs[i]=sessionCheckBoxes[i].isSelected();
				bool_outputSources[i]=new boolean[inputChannels_files[i].length];
				for(int j=0;j<inputChannels_files[i].length;j++){
					if (channelRadioButtons[i][j].isSelected()) referenceChannels[i]=j;
					bool_outputSources[i][j]=channelCheckBoxes[i][j].isSelected();
				}
				if (sessionRadioButtons[i].isSelected()) referenceSession=i;
		}
		
		try {
			exec(inputSessions_files,
					inputChannels_files,
					output_dir,
					referenceSession,
					referenceChannels,
					bool_analyzeInputs,
					bool_outputSources,
					p,
					subsliceString);
	} catch (IOException e) {
			
			IJ.error("failed to execute Register Multiple Multi-Channel VStacks");
			e.printStackTrace();
			
		}
		
		
	}
	public void exec(File[] inputSessions_files,
			File [][] inputChannels_files,
			String output_dir,
			int referenceSession, 
			int[] referenceChannels,
			boolean[] bool_analyzeInputs,
			boolean[][] bool_outputSources,
			Param p) {
			try {
				exec(inputSessions_files,inputChannels_files,output_dir,referenceSession,referenceChannels,bool_analyzeInputs,bool_outputSources,p,"");
			}
			catch (IOException e){
				IJ.error("failed to execute Register Multiple Multi-Channel VStacks");
				e.printStackTrace();
			}
	}
	
	/**
	 * Takes the imaging session listed as a sequence of directories in sourceChannels_files
	 * and register it to the imaging session listed in targetChannels_files 
	 * using the target channel index specified in selected[0] and the source channel index specified in selected[1]
	 * @param inputSessions_files array of File(s) with the input sessions to register
	 * @param output_dir string with output directory to put results in
	 * @param referenceSession index of session to use as the reference
	 * @param referenceChannels an array of indices specifying which channel
	 * in each session is the registered channel.  
	 * indexing assumes that the channel subfolders names are sorted using Arrays.sort()
	 * @param bool_analyzeInputs array of booleans specifying whether to include sessions in analysis or not
	 * @param bool_outputSources 2d array specifying whether each channel in various sessions should be output
	 * @param p - the Param object containing the SIFT parameters to use for this registration
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public void exec(File[] inputSessions_files,
			File [][] inputChannels_files,
			String output_dir,
			int referenceSession, 
			int[] referenceChannels,
			boolean[] bool_analyzeInputs,
			boolean[][] bool_outputSources,
			Param p,
			String subsliceString) throws IOException {
		// TODO Auto-generated method stub
		
		

		//this will register the non reference sessions and place their outputs in the output directory
		IJ.log(getDateTime()+ " started");
		for (int i=0;i<inputSessions_files.length;i++){
			if(i!=referenceSession && bool_analyzeInputs[i]){
				Register_Two_MultiChannel_VStacks.exec(
						inputChannels_files[referenceSession][referenceChannels[referenceSession]], 
						inputChannels_files[i],
						output_dir,
						referenceChannels[i],
						bool_outputSources[i],
						p,
						subsliceString);
			}
		}
	
		int numRefSessChannels=inputChannels_files[referenceSession].length;
				
		//if the reference session is selected, copy its selected channels straight over
		if (bool_analyzeInputs[referenceSession]){
			IJ.log(getDateTime()+ "copying reference session");
			//loop through the channels in the reference sessions
			for (int i=0;i<numRefSessChannels;i++){
				
				//if we are to output this channel
				if (bool_outputSources[referenceSession][i]){
					IJ.log(getDateTime()+ "copying channel:" + inputChannels_files[referenceSession][i].getName() );
					//if this output channel doesn't exist, make it.
					File outputChannel=new File(
							Register_Virtual_Stack_FCC.cleanDirString(output_dir)
							+ inputChannels_files[referenceSession][i].getName());
					if(!outputChannel.exists()) outputChannel.mkdirs();
					//list all the files in this sub directory
					File[] subFiles=inputChannels_files[referenceSession][i].listFiles();
					
					for (int j=0;j<subFiles.length;j++){
						IJ.showProgress(j,subFiles.length);
						File outFile=new File(outputChannel.getAbsolutePath() + File.separator
								+ subFiles[j].getName());			
						 copyFile(subFiles[j],outFile);
					}
					
				}
			}
		}
	
		IJ.log(getDateTime()+ " ended");
	}
	public void itemStateChanged(ItemEvent e) {
	    
	    Object source = e.getItemSelectable();

	    for (int i=0;i<sessionCheckBoxes.length;i++){
	    	 if (source == sessionCheckBoxes[i]){
	    		  if (e.getStateChange() == ItemEvent.DESELECTED){
	    			  for(int j=0;j<channelCheckBoxes[i].length;j++){
	    				  channelCheckBoxes[i][j].setVisible(false);
	    				  channelRadioButtons[i][j].setVisible(false);
	    			  }
	    		  }
	    		  else{
	    			  for(int j=0;j<channelCheckBoxes[i].length;j++){
	    				  channelCheckBoxes[i][j].setVisible(true);
	    				  channelRadioButtons[i][j].setVisible(true);
	    			  }
	    		  }
	    	 }
	    	 
	    	 if (source == sessionRadioButtons[i]){
	    		 if (e.getStateChange() == ItemEvent.DESELECTED){
	    			  for(int j=0;j<channelCheckBoxes[i].length;j++){
	    				  channelCheckBoxes[i][j].setBackground(defaultBackColor);
	    				  channelRadioButtons[i][j].setBackground(defaultBackColor);
	    				  channelRadioButtons[i][j].setForeground(Color.black);
	    				  
	    			  }
	    		  }
	    		  else{
	    			  for(int j=0;j<channelCheckBoxes[i].length;j++){
	    				  channelCheckBoxes[i][j].setBackground(Color.white);
	    				  channelRadioButtons[i][j].setBackground(Color.white);
	    				  channelRadioButtons[i][j].setForeground(Color.blue);
	    			  }
	    		  }
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
	private String getDateTime() {
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