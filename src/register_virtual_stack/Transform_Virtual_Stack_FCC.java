package register_virtual_stack;

/** 
 * Albert Cardona, Ignacio Arganda-Carreras and Stephan Saalfeld 2009. 
 * Edited to allow for a single transform to be applied across the stack by Forrest Collman 2011
 * also to allow a set of files to be provided as templates for cropping purposes by Forrest Collman 2011
 * This work released under the terms of the General Public License in its latest edition. 
 * */

import java.awt.Label;
import java.awt.Panel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.BoxLayout;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.models.CoordinateTransformMesh;



import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import mpicbg.util.Util;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.TiffDecoder;
import ij.plugin.PlugIn;

/** 
 * Fiji plugin to transform sequences of images in a concurrent (multi-thread) way.
 * <p>
 * <b>Requires</b>: 
 * <ul>
 * 		<li>Source folder: a directory with images, of any size and type (8, 16, 32-bit gray-scale or RGB color)</li>
 * 		<li>Transform folder: a directory with the transform files (from a <a target="_blank" href="http://pacific.mpi-cbg.de/wiki/Register_Virtual_Stack_Slices">Register_Virtual_Stack_Slices</a> execution). </li>
 * </ul>
 * <p>
 * <b>Performs</b>: transformation of the sequence of images by applying the transform files.
 * <p>
 * <b>Outputs</b>: the list of new images, one for slice, into a output directory as .tif files.
 * <p>
 * For a detailed documentation, please visit the plugin website at:
 * <p>
 * <A target="_blank" href="http://pacific.mpi-cbg.de/wiki/Transform_Virtual_Stack_Slices">http://pacific.mpi-cbg.de/wiki/Transform_Virtual_Stack_Slices</A>
 * 
 * @version 11/30/2009
 * @author Ignacio Arganda-Carreras (ignacio.arganda@gmail.com), Stephan Saalfeld and Albert Cardona, edited Forrest Collman (forrest.collman@gmail.com)
 */
public class  Transform_Virtual_Stack_FCC implements PlugIn 
{		
	/** source directory **/
	public static String sourceDirectory="";
	/** output directory **/
	public static String outputDirectory="";
	/** transforms directory **/
	public static String transformsDirectory="";
	public static String transformFile="";
	/** interpolate? **/
	public static String templateDirectory="";
	public static boolean interpolate=true;
	public static int resolutionOutput=128;
	public static boolean widthheight=false;
	public static boolean crop=false;
   
	public static int Width=0;
	public static int Height=0;
	
	
	//---------------------------------------------------------------------------------
	/**
	 * Plug-in run method
	 * 
	 * @param arg plug-in arguments
	 */
	public void run(String arg) 
	{
		GenericDialogPlus gd = new GenericDialogPlus("Transform Virtual Stack");

	
		gd.addDirectoryField("Input directory (contains sequence of images)", sourceDirectory, 50);
		gd.addDirectoryField("Output directory", outputDirectory, 50);
		
		//add instructional panel
		Panel instructions=new Panel();
		instructions.setLayout(new BoxLayout(instructions,BoxLayout.PAGE_AXIS));
		instructions.add(new Label("Select either a directory of transforms, or a single transform file."));
		instructions.add(new Label("The directory option will expect that there are a set of" +
				".xml transform files that should be applied to the corresponding set of images in the input directory."));
		instructions.add(new Label("The single transform file option will apply a single .xml transform file to each image in the input directory"));
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
		
		//gd.addCheckbox("input widthheight.txt",widthheight);
		
		gd.showDialog();
		
		// Exit when canceled
		if (gd.wasCanceled()) 
			return;
		
		sourceDirectory = gd.getNextString();
		outputDirectory = gd.getNextString();
		transformsDirectory = gd.getNextString();
		transformFile = gd.getNextString();
		interpolate = gd.getNextBoolean();
		resolutionOutput = ( int )gd.getNextNumber();	
		templateDirectory=gd.getNextString();
		
		
		String source_dir = sourceDirectory;
		if (null == source_dir) 
			return;
		source_dir = Register_Virtual_Stack_FCC.cleanDirString(source_dir);
		
		String output_dir = outputDirectory;
		if (null == output_dir) 
			return;
		output_dir =  Register_Virtual_Stack_FCC.cleanDirString(output_dir);

		String transf_dir = transformsDirectory;
		if(!transf_dir.isEmpty()) transf_dir = Register_Virtual_Stack_FCC.cleanDirString(transf_dir);	
		String transf_file = transformFile;
		
		if (transf_file == null && transf_dir == null)
		{
			IJ.error("no transform file or transforms directory given");
			return;
		}
		
		//figure out whether to run it in directory mode or not
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
		

		String tempcrop_dir = templateDirectory;
		if (!tempcrop_dir.isEmpty())
		{
			crop=true;
			tempcrop_dir = Register_Virtual_Stack_FCC.cleanDirString(tempcrop_dir);
		}
		
		
		IJ.log("crop_mode:"+crop + " directory_mode:" + directory_mode);
		// Execute transformation
		try {
			if (crop){
				if (directory_mode){
					exec_crop(source_dir, output_dir,transf_dir,tempcrop_dir);
				}
				else{
					exec_crop_singletransform(source_dir, output_dir,transf_file,tempcrop_dir);
				}
			}
			else {
				if (directory_mode){
					exec_expand(source_dir, output_dir, transf_dir);
				}
				else{
					exec_expand_singletransform(source_dir, output_dir, transf_file);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	//---------------------------------------------------------------------------------
	/**
	 * Transform images in the source directory applying transform files from a specific directory.
	 * Calculating the maximum size of the resulting transform and making each image equal to that size.
	 * @param source_dir folder with input (source) images.
	 * @param output_dir folder to store output (transformed) images.
	 * @param transf_dir folder with transform files.
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public static boolean exec_expand(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir) throws IOException 
	{
		// Get source file listing	
		final String[] src_names = listFilesOfType(source_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
		// Get transform file listing	
		final String[] transf_names =  listFilesOfType(transf_dir,".xml");
		
		// Check the number of input (source) files and transforms.
		if(transf_names.length != src_names.length)
		{
			IJ.error("The number of source and transform files must be equal!");
			return false;
		}
		
		
		// Read transforms and calculate the global bounds of that transform
		CoordinateTransform[] transforms = new CoordinateTransform[transf_names.length];
	
		final double[] min = new double[ 2 ];
		final double[] max = new double[ 2 ];		
		ImagePlus first = new ImagePlus(Register_Virtual_Stack_FCC.cleanDirString(source_dir)+src_names[0]);
		
		for(int i = 0; i < transf_names.length; i ++)
		{
			final double[] meshMin = new double[ 2 ];
			final double[] meshMax = new double[ 2 ];
			transforms[i] = readCoordinateTransform(transf_dir + transf_names[i]);
			if(transforms[i] == null)
			{
				IJ.error("Error when reading transform from file: " + transf_dir + transf_names[i]);
				return false;
			}
				
				final TransformMeshMapping< CoordinateTransformMesh > mapping = new TransformMeshMapping< CoordinateTransformMesh >( new CoordinateTransformMesh( transforms[i], 32, first.getWidth(), first.getHeight() ) );
				mapping.getTransform().bounds(meshMin, meshMax);
				Util.min( min, meshMin );
				Util.max( max, meshMax );

		}
		//calculate the height and width that will fit all the transforms
		//round seems to agree better with the original register virtual stack slices
		final int width = ( int )Math.round( max[ 0 ] - min[ 0 ] );
		final int height = ( int )Math.round( max[ 1 ] - min[ 1 ] );
		
		IJ.showStatus("Calculating expanded transformed images...");
		// Create transformed images
		if(Register_Virtual_Stack_FCC.createResults(source_dir, src_names, output_dir, null, transforms, interpolate,resolutionOutput,width,height) == false)
		{
			IJ.log("Error when creating transformed images");
			return false;
		}
		
		return true;
	}

	//---------------------------------------------------------------------------------
	/**
	 * Transform images in the source directory applying transform files from a specific directory.
	 * Calculating the maximum size of the resulting transform and making each image equal to that size.
	 * @param source_dir folder with input (source) images.
	 * @param output_dir folder to store output (transformed) images.
	 * @param transf_dir folder with transform files.
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public static boolean exec_expand_singletransform(
			final String source_dir, 
			final String output_dir, 
			final String transf_file) throws IOException 
	{
		// Get source file listing	
		final String[] src_names = listFilesOfType(source_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
		
		// Read transforms and calculate the global bounds of that transform
		CoordinateTransform[] transforms = new CoordinateTransform[src_names.length];
		transforms[0] = readCoordinateTransform(transf_file);
		if(transforms[0] == null)
		{
			IJ.error("Error when reading transform from file: " + transf_file);
			return false;
		}
		
		//calculate the bounds from this transform
		ImagePlus first = new ImagePlus(Register_Virtual_Stack_FCC.cleanDirString(source_dir)+src_names[0]);
		final double[] min = new double[ 2 ];
		final double[] max = new double[ 2 ];	
		final TransformMeshMapping< CoordinateTransformMesh > mapping = new TransformMeshMapping< CoordinateTransformMesh >( new CoordinateTransformMesh( transforms[0], 32, first.getWidth(), first.getHeight() ) );
		mapping.getTransform().bounds(min,max);
		//calculate the height and width that will fit all the transforms
		//round seems to agree better with the original register virtual stack slices
		final int width = ( int )Math.round( max[ 0 ] - min[ 0 ] );
		final int height = ( int )Math.round( max[ 1 ] - min[ 1 ] );
		
		//copy over the first transform into each of the other slots so we can reuse the createResults code below
		for(int i = 1; i < src_names.length; i ++)
		{
				transforms[i] = new CoordinateTransformList<CoordinateTransform>();
				transforms[i] = transforms[0];		
		}
		
		IJ.showStatus("Calculating expanded transformed images...");
		// Create transformed images
		if(Register_Virtual_Stack_FCC.createResults(source_dir, src_names, output_dir, null, transforms, interpolate,resolutionOutput,width,height) == false)
		{
			IJ.log("Error when creating transformed images");
			return false;
		}
		
		return true;
	}

	public static boolean exec_crop(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir,
			final String tempcrop_dir) throws IOException 
	{
		return exec_crop(source_dir,output_dir,transf_dir,tempcrop_dir,null);
	}
	/**
	 * Transform images in the source directory applying transform files from a specific directory.
	 * Using the sizes of the images located in the tempcrop_dir to set the size of each resulting image
	 * @param source_dir folder with input (source) images.
	 * @param output_dir folder to store output (transformed) images.
	 * @param transf_dir folder with transform files.
	 * @param tempcrop_dir folder with the images that are the sizes that you want the resulting transformed images to be
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public static boolean exec_crop(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir,
			final String tempcrop_dir,
			String subString) throws IOException 
	{
		// Get source file listing
		
		final String[] src_names = listFilesOfType(source_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
		final int numimages=src_names.length;
		final String[] tempcrop_names =  listFilesOfType(tempcrop_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
	
		boolean []analyzemask = new boolean[numimages];
		Register_Virtual_Stack_FCC.parse_analyzemask_string(subString,analyzemask);
		
		if (analyzemask==null){
			IJ.log("null mask");
			analyzemask=new boolean[numimages];
			for (int i=0;i < numimages ; i++){
				analyzemask[i]=true;
			}
		}
		if (analyzemask.length!=numimages){
			IJ.log("bad mask");
			IJ.log(String.format("numimages:%d length of mask:%d",numimages,analyzemask.length));
			analyzemask=new boolean[numimages];
			for (int i=0;i < numimages ; i++){
				analyzemask[i]=true;
			}
		}
		// Get transform file listing
		final String[] transf_names =  listFilesOfType(transf_dir,".xml");
		
		// Check the number of input (source) files and transforms.
		//if(transf_names.length != numimages)
		//now unneccesary given above check
		//{
		//	IJ.error("The number of source and transform files must be equal!");
		//	return false;
		//}
		
		//variables to store the size of each of the transform variables
		final int[] heights = new int[numimages];
		final int[] widths = new int[numimages];
	
		// Read transforms and image sizes from the template directory
		IJ.showStatus("Reading transforms and image sizes...");
		CoordinateTransform[] transform = new CoordinateTransform[numimages];
		
		boolean single_crop=false;
		//check to make sure there are the same number of files
		if(tempcrop_names.length != numimages)
		{
			IJ.log("The number of source and template crop files are not the same");
			IJ.log("the output will be cropped to the size of the first file in the reference stack");
			if (tempcrop_names.length<1){
				IJ.error("no files in the reference directory");
				return false;
			}
			single_crop=true;
		}
		
		final FileInfo fi_first = getFirstTifFileInfo(tempcrop_dir,tempcrop_names[0]);
		int single_width = fi_first.width;
		int single_height = fi_first.height;
		
		
		for(int i = 0; i < numimages; i ++)
		{
			if (analyzemask[i]==true){
				//IJ.log(String.format("i:%d ",i));
				//IJ.log(String.format("i:%d transf_names[i]:%s transf_dir:%s",i,transf_names[i],transf_dir));
				String transFileName=transf_dir + String.format("%03d.xml",i);
				File trans_file=new File(transFileName);
				if (trans_file.exists()){
					transform[i]=readCoordinateTransform(transFileName);
				}
				else{
					transform[i] = readCoordinateTransform(transf_dir + transf_names[i]);
				}
				if(transform[i] == null)
				{
					IJ.error("Error when reading transform from file: " + transf_dir + transf_names[i]);
					return false;
				}
			
				if (single_crop){
					widths[i]=single_width;
					heights[i]=single_height;
				}
				else{
					//read in the height and width from the corresponding crop image template
					final FileInfo fi = getFirstTifFileInfo(tempcrop_dir,tempcrop_names[i]);
					widths[i]=fi.width;
					heights[i]=fi.height;
				}
			}
		}
	
		IJ.showStatus("Calculating cropped transformed images ...");
		// Create transformed images
		if(Register_Virtual_Stack_FCC.createResultsCrop(source_dir, src_names, output_dir, null, transform, interpolate,resolutionOutput,widths,heights,analyzemask) == false)
		{
			IJ.log("Error when creating transformed images");
			return false;
		}
		
		return true;
		
	}
	/**
	 * Transform images in the source directory applying the transform file to each file.
	 * Using the sizes of the images located in the tempcrop_dir to set the size of each resulting image
	 * @param source_dir folder with input (source) images.
	 * @param output_dir folder to store output (transformed) images.
	 * @param transf_file single xml file to apply to source images.
	 * @param tempcrop_dir folder with the images that are the sizes that you want the resulting transformed images to be
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public static boolean exec_crop_singletransform(
			final String source_dir, 
			final String output_dir, 
			final String transf_file,
			final String tempcrop_dir) throws IOException 
	{
		// Get source file listing
		
		final String[] src_names = listFilesOfType(source_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
		final int numimages=src_names.length;
		final String[] tempcrop_names =  listFilesOfType(tempcrop_dir,".tif.jpg.png.gif.tiff.jpeg.bmp.pgm");
	
		//check to make sure there are the same number of files
		boolean single_crop=false;
		//check to make sure there are the same number of files
		if(tempcrop_names.length != numimages)
		{
			IJ.log("The number of source and template crop files are not the same");
			IJ.log("the output will be cropped to the size of the first file in the reference stack");
			if (tempcrop_names.length<1){
				IJ.error("no files in the reference directory");
				return false;
			}
			single_crop=true;
		}
		//height and width of first file in reference stack in case of single_crop mode
		final FileInfo fi_first = getFirstTifFileInfo(tempcrop_dir,tempcrop_names[0]);
		int single_width = fi_first.width;
		int single_height = fi_first.height;
			
		//variables to store the size of each of cropped sizes
		final int[] heights = new int[numimages];
		final int[] widths = new int[numimages];
	
		// Read transforms and image sizes from the template directory
		IJ.showStatus("Reading transforms and image sizes...");
		CoordinateTransform[] transform = new CoordinateTransform[numimages];
		transform[0]=readCoordinateTransform(transf_file);
		if(transform[0] == null)
		{
			IJ.error("Error when reading transform from file: " + transf_file);
			return false;
		}
		for(int i = 0; i < numimages; i ++)
		{
			if (i>0){
				transform[i] = new CoordinateTransformList<CoordinateTransform>();
				transform[i] = transform[0];
			}
			
			if (single_crop){
				widths[i]=single_width;
				heights[i]=single_height;
			}
			else{
				//read in the height and width from the corresponding crop image template
				final FileInfo fi = getFirstTifFileInfo(tempcrop_dir,tempcrop_names[i]);
				widths[i]=fi.width;
				heights[i]=fi.height;
			}
			
		}
	
		IJ.showStatus("Calculating cropped transformed images ...");
		// Create transformed images
		if(Register_Virtual_Stack_FCC.createResultsCrop(source_dir, src_names, output_dir, null, transform, interpolate,resolutionOutput,widths,heights,null) == false)
		{
			IJ.log("Error when creating transformed images");
			return false;
		}
		
		return true;
		
	}
	
	//---------------------------------------------------------------------------------
	/**
	 * Transform images in the source directory applying transform files from a specific directory.
	 * legacy function... redirect to exec_expand, as opposed to exec_crop
	 * @param source_dir folder with input (source) images.
	 * @param target_dir folder to store output (transformed) images.
	 * @param transf_dir folder with transform files.
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public boolean exec(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir) throws IOException 
	{
			
		return exec_expand(source_dir,output_dir,transf_dir);
	}

	/**
	 * Transform images in the source directory applying transform files from a specific directory.
	 * legacy function... redirect to exec_expand, as opposed to exec_crop
	 * @param source_dir folder with input (source) images.
	 * @param target_dir folder to store output (transformed) images.
	 * @param transf_dir folder with transform files.
	 * @return true for correct execution, false otherwise.
	 * @throws IOException 
	 */
	public boolean exec_MultipleChannelCrop(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir,
			final String tempcrop_dir) throws IOException 
	{
			
		File []source_dirchannels=Register_Two_MultiChannel_VStacks.ListSubDirectories(source_dir);
		int numchannels=source_dirchannels.length;
		
		//make sure all the required output directories exist and run exec_crop on each
		for(int i=0;i<numchannels;i++){
			File outputchannel_file=new File(output_dir + source_dirchannels[i].getName());
			if (!outputchannel_file.exists()) outputchannel_file.mkdirs();
			String outputchannel_dir = Register_Virtual_Stack_FCC.cleanDirString(outputchannel_file.getAbsolutePath());
			String sourcechannel_dir = Register_Virtual_Stack_FCC.cleanDirString(source_dirchannels[i].getAbsolutePath());
			if (!exec_crop(sourcechannel_dir,outputchannel_dir,transf_dir,tempcrop_dir)) return false;
			
		}
		return true;
	}
	
	public boolean exec_MultipleChannelExpand(
			final String source_dir, 
			final String output_dir, 
			final String transf_dir) throws IOException 
	{
			
		File []source_dirchannels=Register_Two_MultiChannel_VStacks.ListSubDirectories(source_dir);
		int numchannels=source_dirchannels.length;
		
		//make sure all the required output directories exist and run exec_crop on each
		for(int i=0;i<numchannels;i++){
			File outputchannel_file=new File(output_dir + source_dirchannels[i].getName());
			if (!outputchannel_file.exists()) outputchannel_file.mkdirs();
			String outputchannel_dir = Register_Virtual_Stack_FCC.cleanDirString(outputchannel_file.getAbsolutePath());
			String sourcechannel_dir = Register_Virtual_Stack_FCC.cleanDirString(source_dirchannels[i].getAbsolutePath());
			if (!exec_expand(sourcechannel_dir,outputchannel_dir,transf_dir)) return false;
			
		}
		return true;
	}
	public static String[] listFilesOfType(final String source_dir,final String exts){
		
		final String[] src_names = new File(source_dir).list(new FilenameFilter() 
		{
			public boolean accept(File dir, String name) 
			{
				int idot = name.lastIndexOf('.');
				if (-1 == idot) return false;
				return exts.contains(name.substring(idot).toLowerCase());
			}
		});
		Arrays.sort(src_names);
		return src_names;
	}
	public static FileInfo getFirstTifFileInfo(final String directory,final String filename){
		//read in the height and width from the corresponding crop image template
		final TiffDecoder td=new TiffDecoder(directory,filename);
		final FileInfo[] fi;
		
		try {
			fi=td.getTiffInfo();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return fi[0];
	}
	
	//---------------------------------------------------------------------------------
	/**
	 * Read coordinate transform from file (generated in Register_Virtual_Stack)
	 * @param filename complete file name (including path)
	 * @return true if the coordinate transform was properly read, false otherwise.
	 */
	public static CoordinateTransform readCoordinateTransform(String filename) 
	{
		final CoordinateTransformList<CoordinateTransform> ctl = new CoordinateTransformList<CoordinateTransform>();
		try 
		{
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line = null;
			while ((line = br.readLine()) != null) 
			{
				int index = -1;
				if( (index = line.indexOf("class=")) != -1)
				{
					// skip "class"
					index+= 5;
					// read coordinate transform class name
					final int index2 = line.indexOf("\"", index+2); 
					final String ct_class = line.substring(index+2, index2);
					final CoordinateTransform ct = (CoordinateTransform) Class.forName(ct_class).newInstance();
					// read coordinate transform info
					final int index3 = line.indexOf("=", index2+1);
					final int index4 = line.indexOf("\"", index3+2); 
					final String data = line.substring(index3+2, index4);
					ct.init(data);
					
					ctl.add(ct);
				}
			}
		
		} catch (FileNotFoundException e) {
			IJ.error("File not found exception" + e);
			
		} catch (IOException e) {
			IJ.error("IOException exception" + e);
			
		} catch (NumberFormatException e) {
			IJ.error("Number format exception" + e);
			
		} catch (InstantiationException e) {
			IJ.error("Instantiation exception" + e);
			
		} catch (IllegalAccessException e) {
			IJ.error("Illegal access exception" + e);
			
		} catch (ClassNotFoundException e) {
			IJ.error("Class not found exception" + e);
			
		}
		return ctl;
	}

}// end class Register_Virtual_Stack_MT

