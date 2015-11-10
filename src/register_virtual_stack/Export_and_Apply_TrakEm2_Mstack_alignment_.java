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
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.lang.Class;

import ini.trakem2.ControlWindow;
import ini.trakem2.Project;
import ini.trakem2.display.Ball;
import ini.trakem2.display.Displayable;
import ini.trakem2.display.Patch;
import ini.trakem2.display.Display;
import ini.trakem2.display.Display3D;
import ini.trakem2.display.Layer;
import ini.trakem2.display.LayerSet;
import ini.trakem2.tree.ProjectThing;
import ini.trakem2.utils.Utils;
import mpicbg.trakem2.transform.*;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;


//import Register_Virtual_Stack_FCC
/**
 * @author Forrest Collman <forrest.collman@gmail.com> 2011
 * @version 0.1
 */

public class Export_and_Apply_TrakEm2_Mstack_alignment_ implements PlugIn
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
		final Project project = ControlWindow.getActive();
		final LayerSet ls = project.getRootLayerSet();
		
		int numlayers=ls.size();	    
	    Map<File,File> ChannelToRoot = new HashMap<File,File>();
	    CoordinateTransform [] transform_list = new CoordinateTransform[numlayers];
		for (int i=0;i<numlayers;i++){
			Layer layer = ls.getLayer(i);
		   
	        ArrayList<Displayable> patches=layer.getDisplayables();
	        Patch thepatch = (Patch) patches.get(0);
	        File file1=new File(thepatch.getImageFilePath());
	        
	        File chPath = file1.getParentFile();
	        final String[] src_names = Transform_Virtual_Stack_FCC.listFilesOfType(chPath.getAbsolutePath(),".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
	        int file_index = Arrays.asList(src_names).indexOf(file1.getName());
	        
	        File transformPath;
	        File user_roothPath;
	        if (!ChannelToRoot.containsKey(chPath)){
	        	File sessionPath = chPath.getParentFile();
	        	File rootPath = sessionPath.getParentFile();
	        	File [] SubDirs = ListSubDirectories(chPath);
	        	boolean isRoot=false;
	        	
	        	for (int j=0;j<SubDirs.length;j++){
	        		if (SubDirs[i].getName()=="exported_sessions"){
	        			isRoot=true;
	        			break;
	        		}
	        	}
	        	if (!isRoot){
	        		rootPath=rootPath.getParentFile();
	        	}
	        	GenericDialogPlus gd = new GenericDialogPlus("Export Directory");
	        	gd.addMessage("root directory for:" + chPath.getAbsolutePath());
	        	gd.addDirectoryField("Root Directory (transforms in Root/transforms)", rootPath.getAbsolutePath(), 50);	
	        	gd.showDialog();
	    		// Exit when canceled
	    		if (gd.wasCanceled()) 
	    			return;
	    		String userOutPath= gd.getNextString();
	        	user_roothPath=new File(userOutPath);	        	
		        ChannelToRoot.put(chPath, user_roothPath);
	        }
	        else
	        {
	        	user_roothPath=ChannelToRoot.get(chPath);
	        }
	        transformPath = new File(user_roothPath,"transforms");
	        if (!transformPath.exists()){
	        	boolean result = transformPath.mkdirs();
	        }
	 	        
	        File transformFile = new File(transformPath,String.format("%04d.xml",file_index));
	        
	    	FileWriter fw;
			try {
				fw = new FileWriter(transformFile.toString());	
				if ( patches.size() > 0 ){
					CoordinateTransform ct = combineTransform(thepatch);
					transform_list[i]=ct;
					fw.write(ct.toXML( "" ));
	    		}
	    	}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
		}
		GenericDialogPlus gd2 = new GenericDialogPlus("To Merge or Not to Merge");
		String [] choices = {"yes","no"};
		gd2.addChoice("Merge Multiple Ribbons?",choices,choices[0]);
		gd2.showDialog();
		if (gd2.wasCanceled()) 
			return;
		boolean domerge = gd2.getNextChoice()=="yes";
		File outputDirFile;
		if (domerge){
			// then we need to put all the files into a single merged dataset
			GenericDialogPlus gd3 = new GenericDialogPlus("Choose Output Directory");
			gd3.addDirectoryField("output directory","");
			gd3.showDialog();
			if (gd3.wasCanceled()) 
				return;
			String outputDirectory = gd3.getNextString();
			outputDirFile = new File(outputDirectory);
			if (!outputDirFile.exists()){
				outputDirFile.mkdirs();	
			}	
		}
		else {
			outputDirFile = new File("");
		}
		
		
		ExecutorService exe = Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors()/2));
	
		// Apply transform	
		ArrayList<Future<Boolean>> save_job = new ArrayList <Future<Boolean>>();
	
		int numJobs =0;
		for (int i=0;i<numlayers;i++){
			Layer layer = ls.getLayer(i);
			   
	        ArrayList<Displayable> patches=layer.getDisplayables();
	        Patch thepatch = (Patch) patches.get(0);
	        File file1=new File(thepatch.getImageFilePath());
	        
	        File chPath = file1.getParentFile();
	        File sessionPath = chPath.getParentFile();
	        final String[] src_names = Transform_Virtual_Stack_FCC.listFilesOfType(chPath.getAbsolutePath(),".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
	        int file_index = Arrays.asList(src_names).indexOf(file1.getName());
	        
	        File [] channelDirs = ListSubDirectories(sessionPath);
	        
	        for (int k=0;k<channelDirs.length;k++){
	        	final String[] filenames = Transform_Virtual_Stack_FCC.listFilesOfType(channelDirs[k].getAbsolutePath(),".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
				File sourceFile= new File(channelDirs[k],filenames[file_index]);
				File target_dirFile;
				if (!domerge) {
					outputDirFile = new File(ChannelToRoot.get(chPath),"aligned_channels");
				}
				target_dirFile = new File(outputDirFile,channelDirs[k].getName());	
	        	if (!target_dirFile.exists()) target_dirFile.mkdirs();
	        	File targetFile;
	        	if (domerge){
	        		targetFile = new File(target_dirFile,channelDirs[k].getName() + "_S" + String.format("%04d.tif",i));
	        	}
	        	else {
	        		targetFile = new File(target_dirFile,sourceFile.getName());
	        	}
				save_job.add(
						exe.submit(
								Register_Virtual_Stack_FCC.applyTransformCropAndSave(
										sourceFile,
										targetFile,
										transform_list[i],
										true,
										32,
										(int) layer.getLayerWidth(),
										(int) layer.getLayerHeight())
									)
							);
				numJobs++;
	        }

	
		}


		// Wait for the intermediate output files to be saved
		int ind = 0;
		for (Iterator<Future<Boolean>> it = save_job.iterator(); it.hasNext(); )
		{
			ind++;
			Boolean saved_file = null;
			try{
				IJ.showStatus("Applying transform " + (ind) + "/" + numJobs);
				saved_file = it.next().get();
				it.remove(); // so list doesn't build up anywhere with Callable-s that have been called already.				
				System.gc();
			} catch (InterruptedException e) {
				IJ.error("Interruption exception!");
				e.printStackTrace();
				exe.shutdownNow();
				return;
			} catch (ExecutionException e) {
				IJ.error("Execution exception!");
				e.printStackTrace();
				exe.shutdownNow();
				return;
			}
		
			
		}
		
		// Shut executor service down to allow garbage collection
		exe.shutdown();

		save_job = null;

		IJ.showStatus("Done!");
	}
	    	
//		GenericDialogPlus gd = new GenericDialogPlus("Transform Multiple Virtual Stacks");
//		gd.addDirectoryField("Input directory", inputDirectory, 50);	
//		gd.addDirectoryField("Output directory", outputDirectory, 50);
//
//		//add instructional panel
//		Panel instructions=new Panel();
//		instructions.setLayout(new BoxLayout(instructions,BoxLayout.PAGE_AXIS));
//		instructions.add(new Label("Select either a directory of transforms, or a single transform file."));
//		instructions.add(new Label("The directory option will expect that there are a set of" +
//				".xml transform files that should be applied to the corresponding set of images in the input directory."));
//		instructions.add(new Label("The single transform file option will apply a single .xml transform file to every image in the input directory"));
//		gd.addPanel(instructions);
//		
//		gd.addDirectoryField("Transforms directory", transformsDirectory, 50);
//		gd.addFileField("Transforms file", transformFile, 50);
//		gd.addCheckbox("interpolate", interpolate );
//		gd.addNumericField( "resolution :", resolutionOutput, 0 );
//		Panel instructions2=new Panel();
//		
//		//add instructional panel
//		instructions2.setLayout(new BoxLayout(instructions2,BoxLayout.PAGE_AXIS));
//		instructions2.add(new Label("Input a reference directory if you wish to have the resulting "));
//		instructions2.add(new Label("tranformed files to be cropped to a specific set of sizes"));
//		instructions2.add(new Label("the output images will be cropped to the size of the corresponding image in the reference directory"));
//		gd.addPanel(instructions2);
//		
//		gd.addDirectoryField("Reference directory", templateDirectory,50);
//		
//		gd.showDialog();
//		// Exit when canceled
//		if (gd.wasCanceled()) 
//			return;
//		
//		inputDirectory = gd.getNextString();
//		outputDirectory = gd.getNextString();
//		transformsDirectory = gd.getNextString();
//		transformFile =gd.getNextString();
//		interpolate= gd.getNextBoolean();
//		resolutionOutput=(int) gd.getNextNumber();
//		templateDirectory=gd.getNextString();
//		
//		
//		//make sure a input directory was given and that it exists
//		String input_dir = inputDirectory;
//		if (null == input_dir) 
//		{
//			IJ.error("Error: No input directory was provided.");
//			return;
//		}
//		input_dir = Register_Virtual_Stack_FCC.cleanDirString(input_dir);
//		final File input_dir_file=new File(input_dir);
//		if(!input_dir_file.exists()){
//			IJ.error("Error: input directory does not exist");
//			return;
//		}
//		
//		//make sure output directory was given 
//		String output_dir = outputDirectory;
//		if (null == output_dir) 
//		{
//			IJ.error("Error: No output directory was provided.");
//			return;
//		}
//		//make the directory exist if it doesn't exist
//		output_dir = Register_Virtual_Stack_FCC.cleanDirString(output_dir);
//		final File output_dir_file=new File(output_dir);
//		if(!output_dir_file.exists()) output_dir_file.mkdirs();
//	
//		String transf_dir = transformsDirectory;
//		if(!transf_dir.isEmpty()) transf_dir = Register_Virtual_Stack_FCC.cleanDirString(transf_dir);	
//		String transf_file = transformFile;		
//		if (transf_file == null && transf_dir == null)
//		{
//			IJ.error("no transform file or transforms directory given");
//			return;
//		}
//		String tempcrop_dir = templateDirectory;
//		if (tempcrop_dir.length()>0)
//		{
//			tempcrop_dir = Register_Virtual_Stack_FCC.cleanDirString(tempcrop_dir);
//		}
//	
//		
//		File[] inputChannels_files=ListSubDirectories(input_dir);
//		
//		int numinputs=inputChannels_files.length;
//		
//		Panel controlPanel = new Panel(new GridBagLayout());
//		GridBagConstraints c = new GridBagConstraints();
//		c.anchor=GridBagConstraints.LINE_START;
//		c.insets=new Insets(0,0,2,5);
//		c.ipadx=0;
//		c.gridx=0;
//		c.gridy=0;
//		
//		controlPanel.add(new JLabel("Input Channels"),c);		
//		channelCheckBoxes= new JCheckBox[numinputs];
//		for(int i=0;i<numinputs;i++){
//			channelCheckBoxes[i]=new JCheckBox(inputChannels_files[i].getName());	
//			defaultBackColor=channelCheckBoxes[0].getBackground();
//			channelCheckBoxes[i].setSelected(true);
//			c.gridx=1;
//			c.gridy=i;
//			controlPanel.add(channelCheckBoxes[i],c);
//		}
//	
//		GenericDialogPlus gd2 = new GenericDialogPlus("Transform Multiple Virtual Stacks");
//		gd2.setLayout(new FlowLayout());
//		gd2.setSize(640,480);
//		gd2.addPanel(controlPanel);		
//		gd2.showDialog();	
//		
//		// Exit when canceled
//		if (gd2.wasCanceled()) 
//			return;
////		
//		boolean []bool_outputSources=new boolean[numinputs];
//		for(int i=0;i<numinputs;i++){
//				bool_outputSources[i]=channelCheckBoxes[i].isSelected();
//		}
//	
//	
//		try {
//			exec(inputChannels_files,output_dir,transf_dir,transf_file,tempcrop_dir,bool_outputSources);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		

 static public CoordinateTransform combineTransform(Patch patch )
		{
			CoordinateTransform ct = patch.getCoordinateTransform();
			if ( ct == null ) {
				AffineModel2D affine = new AffineModel2D();
				affine.set( patch.getAffineTransform() );
				ct = affine;
			}
			else
			{
				Rectangle box = patch.getCoordinateTransformBoundingBox();
				AffineTransform at = patch.getAffineTransformCopy();
				at.translate( -box.x, -box.y );
				AffineModel2D affine = new AffineModel2D();
				affine.set( at );
						
				CoordinateTransformList ctl = new CoordinateTransformList();
				ctl.add( ct );
				ctl.add( affine );		
				ct = ctl;
			}
			return ct;
		}

		
	static public File[] ListSubDirectories(File dir){
			
		

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